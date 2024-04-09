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
package com.devexperts.qd.qtp.nio;

import com.devexperts.connector.proto.AbstractTransportConnection;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QTPConstants;
import com.devexperts.qd.qtp.socket.SocketUtil;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devexperts.qd.qtp.nio.NioFlags.RS_NOT_READY_FOR_MORE;
import static com.devexperts.qd.qtp.nio.NioFlags.RS_NOT_SELECTABLE;
import static com.devexperts.qd.qtp.nio.NioFlags.RS_PROCESSING;
import static com.devexperts.qd.qtp.nio.NioFlags.WS_PROCESSING;

/**
 * Class that represents a single two-way client socket connection.
 */
class NioConnection extends AbstractTransportConnection implements Closeable {

    private static final Logging log = Logging.getLogging(NioConnection.class);

    final NioCore core;
    final SocketChannel channel;
    final QDStats stats;
    final ConnectionStats connectionStats;
    final ApplicationConnection<?> applicationConnection;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * For {@link NioReader#SELECT_TASK} only.
     */
    NioConnection() {
        core = null;
        channel = null;
        stats = null;
        connectionStats = null;
        applicationConnection = null;
    }

    /**
     * Creates ConnectionInfo of specified <tt>channel</tt>, configures it,
     * adds it into <tt>serverConnector</tt>'s connections and
     * registers it with read and write selectors of the <tt>serverConnector</tt>.
     *
     * @param core server connection.
     * @param channel              SocketChannel to create info.
     * @throws ClosedChannelException if the <tt>channel</tt> has been closed concurrently.
     * @throws IOException            if failed to create.
     */
    NioConnection(NioCore core, SocketChannel channel) throws IOException {
        this.core = core;
        this.channel = channel;

        Socket socket = channel.socket();
        variables().set(MessageConnectors.SOCKET_KEY, socket);
        variables().set(REMOTE_HOST_ADDRESS_KEY, socket.getInetAddress().getHostAddress());
        stats = core.connector.getStats().getOrCreate(QDStats.SType.CONNECTIONS).create(QDStats.SType.CONNECTION,
            "host=" + JMXNameBuilder.quoteKeyPropertyValue(socket.getInetAddress().getHostAddress()) +
                ",port=" + socket.getPort() + ",localPort=" + socket.getLocalPort()
        );
        if (stats == null)
            throw new IOException("Failed to create QDStats.");
        variables().set(MessageConnectors.STATS_KEY, stats);
        connectionStats = new ConnectionStats();

        applicationConnection = core.connector.getFactory().createConnection(this);
        applicationConnection.start();
    }

    /**
     * Closes all the connection info's members and
     * removes the connection from connector.
     */
    @Override
    public void close() {
        closeBecauseOf(null);
    }

    void closeBecauseOf(Throwable reason) {
        if (closed.getAndSet(true))
            return;
        try {
            applicationConnection.close();
        } catch (Throwable t) {
            log.error("Unexpected error while closing application connection", t);
        }
        stats.close();
        core.connector.addClosedConnectionStats(connectionStats);
        String address = SocketUtil.getAcceptedSocketAddress(channel.socket());
        try {
            channel.close();
            log.info("Disconnected from " + LogUtil.hideCredentials(address) +
                (reason == null ? "" : reason.getMessage() == null ? "" : " because of " + reason.getMessage()));
        } catch (Throwable t) {
            log.error("Error occurred while disconnecting from " + LogUtil.hideCredentials(address), t);
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    // ==================== TransportConnection implementation ====================

    @Override
    public void markForImmediateRestart() {
        // nothing to do on server-side -- we can only wait for incoming connection
    }

    @Override
    public void connectionClosed() {
        close();
    }

    @Override
    public void chunksAvailable() {
        core.chunksAvailable(this);
    }

    @Override
    public void readyToProcessChunks() {
        readyToProcessChunksInternal();
    }

    // ==================== Reading data from transport ====================

    private SelectionKey readingKey;

    // See RS_XXX in NioFlags
    final AtomicInteger readingState = new AtomicInteger();

    private ChunkList readingChunks;

    void registerForReading(Selector selector) {
        try {
            if (isClosed())
                return;
            readingKey = channel.register(selector, SelectionKey.OP_READ, this);
        } catch (ClosedChannelException e) {
            close();
        }
    }

    // returns true if the connection was closed during this reading attempt (or before it)
    boolean readChunks(ByteBuffer buffer, ChunkedOutput chunkedOutput, Selector selector) {
        //##### INVARIANT: we are not selectable already but not being processing yet
        assert readingStateMatches(0, 0, 1);

        if (isClosed())
            return true;
        int totalLength = 0;
        while (true) {
            buffer.clear();
            try {
                int length = channel.read(buffer);
                if (length < 0) { // eof
                    chunkedOutput.clear();
                    close();
                    return true;
                }
                totalLength += length;
                connectionStats.addReadBytes(length);
            } catch (ClosedChannelException e) {
                chunkedOutput.clear();
                closeBecauseOf(e);
                return true;
            } catch (IOException e) {
//              log.error("IOException while reading from socket channel", e);
                chunkedOutput.clear();
                closeBecauseOf(e);
                return true;
            }
            buffer.flip();
            try {
                chunkedOutput.writeFromByteBuffer(buffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            //##### INVARIANT: state didn't change
            assert readingStateMatches(0, 0, 1);

            if (buffer.limit() == buffer.capacity()) { // heuristic check if socket has more data to read
                if (totalLength < QTPConstants.READ_AGGREGATION_SIZE)
                    continue; // continue reading up to aggregation size
                readingState.set(RS_NOT_SELECTABLE + RS_PROCESSING);
            } else {
                readingState.set(RS_PROCESSING);
                try {
                    readingKey.interestOps(SelectionKey.OP_READ);
                } catch (CancelledKeyException e) {
                    chunkedOutput.clear();
                    close();
                    return true;
                }
                if (selector != null)
                    selector.wakeup();
            }
            break;
        }
        readingChunks = chunkedOutput.getOutput(this);
        //##### INVARIANT: we've read the data and marked oneself as processing
        assert readingStateMatches(0, 1, -1);
        return false;
    }

    void processChunks() {
        //##### INVARIANT: we are processing and can application connection can take data
        assert readingStateMatches(0, 1, -1);

        ChunkList chunks = readingChunks;
        readingChunks = null;
        if (chunks == null)
            return;
        try {
            readingState.addAndGet(RS_NOT_READY_FOR_MORE); // mark application connection as not ready to process more data for the next time
            if (applicationConnection.processChunks(chunks, this))
                readyToProcessChunksInternal(); // act as if transport connection was notified when receive 'true'
        } catch (Throwable t) {
            log.error("Unexpected error while processing incoming chunks", t);
            close();
        }
    }

    private void readyToProcessChunksInternal() {
        while (true) {
            int state = readingState.get();
            if (state < RS_NOT_READY_FOR_MORE) // if it was ready to process anyway
                return;
            if (readingState.compareAndSet(state, state & ~RS_NOT_READY_FOR_MORE)) {
                state &= ~RS_NOT_READY_FOR_MORE;
                if (state == RS_NOT_SELECTABLE) {
                    core.readyToProcess(this);
                } else
                    return; // otherwise either we are already processing (2 or 3) or we were not selected yet (0)
            }
        }
    }

    // This method is for debug checks only.
    // usage: "assert readingStateMatches(X, X, X);", where each X is 0, 1 or -1 for wildcard
    boolean readingStateMatches(int notReadyForMore, int isProcessing, int notSelectable) {
        int state = readingState.get();
        //log.debug("NIO Connection " + this + ", reading state:" + state);
        //noinspection PointlessBitwiseExpression
        assert (notSelectable   < 0 || ((state >> 0) & 1) == notSelectable)   : "state = " + state;
        assert (isProcessing    < 0 || ((state >> 1) & 1) == isProcessing)    : "state = " + state;
        assert (notReadyForMore < 0 || ((state >> 2) & 1) == notReadyForMore) : "state = " + state;
        return true;
    }

    // ==================== Writing data into transport ====================

    // See WS_XX in NioFlags
    final AtomicInteger writingState = new AtomicInteger();

    boolean shouldDeregisterWrite; // used by NioWriter for internal state storage

    private final ChunkedInput writingChunksInput = new ChunkedInput();

    // returns true if writingChunks were sent completely or the connection was closed
    boolean writeChunks(ByteBuffer buffer) {
        //##### INVARIANT: we are processing
        assert (writingState.get() >= WS_PROCESSING);

        while (true) {
            buffer.clear();
            writingChunksInput.mark();
            try {
                writingChunksInput.readToByteBuffer(buffer);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            if (buffer.position() == 0) { // There were no bytes in writingChunks
                writingChunksInput.clear();
                return true;
            }

            // Sending bytes from byte buffer
            buffer.flip();
            try {
                channel.write(buffer);
            } catch (ClosedChannelException e) {
                writingChunksInput.clear();
                closeBecauseOf(e);
                return true;
            } catch (IOException e) {
//              log.error("IOException while writing into socket channel", e);
                writingChunksInput.clear();
                closeBecauseOf(e);
                return true;
            }
            connectionStats.addWrittenBytes(buffer.position());

            if (buffer.remaining() > 0) {
                // not all data was written
                writingChunksInput.rewind(buffer.remaining());
                writingChunksInput.unmark();
                return false;
            }
        }
    }

    void retrieveChunks() {
        if (isClosed())
            return;
        ChunkList chunks;
        try {
            chunks = applicationConnection.retrieveChunks(this);
        } catch (Throwable t) {
            log.error("Unexpected exception in application connection", t);
            close();
            return;
        }
        if (chunks != null) {
            writingChunksInput.addAllToInput(chunks, this);
        }
    }
}
