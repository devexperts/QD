/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector.codec.shaped;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;

import java.io.IOException;
import javax.annotation.concurrent.GuardedBy;

class ShapedConnection extends CodecConnection<ShapedConnectionFactory> {
    private static final long COMMITTED_BURST_DURATION = 100; // millis

    private final double outLimit; // output throughput limit (bytes per second)
    private final Object chunksLock = new Object();
    private final ChunkPool chunkPool = ChunkPool.DEFAULT;

    private final double cbs; // Committed Burst Size

    private volatile long lastTime;
    private volatile boolean delegateHasChunks = true;
    private ChunkList chunksForSending;

    @GuardedBy("chunksLock")
    private long outBucket;

    ShapedConnection(ApplicationConnectionFactory delegateFactory, ShapedConnectionFactory config,
        TransportConnection transportConnection) throws IOException
    {
        super(delegateFactory, config, transportConnection);
        outLimit = config.getOutLimit();
        cbs = COMMITTED_BURST_DURATION * outLimit / 1000;
    }

    @Override
    protected void startImpl() {
        lastTime = System.currentTimeMillis();
        super.startImpl();
    }

    @Override
    protected void closeImpl() {
        super.closeImpl();
        synchronized (chunksLock) {
            if (chunksForSending != null) {
                chunksForSending.recycle(this);
                chunksForSending = null;
            }
        }
    }

    @Override
    public long examine(long currentTime) {
        boolean hasChunks;
        synchronized (chunksLock) {
            hasChunks = chunksForSending != null;
        }
        long nextTime = super.examine(currentTime);
        if (delegateHasChunks || hasChunks) {
            notifyChunksAvailable();
            nextTime = Math.min(nextTime, currentTime + 10);
        }
        return nextTime;
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        synchronized (chunksLock) {
            if (isClosed())
                return null;

            long curTime = System.currentTimeMillis();
            long interval = Math.max(curTime - lastTime, 0);
            outBucket = (long) Math.min(cbs, outBucket + interval * outLimit / 1000);
            lastTime = curTime;
            if (outBucket <= 0)
                return null;
            long availableSize = chunksForSending == null ? 0 : chunksForSending.getTotalLength();
            if (availableSize < outBucket && delegateHasChunks) {
                delegateHasChunks = false;
                ChunkList delegateChunks = delegate.retrieveChunks(this);
                if (delegateChunks != null) {
                    availableSize += delegateChunks.getTotalLength();
                    if (chunksForSending == null)
                        chunksForSending = delegateChunks;
                    else
                        chunksForSending.addAll(delegateChunks, this);
                }
            }

            ChunkList result;
            if (availableSize <= outBucket) {
                result = chunksForSending;
                chunksForSending = null;
                outBucket -= availableSize;
            } else {
                result = chunkPool.getChunkList(this);
                while (outBucket > 0) {
                    Chunk chunk = chunksForSending.get(0);
                    if (chunk.getLength() > outBucket) {
                        result.addAll(
                            chunkPool.copyToChunkList(chunk.getBytes(), chunk.getOffset(), (int) outBucket, this),
                            this);
                        chunksForSending.setChunkRange(
                            0, chunk.getOffset() + (int) outBucket, chunk.getLength() - (int) outBucket, this);
                        outBucket = 0;
                    } else {
                        result.add(chunk = chunksForSending.poll(this), this);
                        outBucket -= chunk.getLength();
                    }
                }
            }

            if (result != null)
                result.handOver(this, owner);

            return result;
        }
    }

    @Override
    public void chunksAvailable() {
        delegateHasChunks = true;
        super.chunksAvailable();
    }
}
