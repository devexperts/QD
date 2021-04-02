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
package com.devexperts.connector.codec.ssl;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

class SSLConnection extends CodecConnection<SSLConnectionFactory> {

    private static final String SYNC_SSL_ENGINE_PROPERTY = "com.devexperts.connector.codec.ssl.synchronizeSSLEngine";

    private static final ThreadLocal<ByteBuffer> inAppBuffer = new ThreadLocal<>(); // todo: optionally: this may be replaced by buffer pooling
    private static final ThreadLocal<ByteBuffer> inNetBuffer = new ThreadLocal<>();
    private static final ThreadLocal<ByteBuffer> outAppBuffer = new ThreadLocal<>();
    private static final ThreadLocal<ByteBuffer> outNetBuffer = new ThreadLocal<>();

    private final SSLEngineAdapter engine;
    private final ExecutorProvider.Reference executorReference;

    private final ChunkedInput inNetChunkedInput;
    private final ChunkedInput outAppChunkedInput;
    private final ChunkedOutput inAppChunkedOutput;
    private final ChunkedOutput outNetChunkedOutput;

    private volatile boolean delegateHasChunks = true;
    private volatile boolean delegateReadyToProcess = true;
    private volatile boolean isExecutingTask = false;
    private volatile boolean hasUnsentChunks = false;

    SSLConnection(ApplicationConnectionFactory delegateFactory, SSLConnectionFactory factory,
        TransportConnection transportConnection,
        SSLEngine engine, ExecutorProvider.Reference executorReference) throws IOException
    {
        super(delegateFactory, factory, transportConnection);
        // [QD-1196] SSLEngineImpl wrap/unwrap deadlock workaround
        boolean syncEngine = SystemProperties.getBooleanProperty(SYNC_SSL_ENGINE_PROPERTY, true);
        this.engine = syncEngine ? new SSLEngineSynchronizedAdapter(engine) : new SSLEngineAdapter(engine);

        this.executorReference = executorReference;
        inNetChunkedInput = new ChunkedInput(factory.getChunkPool());
        outAppChunkedInput = new ChunkedInput(factory.getChunkPool());
        inAppChunkedOutput = new ChunkedOutput(factory.getChunkPool());
        outNetChunkedOutput = new ChunkedOutput(factory.getChunkPool());
    }

    @Override
    protected void startImpl() {
        super.startImpl();
        notifyChunksAvailable();
    }

    @Override
    protected void closeImpl() {
        super.closeImpl();
        executorReference.close();
    }

    @Override
    public void chunksAvailable() {
        delegateHasChunks = true;
        super.chunksAvailable();
    }

    @Override
    public void readyToProcessChunks() {
        delegateReadyToProcess = true;
        if (!isExecutingTask)
            super.readyToProcessChunks();
    }

    private ByteBuffer getBuffer(ThreadLocal<ByteBuffer> threadLocal, boolean isAppBuffer) {
        ByteBuffer buffer = threadLocal.get();
        SSLSession session = engine.getSession();
        int capacity = isAppBuffer ? session.getApplicationBufferSize() : session.getPacketBufferSize();
        if (buffer == null || buffer.capacity() < capacity)
            threadLocal.set(buffer = ByteBuffer.allocateDirect(capacity)); // todo: optionally: do some pooling or at least advanced allocation?
        buffer.clear();
        return buffer;
    }

    private void executeEngineTasks() {
        final Runnable task = engine.getDelegatedTask();
        if (task == null)
            return;
        isExecutingTask = true;
        executorReference.getOrCreateExecutor().execute(() -> {
            task.run();
            isExecutingTask = false;
            initiateNextOperation();
        });
    }

    private void initiateNextOperation() {
        switch (engine.getHandshakeStatus()) {
        case NEED_WRAP:
            notifyChunksAvailable();
            break;
        case NEED_UNWRAP:
            if (processChunks(ChunkList.EMPTY, null))
                notifyReadyToProcess();
            break;
        case NEED_TASK:
            executeEngineTasks();
            break;
        case FINISHED:
        case NOT_HANDSHAKING:
            if (delegateReadyToProcess && !isExecutingTask)
                notifyReadyToProcess();
            if (delegateHasChunks || hasUnsentChunks)
                notifyChunksAvailable();
            break;
        }
    }

    // ==================== Sending data into transport ====================

    @Override
    public ChunkList retrieveChunks(Object owner) {
        if (!outAppChunkedInput.hasAvailable()) {
            if (delegateHasChunks) {
                delegateHasChunks = false;
                try {
                    ChunkList chunks = delegate.retrieveChunks(this);
                    if (chunks != null)
                        outAppChunkedInput.addAllToInput(chunks, this);
                } catch (Throwable t) {
                    log.error("Unexpected error", t);
                    close();
                    return null;
                }
            }
        }
        return wrap(owner);
    }

