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
package com.devexperts.qd.dxlink.websocket.transport;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.qd.dxlink.websocket.application.DxLinkWebSocketApplicationConnection;
import com.devexperts.qd.qtp.QTPWorkerThread;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * The <code>WebSocketWriter</code> writes standard socket using blocking API.
 * </p>
 * It was also decided to do without chunks for sending messages. Usually all messages are written into one buffer.
 * With the help of mask (see how to use com.devexperts.qd.qtp.MessageAdapter#addMask(long)) and cyclic looping
 * through all data sources (ticker, stream, histories, protocol description, etc.) each source writes
 * its own small portion of data (this is to make sure that one particularly productive source
 * does not take up the entire channel from other data sources), then, as we are able to limit the size of the message,
 * the message is sent. In our case for API web socket libraries need to mark when the end of the whole message,
 * for this we have to insert a single symbol and then passes byte by byte across the whole message in the chunks
 * and find the end. In the socket can be written in portions, but in this case for each portion will be
 * a separate frame to send and in the case of sending from chunks to the socket symbolically we get for each symbol
 * a separate frame, which is too much to send one byte. That's why we decided to accumulate in the composer
 * on the message in the chunks, but the list of ready messages. And when the compositor reports
 * {@link TransportConnection#chunksAvailable}, we call the {@link DxLinkWebSocketApplicationConnection#retrieveMessages}
 * method instead of the method {@link ApplicationConnection#retrieveChunks(Object)}.
 * It also doesn't make sense to pack the finished json into chunks, and then from chunks reassemble the string
 * to be sent to the websocket library API.
 */
class WebSocketWriter extends QTPWorkerThread {

    // Do not wait too long - wake up and recheck periodically.
    // Modifiable just in case some bug needs to be worked out in prod
    private static final long MAX_WAIT_TIME =
        TimePeriod.valueOf(SystemProperties.getProperty(WebSocketWriter.class, "maxWaitTime", "1m"))
            .getTime();

    private static final AtomicIntegerFieldUpdater<WebSocketWriter> STATE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(WebSocketWriter.class, "state");

    private static final int STATE_IDLE = 0;
    private static final int STATE_AVAILABLE = 1;
    private static final int STATE_PARKED = 2;

    private final WebSocketTransportConnection transportConnection;

    private volatile int state = STATE_IDLE;

    WebSocketWriter(WebSocketTransportConnection transportConnection) {
        super(transportConnection + "-Writer");
        this.transportConnection = transportConnection;
    }

    private boolean casState(int expect, int update) {
        return STATE_UPDATER.compareAndSet(this, expect, update);
    }

    @Override
    protected void doWork() throws InterruptedException, IOException {
        WebSocketTransportConnection.Session session = transportConnection.createSession();
        if (session == null)
            return;
        while (!isClosed()) {
            // don't even go into the wait loop if data is already available
            if (state != STATE_AVAILABLE) {
                // wait until data is available
                if (waitAvailableOrClosed(session.application))
                    return; // bail out if was closed while waiting
            }

            assert state == STATE_AVAILABLE; // we are here only in data available state
            state = STATE_IDLE; // clean up the state before retrieving chunks

            for (ByteBuf message : session.application.retrieveMessages()) {
                session.writeAndFlush(message);
            }
        }
    }

    // returns true if closed while waiting
    private boolean waitAvailableOrClosed(ApplicationConnection<?> connection) throws InterruptedException {
        while (true) {
            long currentTime = System.currentTimeMillis();
            long examineTime = connection.examine(currentTime);
            /*
             * We must check isClosed() somewhere before calling LockSupport.park() because some method that
             * is invoked from inside of this thread (in some client code in retrieveChunks) might have lost
             * interruption flag that was set when this worker thread was closed, which, in turn, may cause
             * the park invocation to block.
             *
             * This check also serves a second purpose. If park unblock due to interrupt, then this method
             * will get invoked again in a loop and will bail out on this check.
             */
            if (isClosed())
                return true; // bail out if closed
            if (state == STATE_AVAILABLE)
                return false; // bail out -- examine had set AVAILABLE state
            if (examineTime <= currentTime) {
                // this should never happen, because examine will only return time less
                // or equal to currentTime when chunks are available
                log.warn("INVARIANT VIOLATION DETECTED: examineTime <= currentTime but chunks are not available");
                // "workaround the bug" by waiting a little bit (to avoid 100% CPU consumption)
                examineTime = currentTime + 10;
            }
            // Do not wait too long - wake up and recheck. Also defend from overflows in border cases.
            long waitTime = examineTime - currentTime;
            if (waitTime < 0 || waitTime > MAX_WAIT_TIME)
                waitTime = MAX_WAIT_TIME;
            if (!casState(STATE_IDLE, STATE_PARKED))
                return false; // data became available before we've even started to park
            doPark(waitTime);
            if (!casState(STATE_PARKED, STATE_IDLE))
                return false; // data became available while we were parked (and we were unparked)
            // will need to wait again
        }
    }

    private void doPark(long waitTime) {
        boolean verboseDebug = transportConnection.verbose && log.debugEnabled();
        if (verboseDebug)
            log.debug("Parking for " + waitTime + " ms until more data is ready to send");
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitTime));
        if (verboseDebug)
            log.debug("Unparked");
    }

    @Override
    protected void handleShutdown() {
        transportConnection.stopConnector();
    }

    @Override
    protected void handleClose(Throwable reason) {
        transportConnection.exitSocket(reason);
    }

    void chunksAvailable() {
        /*
         * There is no "CAS loop" idiom here for performance reasons (to avoid loop-induced safe-point in this code).
         * See explanation below (after switch) on why it works.
         */
        int state = this.state; // volatile read state (1), then check current state
        if (state == STATE_IDLE) {
            // return if successfully made IDLE->AVAILABLE transition (2) -- there is no need to call unpark(!)
            if (casState(STATE_IDLE, STATE_AVAILABLE))
                return;
        } else if (state == STATE_AVAILABLE) {
            return; // already available -- nothing to do (bail out from the method)
        }
        /*
         * Execution can get here only when state was PARKED or the CAS operation at line (2) fails,
         * e.g. when the state had changed between its read in line (1) and the CAS in line (2).
         *
         * Note: there is exactly 1 doWork() call active and most of the time it does NOT perform any transition,
         * while massive parallel chunksAvailable() calls cooperate nicely as they all aim for AVAILABLE state --
         * the first one succeed, and all other return quickly after a single volatile read at line (1).
         *
         * To avoid "CAS loop", we opt for spurious/double unpark (which is perfectly fine) on a race between multiple
         * concurrent chunksAvailable invocation. It is safe just to assign state to AVAILABLE given that we also
         * call unpark _after_ that. The worst thing that can happen because of that is a spurious/double unpark/wakeup
         * and an maybe an extra retrieveData call.
         */
        this.state = STATE_AVAILABLE;
        LockSupport.unpark(this);
    }
}




