/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.http;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;

import com.devexperts.io.*;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.stats.QDStats;

final class QDServletConnection
    implements HttpSessionBindingListener, MessageAdapter.CloseListener, Serializable
{
    private static final long serialVersionUID = 3310839998222991847L;

    private static final Logging log = Logging.getLogging(QDServlet.class);

    // Separate class so that all memory-consuming resources can be reclaimed by GC when connection is closed
    private static class Handler {
        private final MessageAdapter adapter;
        private final QDStats stats;
        private final ChunkedOutput output = new ChunkedOutput();
        private final BinaryQTPComposer composer;
        private final ChunkedInput input = new ChunkedInput();
        private final BinaryQTPParser parser;

        Handler(MessageAdapter adapter, QDStats stats) {
            this.adapter = adapter;
            this.stats = stats;
            this.composer = new BinaryQTPComposer(adapter.getScheme(), true);
            this.composer.setOutput(output);
            this.composer.setStats(stats);
            this.parser = new BinaryQTPParser(adapter.getScheme());
            this.parser.setInput(input);
            this.parser.setStats(stats);
        }

        public void close() {
            adapter.close();
            stats.close();
        }

        public long readMessages(HttpServletRequest request) throws IOException {
            InputStream in = request.getInputStream();
            int total = 0;
            while (true) {
                Chunk chunk = ChunkPool.DEFAULT.getChunk(this);
                int bytes = in.read(chunk.getBytes(), 0, Math.min(Math.max(1, in.available()), chunk.getLength()));
                if (bytes > 0) {
                    chunk.setLength(bytes, this);
                    input.addToInput(chunk, this);
                    parser.parse(adapter);
                    total += bytes;
                } else {
                    chunk.recycle(this);
                    if (bytes < 0)
                        break; // EOF
                }
            }
            in.close();
            return total;
        }

        public long writeMessages(HttpServletResponse response) throws IOException {
            boolean hasMore = composer.compose(adapter);
            if (hasMore)
                response.setHeader(HttpConnector.MORE_MESSAGES_HTTP_PROPERTY, "true");
            response.setContentType("application/x-octet-stream");
            ChunkList chunks = output.getOutput(this);
            int processed = chunks == null ? 0 : (int) chunks.getTotalLength();
            response.setContentLength(processed);
            OutputStream out = response.getOutputStream();
            if (chunks != null) {
                Chunk chunk;
                while ((chunk = chunks.poll(this)) != null) {
                    out.write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
                    chunk.recycle(this);
                }
                chunks.recycle(this);
            }
            out.close();
            return processed;
        }
    }

    private final String connectionName;
    private final transient QDServletConfig config; // sets to null when deserialized
    private transient volatile Handler handler; // set to null when connection is closed
    private transient long timeoutTime;

    QDServletConnection(String connectionName, MessageAdapter adapter, QDStats stats, QDServletConfig config) {
        this.connectionName = connectionName;
        this.config = config;
        handler = new Handler(adapter, stats);
        adapter.setCloseListener(this);
        stats.addMBean(QDStats.SType.CONNECTION.getName(), adapter);
        adapter.useDescribeProtocol();
        adapter.start();
        updateTimeoutTime();
        QDServletConnectionCleaner.addConnection(this);
        log.info("Connection established (" + connectionName + ")");
    }

    public boolean isValid() {
        return config != null; // sets to null when deserialized
    }

    private void updateTimeoutTime() {
        timeoutTime = System.currentTimeMillis() + config.getConnectionTimeout();
    }

    public boolean isTimedOut(long time) {
        return time > timeoutTime;
    }

    private synchronized boolean makeClosed() {
        Handler handler = this.handler;
        if (handler == null)
            return false;
        this.handler = null; // mark as closed first!!!
        handler.close();
        return true;
    }

    public void close(String reason) {
        if (!isValid())
            return; // cannot close invalid connection (that was deserialized)
        if (makeClosed()) {
            QDServletConnectionCleaner.removeConnection(this);
            log.info("Connection closed (" + connectionName + ") because " + reason);
        }
    }

    public void adapterClosed(MessageAdapter adapter) {
        close("message adapter was closed");
    }

    public void valueBound(HttpSessionBindingEvent event) {
        // nothing to do
    }

    public void valueUnbound(HttpSessionBindingEvent event) {
        close("http session expired");
    }

    public long readMessages(HttpServletRequest request) throws IOException, ServletException {
        Handler handler = this.handler;
        if (handler == null)
            throw new ServletException("Connection " + connectionName + " was already closed");
        updateTimeoutTime();
        return handler.readMessages(request);
    }

    public long writeMessages(HttpServletResponse response) throws IOException, ServletException {
        Handler handler = this.handler;
        if (handler == null)
            throw new ServletException("Connection " + connectionName + " was already closed");
        return handler.writeMessages(response);
    }

    public String toString() {
        return connectionName;
    }
}