    private ChunkList wrap(Object owner) {
        ByteBuffer outAppBuffer = getBuffer(SSLConnection.outAppBuffer, true);
        ByteBuffer outNetBuffer = getBuffer(SSLConnection.outNetBuffer, false);
        outAppBuffer.flip();

        outAppChunkedInput.mark();
        LOOP: while (true) {
            // get more application data into the buffer
            outAppBuffer.compact();
            try {
                outAppChunkedInput.readToByteBuffer(outAppBuffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            outAppBuffer.flip();

            // perform wrap
            SSLEngineResult result;
            try {
                result = engine.wrap(outAppBuffer, outNetBuffer);
            } catch (SSLException e) {
                log.error("Failed to wrap", e);
                outNetChunkedOutput.clear();
                close();
                return null;
            }

            // retrieve network data from the buffer
            outNetBuffer.flip();
            try {
                outNetChunkedOutput.writeFromByteBuffer(outNetBuffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            outNetBuffer.clear();

            switch (result.getStatus()) {
            case CLOSED:
                outNetChunkedOutput.clear();
                close();
                return null;
            case BUFFER_OVERFLOW:
                outNetBuffer = getBuffer(SSLConnection.outNetBuffer, false);
                continue;
            }

            switch (result.getHandshakeStatus()) {
            case NEED_WRAP:
                continue;
            case NOT_HANDSHAKING:
            case FINISHED:
                if (!outAppChunkedInput.hasAvailable() && outAppBuffer.remaining() == 0)
                    break LOOP;
                break;
            default:
                break LOOP;
            }
        }

        outAppChunkedInput.rewind(outAppBuffer.remaining());
        outAppChunkedInput.unmark();
        hasUnsentChunks = outAppChunkedInput.hasAvailable();

        initiateNextOperation();
        return outNetChunkedOutput.getOutput(owner);
    }

    // ==================== Receiving data from transport ====================

    private final ArrayBlockingQueue<ChunkList> chunkListsToProcess = new ArrayBlockingQueue<>(4); // capacity is a random small constant
    private final AtomicBoolean isProcessing = new AtomicBoolean();

    @Override
    public boolean processChunks(ChunkList newChunks, Object owner) {
        if (newChunks == null)
            throw new NullPointerException("chunks is null");
        if (newChunks == ChunkList.EMPTY && !chunkListsToProcess.isEmpty())
            return false; // no need to enqueue yet another internal request for unwrap since there already are some
        newChunks.handOver(owner, this);
        try {
            chunkListsToProcess.put(newChunks);
        } catch (InterruptedException e) {
            close();
            return false;
        }
        if (!isProcessing.compareAndSet(false, true))
            return false; // someone is already processing

        boolean newChunksAppeared = true;
        while (!isClosed()) {
            while (true) {
                while (true) {
                    ChunkList chunks = chunkListsToProcess.poll();
                    if (chunks == null)
                        break;
                    newChunksAppeared = true;
                    inNetChunkedInput.addAllToInput(chunks, this);
                }
                if (newChunksAppeared)
                    break;
                // trying to exit
                isProcessing.set(false);
                if (chunkListsToProcess.isEmpty())
                    return delegateReadyToProcess && !isExecutingTask;
                else
                    if (!isProcessing.compareAndSet(false, true))
                        return false;
            }
            newChunksAppeared = false;

            ChunkList inAppChunks;
            inAppChunks = unwrap();
            if (inAppChunks != null) {
                delegateReadyToProcess = false;
                //noinspection RedundantIfStatement
                if (delegate.processChunks(inAppChunks, this))
                    delegateReadyToProcess = true;
            }
        }
        return false; // was closed
    }

    private ChunkList unwrap() {
        ByteBuffer inNetBuffer = getBuffer(SSLConnection.inNetBuffer, false);
        ByteBuffer inAppBuffer = getBuffer(SSLConnection.inAppBuffer, true);
        inNetBuffer.flip();

        inNetChunkedInput.mark();
        LOOP: while (true) {
            // get more network data into the buffer
            inNetBuffer.compact();
            try {
                inNetChunkedInput.readToByteBuffer(inNetBuffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            inNetBuffer.flip();

            // perform unwrap
            SSLEngineResult result;
            try {
                result = engine.unwrap(inNetBuffer, inAppBuffer);
            } catch (SSLException e) {
                log.error("Failed to unwrap", e);
                close();
                inAppChunkedOutput.clear();
                return null;
            }

            // retrieve application data from the buffer
            inAppBuffer.flip();
            try {
                inAppChunkedOutput.writeFromByteBuffer(inAppBuffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            inAppBuffer.clear();

            switch (result.getStatus()) {
            case CLOSED:
                inAppChunkedOutput.clear();
                close();
                return null;
            case BUFFER_OVERFLOW:
                inAppBuffer = getBuffer(SSLConnection.inAppBuffer, true);
                continue LOOP;
            case BUFFER_UNDERFLOW:
                break LOOP;
            }

            switch (result.getHandshakeStatus()) {
            case NEED_WRAP:
                notifyChunksAvailable();
                break LOOP;
            case NEED_TASK:
                executeEngineTasks();
                break LOOP;
            }
        }

        inNetChunkedInput.rewind(inNetBuffer.remaining());
        inNetChunkedInput.unmark();

        switch (engine.getHandshakeStatus()) {
            case NOT_HANDSHAKING:
            case FINISHED:
                if (delegateHasChunks || hasUnsentChunks)
                    notifyChunksAvailable();
                break;
        }

        return inAppChunkedOutput.getOutput(this);
    }
}
