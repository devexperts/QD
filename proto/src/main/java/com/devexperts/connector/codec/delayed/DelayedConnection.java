/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector.codec.delayed;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.logging.Logging;

import java.io.IOException;
import javax.annotation.concurrent.GuardedBy;

class DelayedConnection extends CodecConnection<DelayedConnectionFactory> {

    private static final Logging log = Logging.getLogging(DelayedConnection.class);

    private final long delay; // delay period in millis
    private final long bufferLimit; // maximum size of data accumulated for delaying

    private final Object chunksLock = new Object();

    // last time observed in adjustTimeStampsIfNeeded (used to detect abnormal time shifts).
    private long lastTime = Long.MIN_VALUE;

    private volatile boolean delegateHasChunks = true;

    @GuardedBy("chunksLock")
    private final StampedChunksQueue chunksForSending = new StampedChunksQueue();

    DelayedConnection(ApplicationConnectionFactory delegateFactory, DelayedConnectionFactory config,
        TransportConnection transportConnection) throws IOException
    {
        super(delegateFactory, config, transportConnection);
        delay = config.getDelay();
        bufferLimit = config.getBufferLimit();
    }

    @Override
    protected void closeImpl() {
        super.closeImpl();
        synchronized (chunksLock) {
            chunksForSending.clearAndRecycle(this);
        }
    }

    @Override
    public long examine(long currentTime) {
        boolean hasChunks = false;
        long nextChunkTime = Long.MAX_VALUE;
        synchronized (chunksLock) {
            adjustTimeStampsIfNeeded(currentTime);
            if (!chunksForSending.isEmpty()) {
                nextChunkTime = chunksForSending.getFirstTimestamp() + delay;
                hasChunks = nextChunkTime <= currentTime;
            }
        }
        long nextTime = super.examine(currentTime);
        if (delegateHasChunks || hasChunks) {
            notifyChunksAvailable();
        }
        nextTime = Math.min(nextTime, nextChunkTime);
        return nextTime;
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        synchronized (chunksLock) {
            if (isClosed())
                return null;
            long curTime = System.currentTimeMillis();
            adjustTimeStampsIfNeeded(curTime);
            long borderTime = curTime - delay;

            if (delegateHasChunks && haveSpaceForBuffering()) {
                delegateHasChunks = false;
                ChunkList delegateChunks = delegate.retrieveChunks(this);
                if (delegateChunks != null) {
                    chunksForSending.add(delegateChunks, curTime); // mark with current time
                    if (!haveSpaceForBuffering()) {
                        // If we have no space for buffering incoming data, it will be naturally back pressured to the
                        // source and effective delay will grow uncontrollably.
                        log.warn("delayed data exceeded specified buffering limit " + bufferLimit);
                    }
                }
            }

            ChunkList result = null;
            // Handover delayed chunks to the uplink with the same granularity as originally received to avoid
            // extraneous ChunkLists juggling.
            if (chunksForSending.hasChunkToSend(borderTime)) {
                result = chunksForSending.remove();
                result.handOver(this, owner);
                if (chunksForSending.hasChunkToSend(borderTime) || delegateHasChunks && haveSpaceForBuffering())
                    notifyChunksAvailable();
            }
            return result;
        }
    }

    @Override
    public void chunksAvailable() {
        delegateHasChunks = true;
        super.chunksAvailable();
    }

    @GuardedBy("chunksLock")
    private boolean haveSpaceForBuffering() {
        return bufferLimit <= 0 || chunksForSending.getTotalSize() < bufferLimit;
    }

    @GuardedBy("chunksLock")
    private void adjustTimeStampsIfNeeded(long currentTime) {
        if (currentTime < lastTime) {
            // observed a negative time shift, need to shift collected chunks to avoid uncontrollable delay
            chunksForSending.adjustTimeStamps(currentTime - lastTime);
        }
        lastTime = currentTime;
    }
}
