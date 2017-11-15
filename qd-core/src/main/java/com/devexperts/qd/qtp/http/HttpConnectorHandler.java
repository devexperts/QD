/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.http;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import com.devexperts.connector.proto.*;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.Base64;
import com.devexperts.util.LogUtil;

class HttpConnectorHandler extends AbstractConnectionHandler<HttpConnector> {
    // --- initial config parameters copied from connector ---

    private final String user;
    private final String password;
    private final int fetchCount;
    private final long fetchDelay;
    private final long updateDelay;
    private final String proxyHost;
    private final int proxyPort;
    private final ReconnectHelper reconnectHelper;

    // --- state ---

    private final Object messagesLock = new Object();
    private boolean messagesAvailable = true;

    private ApplicationConnection<?> applicationConnection;
    private QDStats stats;
    private boolean hasMore;
    private int fetchesLeft;
    private String cookie;

    private final TransportConnection transportConnection = new AbstractTransportConnection() {
        @Override
        public void markForImmediateRestart() {
            reconnectHelper.reset();
        }

        @Override
        public void connectionClosed() {
            close();
        }

        @Override
        public void chunksAvailable() {
            synchronized (messagesLock) {
                messagesAvailable = true;
                messagesLock.notifyAll();
            }
        }

        @Override
        public void readyToProcessChunks() {
            //todo: introduce flow control
        }
    };

    HttpConnectorHandler(HttpConnector connector, ReconnectHelper reconnectHelper) {
        super(connector);
        this.reconnectHelper = reconnectHelper;
        setPriority(connector.getThreadPriority());
        user = connector.getUser();
        password = connector.getPassword();
        fetchCount = connector.getFetchCount();
        fetchDelay = connector.getFetchDelay();
        updateDelay = connector.getUpdateDelay();
        proxyHost = connector.getProxyHost();
        proxyPort = connector.getProxyPort();
    }

    @Override
    protected void closeImpl(Throwable reason) {
        ApplicationConnection<?> connection = this.applicationConnection;
        if (connection != null)
            connection.close();
        QDStats stats = this.stats;
        if (stats != null)
            stats.close();
        log.error("Disconnected from " + LogUtil.hideCredentials(address), reason);
        connector.handlerClosed(this);
    }

