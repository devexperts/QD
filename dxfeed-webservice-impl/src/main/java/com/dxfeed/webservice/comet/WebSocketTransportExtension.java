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
package com.dxfeed.webservice.comet;

import com.devexperts.logging.Logging;
import com.devexperts.util.SystemProperties;
import org.cometd.bayeux.Promise;
import org.cometd.bayeux.server.BayeuxContext;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.ServerSessionImpl;
import org.cometd.server.websocket.common.AbstractWebSocketEndPoint;
import org.cometd.server.websocket.jetty.JettyWebSocketEndPoint;
import org.cometd.server.websocket.jetty.JettyWebSocketTransport;

import java.util.List;
import java.util.Queue;
import javax.annotation.concurrent.GuardedBy;

public class WebSocketTransportExtension extends JettyWebSocketTransport {
    private static final Logging log = Logging.getLogging(WebSocketTransportExtension.class);

    private static final int DEFAULT_MAX_FLUSHER_QUEUE_SIZE = 100;
    private static final int MAX_FLUSHER_QUEUE_SIZE = SystemProperties.getIntProperty(
        WebSocketTransportExtension.class, "maxFlusherQueueSize", DEFAULT_MAX_FLUSHER_QUEUE_SIZE, 10, 1_000_000_000);

    @FunctionalInterface
    public interface FlusherQueueOverflowHandler {
        void toggleMessageProcessingDelaying(ServerSessionImpl session, boolean delayProcessing);
    }

    private static FlusherQueueOverflowHandler overflowHandler;

    public static void setOverflowHandler(FlusherQueueOverflowHandler overflowHandler) {
        if (WebSocketTransportExtension.overflowHandler != null) {
            log.warn("OverflowHandler is already initialized, overwriting it.");
        }
        WebSocketTransportExtension.overflowHandler = overflowHandler;
    }

    public WebSocketTransportExtension(BayeuxServerImpl bayeux) {
        super(bayeux);
    }

    protected Object newWebSocketEndPoint(BayeuxContext bayeuxContext) {
        return new EndPointWithQueueCheck(bayeuxContext);
    }

    private class EndPointWithQueueCheck extends JettyWebSocketEndPoint {
        /**
         * Holds AbstractWebSocketEndPoint.Flusher reference and is used as a synchronization object only.
         */
        private final Object flusher;

        /**
         * Holds an extracted AbstractWebSocketEndPoint.Flusher._entries reference.
         */
        @GuardedBy("flusher")
        private final Queue<?> flusherQueue;

        public EndPointWithQueueCheck(BayeuxContext bayeuxContext) {
            super(WebSocketTransportExtension.this, bayeuxContext);
            flusher = CometReflectionUtil.extractFlusher(this);
            flusherQueue = CometReflectionUtil.extractFlusherQueue(this);
            if (flusher == null) {
                throw new IllegalStateException("Flusher instance is null in AbstractWebSocketEndPoint");
            }
            if (flusherQueue == null) {
                throw new IllegalStateException("Queue instance is null in Flusher of AbstractWebSocketEndPoint");
            }
        }

        private void handleFlusherQueueOverflow() {
            if (overflowHandler == null) {
                log.warn("Flusher queue overflow handler is not initialized.");
                return;
            }

            ServerSessionImpl session = CometReflectionUtil.extractSessionInstance(this);
            if (session == null)
                return;

            int size;
            synchronized (flusher) {
                size = flusherQueue.size();
            }
            if (size > MAX_FLUSHER_QUEUE_SIZE) {
                // Delaying the processing if queue size reached the threshold
                overflowHandler.toggleMessageProcessingDelaying(session, true);

                // Another thread might have already consumed the queue down to reasonable size
                // while this thread was activating message delay. Let's check the size again
                // and resume message delivery (with the following "if" block) if this happened.
                synchronized (flusher) {
                    size = flusherQueue.size();
                }
            }
            if (size < MAX_FLUSHER_QUEUE_SIZE / 2) {
                // Resuming the processing only when queue size goes down significantly below the threshold
                // This enables for a smooth cool down without continuous swinging between enabled and disabled state
                overflowHandler.toggleMessageProcessingDelaying(session, false);
            }
        }

        @Override
        protected void writeComplete(AbstractWebSocketEndPoint.Context context, List<ServerMessage> messages) {
            super.writeComplete(context, messages);
            handleFlusherQueueOverflow();
        }

        @Override
        protected void flush(Context context, Promise<Void> promise) {
            super.flush(context, promise);
            handleFlusherQueueOverflow();
        }
    }
}