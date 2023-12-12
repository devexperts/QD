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
package com.dxfeed.webservice.rest;

import com.devexperts.logging.Logging;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.GuardedBy;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletResponse;

/**
 * Basic connection class for Server-Sent Events.
 * It maintains a list of connections and static {@link #checkAndHeartbeatAll} method.
 */
public abstract class SSEConnection implements AsyncListener, Serializable {
    protected final Logging log = Logging.getLogging(getClass());

    private static final long serialVersionUID = 0;

    public static final long HEARTBEAT_PERIOD = TimePeriod.valueOf(SystemProperties.getProperty(
        EventsServlet.class, "heartbeatPeriod", "10s")).getTime();

    private static final IndexedSet<SSEConnection, SSEConnection> CONNECTIONS = new SynchronizedIndexedSet<>();

    private static final AtomicLong CONNECTION_ID = new AtomicLong();

    public static final String CONTENT_TYPE = "text/event-stream";

    private static final byte[] LINE_START = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte LINE_END = 0x0a;
    private static final byte MESSAGE_END = 0x0a;

    // ========================== instance fields ==========================

    protected final long id;

    @GuardedBy("this") protected transient volatile boolean active;
    @GuardedBy("this") protected transient Output out;
    @GuardedBy("this") protected transient AsyncContext async;

    protected transient volatile long lastMessageTime;

    protected SSEConnection() {
        this.id = CONNECTION_ID.incrementAndGet();
    }

    public boolean isActive() {
        return active;
    }

    protected synchronized boolean start(AsyncContext async) throws IOException {
        boolean wasActive = active;
        stopSync();
        active = true;
        ServletResponse resp = async.getResponse();
        resp.setContentType(CONTENT_TYPE);
        resp.flushBuffer(); // immediately commit headers
        this.async = async;
        this.out = new Output(resp.getOutputStream());
        CONNECTIONS.add(this);
        async.setTimeout(0); // no timeout
        async.addListener(this);
        startImpl();
        return wasActive;
    }

    protected boolean stop() {
        if (!active)
            return false;
        return stopSync();
    }

    private synchronized boolean stopSync() {
        if (!active)
            return false;
        active = false;
        CONNECTIONS.remove(this);
        stopImpl();
        async.complete();
        async = null;
        out = null;
        return true;
    }

    public void heartbeat() {
        if (System.currentTimeMillis() >= lastMessageTime + HEARTBEAT_PERIOD)
            heartbeatImpl();
    }

    @GuardedBy("this")
    protected abstract void startImpl();

    @GuardedBy("this")
    protected abstract void stopImpl();

    protected abstract void heartbeatImpl();

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        if (stop())
            log.info("Stopped, because of onComplete " + this);
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        if (stop())
            log.info("Stopped, because of onTimeout " + this);
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        if (stop())
            log.info("Stopped, because of onError " + this);
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {}

    public static void checkAndHeartbeatAll() {
        for (Iterator<SSEConnection> it = CONNECTIONS.concurrentIterator(); it.hasNext(); ) {
            it.next().heartbeat();
        }
    }

    protected class Output extends FilterOutputStream {
        boolean inLine;
        boolean crSeen;

        Output(OutputStream out) {
            super(out);
        }

        private void startLine() throws IOException {
            if (!inLine) {
                inLine = true;
                super.write(LINE_START);
            }
        }

        private void endLine() throws IOException {
            if (inLine) {
                inLine = false;
                super.write(LINE_END);
            }
        }

        @Override
        public void write(int b) throws IOException {
            switch (b) {
            case 0x0d:
                crSeen = true;
                startLine();
                endLine();
                break;
            case 0x0a:
                if (crSeen)
                    crSeen = false;
                else {
                    startLine();
                    endLine();
                }
                break;
            default:
                crSeen = false;
                startLine();
                super.write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            // will flush only on endMessage
        }

        @Override
        public void close() throws IOException {
            // is not closeable directly
        }

        public void endMessage() throws IOException {
            endLine();
            super.write(MESSAGE_END);
            super.flush();
            lastMessageTime = System.currentTimeMillis();
        }
    }

}
