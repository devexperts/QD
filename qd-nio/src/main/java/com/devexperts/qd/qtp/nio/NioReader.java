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
package com.devexperts.qd.qtp.nio;

import com.devexperts.io.ChunkedOutput;
import com.devexperts.logging.Logging;
import com.devexperts.util.LockFreePool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static com.devexperts.qd.qtp.nio.NioFlags.RS_NOT_READY_FOR_MORE;
import static com.devexperts.qd.qtp.nio.NioFlags.RS_NOT_SELECTABLE;
import static com.devexperts.qd.qtp.nio.NioFlags.RS_PROCESSING;

class NioReader {

    private static final Logging log = Logging.getLogging(NioReader.class);

    private static final NioConnection SELECT_TASK = new NioConnection();

    static ByteBuffer[] createDirectBuffers(int count, int size) {
        ByteBuffer[] buffers = new ByteBuffer[count];
        ByteBuffer master = ByteBuffer.allocateDirect(count * size);
        for (int i = 0; i < count; i++) {
            master.position(size * i);
            master.limit(size * (i + 1));
            buffers[i] = master.slice();
        }
        return buffers;
    }

    private final NioCore core;
    private final Selector selector;
    private final ReadingThread[] threads;
    private final LockFreePool<ReadingThread> spareThreads;
    private final ConcurrentLinkedQueue<NioConnection> readyConnections = new ConcurrentLinkedQueue<>();
    private final Queue<NioConnection> newConnections = new ConcurrentLinkedQueue<>();
    private final NioPoolCounters counters;
    private final NioPoolCountersHolder countersHolder;
    private final AtomicLong wakeupRequestNanoTime = new AtomicLong();

