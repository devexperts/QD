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

import com.devexperts.logging.Logging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.devexperts.qd.qtp.nio.NioFlags.WS_MORE_DATA;
import static com.devexperts.qd.qtp.nio.NioFlags.WS_PROCESSING;

class NioWriter {

    private static final Logging log = Logging.getLogging(NioWriter.class);

    private final NioCore core;
    private final Selector selector;
    private final WritingThread[] threads;
    private final BlockingQueue<NioConnection> readyConnections = new LinkedBlockingQueue<>();
    private final Queue<NioConnection> connectionsToRegister = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSelecting = new AtomicBoolean();
    private final NioPoolCounters counters;
    private final NioPoolCountersHolder countersHolder;
    private final AtomicLong wakeupRequestNanoTime = new AtomicLong();
    private final List<SelectionKey> keysToCancel = new ArrayList<>(); // used in doSelect

    NioWriter(NioCore core, int writerThreads) throws IOException {
        this.core = core;
        selector = Selector.open();
        ByteBuffer[] buffers = NioReader.createDirectBuffers(writerThreads, 4096);
        threads = new WritingThread[writerThreads];
        for (int i = 0; i < writerThreads; i++)
            threads[i] = new WritingThread(i, buffers[i]);
        counters = new NioPoolCounters(writerThreads);
        countersHolder = new NioPoolCountersHolder(counters);
    }

    NioPoolCountersHolder getCountersHolder() {
        return countersHolder;
    }

    void start() {
        for (WritingThread thread : threads)
            thread.start();
    }

    void close() {
        try {
            selector.close();
        } catch (IOException e) {
            log.error("Failed to close selector", e);
        }
        for (WritingThread thread : threads)
            thread.interrupt();
    }

    void join() throws InterruptedException {
        for (WritingThread thread : threads)
            thread.join();
    }

    void chunksAvailable(NioConnection connection) {
        // try-CAS-loop
        while (true) {
            if (connection.isClosed())
                return;
            int state = connection.writingState.get();
            if ((state & WS_MORE_DATA) != 0) // listener flag has been already set
                break;
            if (connection.writingState.compareAndSet(state, state | WS_MORE_DATA)) {
                if (state == 0) // the connection was not processing yet
                    readyConnections.offer(connection);
                break;
            }
        }
    }

    private void selectorWakeup() {
        if (wakeupRequestNanoTime.get() == 0)
            wakeupRequestNanoTime.compareAndSet(0, System.nanoTime());
        selector.wakeup();
    }

    // This method never executes concurrently by several threads
    synchronized void doSelect(ByteBuffer buffer) throws IOException, ClosedSelectorException {
        boolean restoreSelectState = true;
        try {
            int count = 0;
            while (!core.isClosed()) {
                if (count == 0 && connectionsToRegister.isEmpty() && keysToCancel.isEmpty()) {
                    // trying to exit
                    isSelecting.set(false);
                    if (connectionsToRegister.isEmpty() || isSelecting.getAndSet(true)) {
                        // either there is no more under-written connections
                        // or someone else volunteered to perform selecting
                        restoreSelectState = false;
                        return;
                    }
                }

                count += registerNew();
                count -= deregisterOld();
                counters.registeredSockets = selector.keys().size();

                if (count == 0) {
                    selector.selectNow(); // in order to merely clear selector's cancelledKeys
                    // otherwise CancelledKeyException may be thrown in next registerNew() invocation
                    continue;
                }

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
                    //##### INVARIANT: we are processing
                    assert (connection.writingState.get() >= WS_PROCESSING);
                    if (connection.writeChunks(buffer)) {
                        // NOTE: We can not just cancel the keys right here because in case the corresponding connections
                        // will get into connectionsToRegister again until the next selection is performed
                        // we will receive CancelledKeyException while trying to register them again with the same selector.
                        keysToCancel.add(key);
                        connection.shouldDeregisterWrite = true;
                        if (!connection.isClosed() && (connection.writingState.addAndGet(-WS_PROCESSING) == WS_MORE_DATA))
                            readyConnections.offer(connection);
                    }
                }
                selector.selectedKeys().clear();
                counters.busyTime.addMeasurement(System.nanoTime() - selectedNanoTime);
            }
            // core.isClosed() == true
        } finally {
            if (restoreSelectState)
                isSelecting.set(false);
        }
    }

    private int deregisterOld() {
        int count = 0;
        for (SelectionKey key : keysToCancel) {
            NioConnection connection = (NioConnection) key.attachment();
            if (connection.shouldDeregisterWrite) {
                connection.shouldDeregisterWrite = false;
                key.cancel();
                count++;
            }
        }
        keysToCancel.clear();
        return count;
    }

    private int registerNew() {
        int count = 0;
        for (NioConnection connection; (connection = connectionsToRegister.poll()) != null;)
            try {
                if (connection.shouldDeregisterWrite) { // the connection is registered and we were going to deregister it
                    connection.shouldDeregisterWrite = false;
                    continue;
                }
                connection.channel.register(selector, SelectionKey.OP_WRITE, connection); // a CancelledKeyException here may never be thrown and it may only mean a bug in the code        count++;
                count++;
            } catch (ClosedChannelException e) {
                connection.close();
            }
        return count;
    }

    private class WritingThread extends NioWorkerThread {

        private final ByteBuffer buffer;

        WritingThread(int index, ByteBuffer buffer) {
            super(NioWriter.this.core, "Writer-" + index);
            this.buffer = buffer;
        }

        @Override
        protected void makeIteration() throws InterruptedException, IOException, ClosedSelectorException {
            try {
                NioConnection connection = readyConnections.take();
                counters.activeThreads.incrementAndGet();
                try {
                    while (!core.isClosed()) {
                        //##### INVARIANT: we were in queue => connection has data
                        assert (connection.writingState.get() == WS_MORE_DATA);
                        connection.writingState.set(WS_PROCESSING);

                        // Retrieve chunks and measure time
                        long appNanoTime = System.nanoTime();
                        connection.retrieveChunks();
                        counters.appTime.addMeasurement(System.nanoTime() - appNanoTime);

                        // pretend that is were we are actually writing bytes for latency measurement...
                        // will not track the time that will be spend in "select" if cannot actually write those bytes
                        if (connection.writeChunks(buffer)) {// wrote everything
                            //##### INVARIANT: we are processing
                            assert (connection.writingState.get() >= WS_PROCESSING);
                            if (!connection.isClosed() && (connection.writingState.addAndGet(-WS_PROCESSING) == WS_MORE_DATA)) {
                                if (!readyConnections.isEmpty())
                                    readyConnections.offer(connection);
                                else
                                    continue; // keep this task for ourselves as if we've just put it into the readyConnections queue and got it back right away
                            }
                        } else { // not all data has been written or the task was closed
                            connectionsToRegister.offer(connection);
                            if (isSelecting.getAndSet(true)) // someone is already selecting
                                selectorWakeup();
                            else // we will select
                                doSelect(buffer);
                        }
                        break;
                    }
                } finally {
                    counters.activeThreads.decrementAndGet();
                }
            } catch (ClosedSelectorException e) {
                core.close();
            }
        }
    }
}
