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
package com.devexperts.qd.qtp.socket;

import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.qd.qtp.QTPConstants;
import com.devexperts.qd.qtp.QTPWorkerThread;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;

/**
 * The <code>SocketReader</code> reads standard socket using blocking API.
 */
class SocketReader extends QTPWorkerThread {
    private static final long WARN_TIMEOUT_NANOS = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.qtp.socket.readerWarnTimeout", "15s")).
        getNanos();

    private final SocketHandler handler;
    private volatile boolean isReadyToProcess = true;

    SocketReader(SocketHandler handler) {
        super(handler + "-Reader");
        this.handler = handler;
    }

    @Override
    protected void doWork() throws IOException, InterruptedException {
        SocketHandler.ThreadData threadData = handler.initThreadData();
        if (threadData == null)
            return;
        InputStream in = threadData.socket.getInputStream();
        /*
         * All internal loops have while(true) { ... } form and the following pattern is used for consistency
         * to bail out when connection is closed:
         *     if (isClosed())
         *         return; // bail out if closed
         */
        while (true) {
            ChunkList chunks = handler.chunkPool.getChunkList(this);
            int totalRead = 0;
            long timeNanos;
            while (true) {
                if (isClosed())
                    return; // bail out if closed
                Chunk chunk = handler.chunkPool.getChunk(this);
                int readCapacity = chunk.getLength();
                int bytesRead = in.read(chunk.getBytes(), chunk.getOffset(), readCapacity);
                timeNanos = System.nanoTime();
                if (handler.verbose && log.debugEnabled())
                    log.debug(SocketHandler.verboseBytesToString("Read", chunk.getBytes(), chunk.getOffset(), bytesRead));
                if (bytesRead < 0)
                    throw new EOFException("Connection closed by remote side");
                threadData.connectionStats.addReadBytes(bytesRead);
                chunk.setLength(bytesRead, this);
                chunks.add(chunk, this);
                totalRead += bytesRead;
                if (totalRead >= QTPConstants.READ_AGGREGATION_SIZE || bytesRead < readCapacity || in.available() == 0)
                    break; // either we've reached aggregation limit or the portion of data have been (most likely) read completely; we'll pass it for processing
                if (handler.verbose && log.debugEnabled())
                    log.debug("More data is available, will read");
            }
            if (isClosed())
                return; // bail out if closed

            // Note that isReadyToProcess can be set to true concurrently via listener.
            // We do not want to override the result of this notification, thus we first set it to false
            // and then as we call processChunks() we could only raise it to true.
            isReadyToProcess = false;
            //noinspection RedundantIfStatement
            if (threadData.connection.processChunks(chunks, this))
                isReadyToProcess = true;
            long deltaTimeNanos = System.nanoTime() - timeNanos;
            if (deltaTimeNanos > WARN_TIMEOUT_NANOS)
                log.warn("processChunks took " + deltaTimeNanos + " ns");

            while (!isReadyToProcess) {
                /*
                 * We must check isClosed() before calling LockSupport.park() because some method that
                 * is invoked from inside of this thread (in some client code in processChunks) might have lost
                 * interruption flag that was set when this worker thread was closed, which, in turn, may cause
                 * the park invocation to block.
                 *
                 * This check also server a second purpose. If park unblock due to interrupt, then this loop
                 * will repeat again and will bail out on this check.
                 */
                if (isClosed())
                    return; // bail out if closed
                if (handler.verbose && log.debugEnabled())
                    log.debug("Parking until more data can be processed");
                LockSupport.park();
                if (handler.verbose && log.debugEnabled())
                    log.debug("Unparked");
            }
        }
    }

    @Override
    protected void handleShutdown() {
        handler.stopConnector();
    }

    @Override
    protected void handleClose(Throwable reason) {
        handler.exitSocket(reason);
    }

    void readyToProcess() {
        if (isReadyToProcess)
            return;
        isReadyToProcess = true;
        LockSupport.unpark(this);
    }
}
