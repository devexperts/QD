/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.qtp.FileConstants;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ParallelWriter implements Closeable {
    private static final Task CLOSE_THREAD_TASK = new Task();
    private static final Task EMPTY_TASK = new Task();

    private final TaskQueue todoQueue;
    private final TaskQueue doneQueue;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> throwable = new AtomicReference<>();

    private final Worker worker;
    private final Output output;

    ParallelWriter(String name, int taskQueueSize) {
        todoQueue = new TaskQueue(taskQueueSize);
        doneQueue = new TaskQueue(taskQueueSize);
        worker = new Worker(name);
        output = new Output(taskQueueSize);
    }

    public void start() {
        if (closed.get())
            throw new IllegalStateException("closed");
        worker.start();
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true))
            return;
        todoQueue.put(CLOSE_THREAD_TASK); // force graceful termination of worker threads
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        checkThrowable();
    }

    public BufferedOutput open(Opener opener, Runnable closeHandler) {
        if (output.isOpen || closed.get())
            throw new IllegalStateException();
        output.isOpen = true;
        output.opener = opener;
        output.closeHandler = closeHandler;
        return output;
    }

    private void checkThrowable() throws IOException {
        Throwable t = throwable.getAndSet(null);
        if (t == null)
            return;
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        if (t instanceof Error)
            throw (Error) t;
        if (t instanceof IOException)
            throw (IOException) t;
        throw new RuntimeException(t);
    }

    public interface Opener {
        public OutputStream open() throws IOException;
    }

    private static class Task {
        Opener opener;
        Runnable closeHandler;
        ChunkList chunks;
        boolean close;
    }

    private class Output extends ChunkedOutput {
        // all fields are guarded by this
        private boolean isOpen;
        private boolean closeOnFlush;
        private Opener opener;
        private Runnable closeHandler;
        private int taskQueueSize;

        Output(int taskQueueSize) {
            this.taskQueueSize = taskQueueSize;
        }

        // "flush" must be synchronized with "needSpace" and other chunk-list modifying methods
        // because worker thread may flush concurrently with data writer threads that write data
        // to this output
        @Override
        public synchronized void flush() throws IOException {
            ChunkList chunks = getOutput(worker);
            if (chunks != null && !chunks.isEmpty())
                queueTask(chunks);
        }

        @Override
        protected synchronized void needSpace() throws IOException {
            super.needSpace();
        }

        @Override
        public synchronized void writeFromChunk(Chunk chunk, Object owner) throws IOException {
            super.writeFromChunk(chunk, owner);
        }

        @Override
        public synchronized void writeAllFromChunkList(ChunkList chunks, Object owner) throws IOException {
            super.writeAllFromChunkList(chunks, owner);
        }

        @Override
        public synchronized void close() throws IOException {
            if (!isOpen)
                throw new IllegalStateException();
            isOpen = false;
            closeOnFlush = true;
            flush(); // write remaining chunks
            if (closeOnFlush)
                queueTask(null);
        }

        private void queueTask(ChunkList chunks) throws IOException {
            Task writeTask = doneQueue.poll();
            if (writeTask == null) {
                if (taskQueueSize > 0) {
                    writeTask = new Task();
                    taskQueueSize--;
                } else {
                    writeTask = doneQueue.take();
                }
            }
            writeTask.opener = opener;
            writeTask.closeHandler = closeHandler;
            writeTask.chunks = chunks;
            writeTask.close = closeOnFlush;
            opener = null;
            if (closeOnFlush) {
                closeHandler = null;
            }
            closeOnFlush = false;
            todoQueue.put(writeTask);
            checkThrowable();
        }
    }

    private class Worker extends Thread {
        private OutputStream out;
        private long nextFlushTime;

        Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (true) {
                Task task = todoQueue.poll(FileConstants.MAX_BUFFER_TIME, TimeUnit.MILLISECONDS);
                if (task == CLOSE_THREAD_TASK)
                    break; // close thread signal
                try {
                    runTask(task == null ? EMPTY_TASK : task); // empty task will only force periodic flush
                } catch (Throwable t) {
                    throwable.set(t);
                }
                if (task != null)
                    doneQueue.offer(task);
            }
        }

        private void runTask(Task task) throws IOException {
            try {
                long currentTime = System.currentTimeMillis();
                // TASK STEP #1: OPEN
                if (task.opener != null) {
                    assert out == null;
                    out = task.opener.open();
                    nextFlushTime = currentTime + FileConstants.MAX_BUFFER_TIME;
                    task.opener = null; // release opener to GC
                }
                // TASK STEP #2: WRITE CHUNKS
                if (task.chunks != null) {
                    Chunk chunk;
                    while ((chunk = task.chunks.poll(this)) != null) {
                        out.write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
                        chunk.recycle(this);
                    }
                    task.chunks.recycle(this);
                }
                // TASK STEP #3: FLUSH ACTUAL OUTPUT PERIODICALLY (NOT TOO OFTEN) -- it is "syncFlush" for gzip
                // Note: don't need to do it before close
                if (out != null && !task.close && currentTime >= nextFlushTime) {
                    out.flush();
                    nextFlushTime = currentTime + FileConstants.MAX_BUFFER_TIME;
                }
            } finally {
                // TASK STEP #3: CLOSE
                if (task.close && out != null) {
                    OutputStream toBeClosed = out;
                    out = null;
                    toBeClosed.close();
                    if (task.closeHandler != null) {
                        task.closeHandler.run();
                    }
                }
            }
        }
    }

    // uninterruptible task queue (never throws InterruptedException)
    private static class TaskQueue {
        // we want a fair lock, because we don't want our closeThread to starve
        private final Lock lock = new ReentrantLock(true);
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        private final int n;
        private final Task[] a;
        private int head;
        private int tail;

        TaskQueue(int maxSize) {
            this.n = maxSize + 1;
            a = new Task[maxSize + 1];
        }

        public Task poll() {
            lock.lock();
            try {
                return pollImpl();
            } finally {
                lock.unlock();
            }
        }

        // interruptible, returns null when interrupted
        public Task poll(long time, TimeUnit unit) {
            lock.lock();
            try {
                if (head == tail)
                    try {
                        notEmpty.await(time, unit);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                return pollImpl();
            } finally {
                lock.unlock();
            }
        }

        public Task take() {
            lock.lock();
            try {
                while (head == tail)
                    notEmpty.awaitUninterruptibly();
                return pollImpl();
            } finally {
                lock.unlock();
            }
        }

        private Task pollImpl() {
            if (head == tail)
                return null;
            Task task = a[head];
            head = (head + 1) % n;
            notFull.signalAll();
            return task;
        }

        public void put(Task task) {
            lock.lock();
            try {
                while (!offerImpl(task))
                    notFull.awaitUninterruptibly();
            } finally {
                lock.unlock();
            }
        }

        public boolean offer(Task task) {
            lock.lock();
            try {
                return offerImpl(task);
            } finally {
                lock.unlock();
            }
        }

        boolean offerImpl(Task task) {
            int nextTail = (tail + 1) % n;
            if (nextTail == head)
                return false;
            a[tail] = task;
            tail = nextTail;
            notEmpty.signalAll();
            return true;
        }
    }
}