    NioReader(NioCore core, int readerThreads) throws IOException {
        this.core = core;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            log.error("Failed to open selector", e);
            throw e;
        }
        ByteBuffer[] buffers = createDirectBuffers(readerThreads, 4096);
        threads = new ReadingThread[readerThreads];
        for (int i = 0; i < readerThreads; i++)
            threads[i] = new ReadingThread(i, buffers[i]);
        spareThreads = new LockFreePool<>(readerThreads);
        readyConnections.offer(SELECT_TASK);
        counters = new NioPoolCounters(readerThreads);
        countersHolder = new NioPoolCountersHolder(counters);
    }

    NioPoolCountersHolder getCountersHolder() {
        return countersHolder;
    }

    void start() {
        for (ReadingThread thread : threads)
            thread.start();
    }

    void close() {
        try {
            selector.close();
        } catch (IOException e) {
            log.error("Failed to close selector", e);
        }
        for (ReadingThread thread : threads)
            thread.interrupt();
    }

    void join() throws InterruptedException {
        for (ReadingThread thread : threads)
            thread.join();
    }

    void register(NioConnection connection) {
        newConnections.offer(connection);
        selectorWakeup();
    }

    void registerNewConnections() {
        for (NioConnection connection; (connection = newConnections.poll()) != null;)
            connection.registerForReading(selector);
    }

    void readyToProcess(NioConnection connection) {
        //##### INVARIANT: the connection has just become ready to process more chunks
        assert connection.readingStateMatches(0, 0, 1);
        if (!readyConnections.offer(connection))
            throw new AssertionError();
        selectorWakeup(); // since all threads may be waiting for selection; this line may be replaced by some advanced code for optimization.
    }

    void selectorWakeup() {
        if (wakeupRequestNanoTime.get() == 0)
            wakeupRequestNanoTime.compareAndSet(0, System.nanoTime());
        selector.wakeup();
    }

    // This method never executes concurrently by several threads
    synchronized NioConnection doSelect(ByteBuffer buffer, ChunkedOutput chunkedOutput) throws IOException, ClosedSelectorException {
        registerNewConnections();
        counters.registeredSockets = selector.keys().size();

        // Select from selector and record stats
        wakeupRequestNanoTime.set(0);
        selector.select(NioCore.SELECT_TIMEOUT);
        long selectedNanoTime = System.nanoTime();
        long wakeupRequestNanoTime = this.wakeupRequestNanoTime.getAndSet(0);
        if (wakeupRequestNanoTime != 0) {
            // select was woken up (there was request to wake it up while it was selecting)
            counters.wakeupTime.addMeasurement(selectedNanoTime - wakeupRequestNanoTime);
        }
        counters.registeredSockets = selector.keys().size();
        counters.totalSelectedSockets += selector.selectedKeys().size();

        // Process selected keys
        for (SelectionKey key : selector.selectedKeys()) {
            NioConnection connection = (NioConnection) key.attachment();
            if (!key.isValid()) {
                connection.close();
                continue;
            }
            try {
                key.interestOps(0);
            } catch (CancelledKeyException e) {
                connection.close();
                continue;
            }
            //##### INVARIANT: we were selectable
            assert connection.readingStateMatches(-1, -1, 0);

            int state = connection.readingState.incrementAndGet();
            if (state == RS_NOT_SELECTABLE)
                readyConnections.offer(connection); // otherwise the connection is either not ready for more data yet (5, 7) or already processing (3)
        }
        selector.selectedKeys().clear();

        NioConnection connection;
        while (true) {
            connection = readyConnections.poll();
            if (connection == null)
                break;
            //##### INVARIANT: the connection was just taken from the queue
            assert connection.readingStateMatches(0, 0, 1);

            if (connection.readChunks(buffer, chunkedOutput, null))
                continue; // the connection was closed
            ReadingThread thread = spareThreads.poll();
            if (thread == null) {
                readyConnections.offer(SELECT_TASK);
                break;
            }
            thread.wakeup(connection);
        }
        counters.busyTime.addMeasurement(System.nanoTime() - selectedNanoTime);
        return connection == null ? SELECT_TASK : connection;
    }

    private class ReadingThread extends NioWorkerThread {
        private final ByteBuffer buffer;
        private final ChunkedOutput chunkedOutput;
        private volatile NioConnection connection; // next connection to be processing

        ReadingThread(int index, ByteBuffer buffer) {
            super(NioReader.this.core, "Reader-" + index);
            this.buffer = buffer;
            this.chunkedOutput = new ChunkedOutput(core.chunkPool);
        }

        void wakeup(NioConnection connection) {
            //##### INVARIANT: we were sleeping in the spareThreads pool and received
            //##### processing connection with already read data from selector
            assert connection.readingStateMatches(0, 1, -1);
            this.connection = connection;
            LockSupport.unpark(this);
        }

        @Override
        public void makeIteration() throws InterruptedException, IOException, ClosedSelectorException {
            counters.activeThreads.incrementAndGet();
            try {
                if (connection == null)
                    connection = readyConnections.poll();
                if (connection != null && connection != SELECT_TASK) {
                    //##### INVARIANT: the connection was just taken from the queue (explicitly or virtually),
                    //##### it was already selected but its data was not read yet
                    assert connection.readingStateMatches(0, 0, 1);

                    if (connection.readChunks(buffer, chunkedOutput, selector)) { // the connection was closed
                        connection = null;
                        return;
                    }
                }
                if (connection == null) {
                    if (!spareThreads.offer(this)) {
                        log.warn("Failed to put the thread into spareThreads pool");
                        sleep(100);
                        return;
                    }
                    while (connection == null && !core.isClosed()) {
                        counters.activeThreads.decrementAndGet();
                        LockSupport.park();
                        counters.activeThreads.incrementAndGet();
                    }
                }
                while (connection == SELECT_TASK && !core.isClosed())
                    connection = doSelect(buffer, chunkedOutput); // Any Throwable here will be logged below and will keep SELECT_TASK for ourselves
                if (core.isClosed())
                    return;
                //##### INVARIANT: the data was just read either by us (if we took connection from the queue)
                //##### or by selector (if we got the connection via wakeup(...) method)
                assert connection.readingStateMatches(0, 1, -1);

                // Process chunks and measure time
                long appNanoTime = System.nanoTime();
                connection.processChunks();
                counters.appTime.addMeasurement(System.nanoTime() - appNanoTime);

                while (true) {
                    // if yet not ready to process more - trying to stop processing
                    int state = connection.readingState.get();
                    if (state < RS_NOT_READY_FOR_MORE)
                        break; // it is ready to be processed
                    if (connection.readingState.compareAndSet(state, state & ~RS_PROCESSING)) {
                        connection = null;
                        return; // we successfully cleared processing flag
                    }
                }
                //##### INVARIANT: we've cleared "not-ready-for-more" flag and are still processing
                assert connection.readingStateMatches(0, 1, -1);

                if (connection.readingState.addAndGet(-RS_PROCESSING) == 1) {
                    // if readyConnections queue is empty - then we just keep our connection for next pass as if handled through the queue
                    if (!readyConnections.isEmpty()) {
                        readyConnections.offer(connection);
                        connection = null;
                    }
                } else
                    connection = null;
            } catch (ClosedSelectorException e) {
                core.close();
            } finally {
                counters.activeThreads.decrementAndGet();
            }
        }
    }
}