    // returns false if POST is not a supported method
    private boolean doPost(URL url, ApplicationConnection<?> connection, boolean isNewConnection, QDStats stats) throws IOException {
        hasMore = false;
        HttpURLConnection con = proxyHost != null && proxyHost.length() > 0 ?
            (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))) :
            (HttpURLConnection) url.openConnection();
        if (isNewConnection)
            con.setRequestProperty(HttpConnector.NEW_CONNECTION_HTTP_PROPERTY, "true");
        else if (cookie != null)
            con.setRequestProperty("Cookie", cookie);
        if (user != null && password != null && user.length() > 0) {
            con.setRequestProperty("Authorization", "Basic " +
                Base64.DEFAULT.encode((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        con.setUseCaches(false);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        writeMessages(con, connection);
        con.connect();
        int responseCode = con.getResponseCode();
        if (isNewConnection && responseCode == HttpURLConnection.HTTP_BAD_METHOD) {
            // "POST" is not supported. Will fallback to reading it as a file
            return false;
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String msg = responseCode + " " + con.getResponseMessage();
            String longMsg = msg;
            if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                StringBuilder serverError = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String readLine;
                    while ((readLine = br.readLine()) != null)
                        if (readLine.trim().length() != 0)
                            serverError.append(readLine).append('\n');
                    longMsg += "\n" +
                        "server response follows: \n" +
                        ">====================\n" +
                        serverError +
                        "<======================";
                }
            }
            con.disconnect(); // do not reuse failed connection
            log.error(longMsg);
            throw new IOException(msg);
        }
        if (cookie == null)
            cookie = con.getHeaderField("Set-Cookie");
        if (isNewConnection && con.getHeaderField("Content-length") == null)
            log.info("WARNING: Content-length is not set and connection keep-alive will not work.");
        hasMore |= "true".equalsIgnoreCase(con.getHeaderField(HttpConnector.MORE_MESSAGES_HTTP_PROPERTY));
        readMessages(con, connection);
        return true;
    }

    private void writeMessages(HttpURLConnection con, ApplicationConnection<?> applicationConnection) throws IOException {
        con.setRequestProperty("Content-type", "application/x-octet-stream");
        OutputStream out = con.getOutputStream();
        ChunkList chunks = applicationConnection.retrieveChunks(this);
        if (chunks != null) {
            for (Chunk chunk : chunks) {
                out.write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
                connectionStats.addWrittenBytes(chunk.getLength());
            }
            chunks.recycle(this);
        }
        fetchesLeft = fetchCount; // sending new stuff, need quick round-trips to get delayed responses back.
        out.close();
    }

    private void readMessages(HttpURLConnection con, ApplicationConnection<?> applicationConnection) throws IOException {
        InputStream in = con.getInputStream();
        boolean eof = false;
        while (!eof) {
            ChunkList chunks = connector.chunkPool.getChunkList(this);
            while (true) {
                Chunk chunk = connector.chunkPool.getChunk(this);
                int bytesRead = in.read(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
                if (bytesRead < 0) { // EOF
                    in.close();
                    eof = true;
                    break;
                }
                connectionStats.addReadBytes(bytesRead);
                chunk.setLength(bytesRead, this);
                chunks.add(chunk, this);
                if (bytesRead < chunk.getLength() || in.available() == 0)
                    break; // the portion of data have been read completely; we'll pass it for processing
            }
            applicationConnection.processChunks(chunks, this); // todo: check returned value and use it to organize proper flow control
        }
    }

    private ApplicationConnection<?> connect(URL url) {
        ApplicationConnection<?> connection = null;
        QDStats stats = null;
        try {
            transportConnection.variables().set(TransportConnection.REMOTE_HOST_ADDRESS_KEY, url.getHost());
            stats = connector.getStats().getOrCreate(QDStats.SType.CONNECTIONS).create(QDStats.SType.CONNECTION);
            if (stats == null)
                throw new NullPointerException("Stats were not created");
            transportConnection.variables().set(MessageConnectors.STATS_KEY, stats);
            connection = connector.getFactory().createConnection(transportConnection);
            connection.start();
            if (!doPost(url, connection, true, stats)) {
                log.info("POST method is not supported by the server. Will start reading file with GET method.");
                connector.setFile(true);
                return null;
            }
            if (makeConnected()) {
                this.applicationConnection = connection;
                this.stats = stats;
                return connection;
            }
        } catch (Throwable t) {
            closeImpl(t);
        }
        // we are here only if something went wrong or we failed to makeConnected (we were already stopped)
        if (connection != null)
            connection.close();
        if (stats != null)
            stats.close();
        return null;
    }

    @Override
    protected void doWork() throws InterruptedException, IOException {
        URL url = new URL(address); // fail early if URL is malformed
        reconnectHelper.sleepBeforeConnection();
        log.info("Connecting to " + LogUtil.hideCredentials(address));
        ApplicationConnection<?> connection = connect(url);
        if (connection == null)
            return;
        log.info("Connected to " + LogUtil.hideCredentials(address));
        while (!isClosed()) {
            synchronized (messagesLock) {
                if (!messagesAvailable && !hasMore) {
                    // we'll sleep only if there not data on both sides.
                    if (fetchesLeft > 0) {
                        messagesLock.wait(Math.min(updateDelay, fetchDelay));
                        fetchesLeft--;
                    } else
                        messagesLock.wait(updateDelay);
                }
                messagesAvailable = false;
            }
            doPost(url, connection, false, stats);
            connection.examine(System.currentTimeMillis());
        }
    }

}
