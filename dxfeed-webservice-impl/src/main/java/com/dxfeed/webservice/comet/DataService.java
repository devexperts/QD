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
import com.devexperts.qd.QDFilter;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.impl.DXFeedImpl;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.ondemand.OnDemandService;
import com.dxfeed.webservice.DXFeedContext;
import com.dxfeed.webservice.EventSymbolMap;
import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.Promise;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.ServerSessionImpl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
public class DataService {
    private static final Logging log = Logging.getLogging(DataService.class);

    private static final String DATA_CHANNEL = "/service/data";
    private static final String TIME_SERIES_DATA_CHANNEL = "/service/timeSeriesData";
    private static final String STATE_CHANNEL = "/service/state";

    private static final int DEFAULT_BATCH_SIZE = 100;
    private int messageBatchSize = SystemProperties.getIntProperty(
        DataService.class, "messageBatchSize", DEFAULT_BATCH_SIZE, 1, 10_000);

    // ------------------------ static shared ------------------------

    private static final String ONDEMAND_NAME_PREFIX = "onDemand";
    private static final Map<String, Method> ONDEMAND_OPS = new HashMap<>();

    static {
        for (Method method : SessionState.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith(ONDEMAND_NAME_PREFIX)) {
                String op = name.substring(ONDEMAND_NAME_PREFIX.length());
                op = Character.toLowerCase(op.charAt(0)) + op.substring(1);
                ONDEMAND_OPS.put(op, method);
            }
        }
    }

    // ------------------------ instance ------------------------

    private DXEndpointImpl sharedEndpoint;
    private OnDemandService sharedOnDemand;

    private final ConcurrentHashMap<ServerSession, SessionState> sessions = new ConcurrentHashMap<>();

    @Session
    private ServerSession server;

    @PostConstruct
    public void init() {
        WebSocketTransportExtension.setOverflowHandler(this::toggleMessageProcessingDelaying);
        DXFeedContext.INSTANCE.acquire();
        sharedEndpoint = (DXEndpointImpl) DXFeedContext.INSTANCE.getEndpoint();
        sharedOnDemand = OnDemandService.getInstance(sharedEndpoint);
    }

    @PreDestroy
    public void destroy() {
        log.info("Closing");
        DXFeedContext.INSTANCE.release();
    }

    @Listener("/service/sub")
    public void sub(ServerSession remote, ServerMessage.Mutable message) {
        getOrCreateSession(remote).sub(message);
    }

    @Listener("/service/onDemand")
    public void onDemand(ServerSession remote, ServerMessage.Mutable message) {
        Map<String, Object> map = message.getDataAsMap();
        String op = (String) map.get("op");
        List<?> rawArgs = (List<?>) map.get("args");
        Method method = ONDEMAND_OPS.get(op);
        if (method == null)
            throw new IllegalArgumentException("Unsupported ondemand operation: " + op);
        try {
            invokeMethod(remote, method, rawArgs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke onDemand." + op + " with " + rawArgs, e);
        }
    }

    private void invokeMethod(ServerSession remote, Method method, List<?> rawArgs) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            Object rawArg = i < rawArgs.size() ? rawArgs.get(i) : null;
            Class<?> rawType = rawArg == null ? null : rawArg.getClass();
            Object arg = rawArg;
            if (paramType == Date.class) {
                if (rawType != null && Number.class.isAssignableFrom(rawType))
                    arg = new Date(((Number) rawArg).longValue());
                else if (rawType == String.class)
                    arg = TimeFormat.DEFAULT.parse((String) rawArg);
                else if (rawType != Date.class)
                    throw new IllegalArgumentException("Arg #" + i + " cannot be coerced to Date");
            } else if (paramType == double.class) {
                if (rawType == String.class)
                    arg = new Double((String) rawArg);
                else if (rawType != Double.class) {
                    if (rawType != null && Number.class.isAssignableFrom(rawType))
                        arg = ((Number) rawArg).doubleValue();
                    else
                        throw new IllegalArgumentException("Arg #" + i + " cannot be coerced to double");
                }
            }
            args[i] = arg;
        }
        method.invoke(getOrCreateSession(remote), args);
    }

    private SessionState getOrCreateSession(ServerSession remote) {
        QDFilter filter = (QDFilter) remote.getAttribute(DXFeedContext.FILTER_PARAM);
        if (filter == null) {
            filter = QDFilter.ANYTHING;
        }

        SessionState session = sessions.get(remote);
        if (session != null && !filter.toString().equals(session.filter.toString())) {
            session.removed(remote, false);
            session = null;
        }
        if (session != null) {
            return session;
        }
        session = new SessionState(remote, filter);
        SessionState result = sessions.putIfAbsent(remote, session);
        if (result != null) {
            return result;
        }
        remote.addListener(session);
        return session;
    }

    private void toggleMessageProcessingDelaying(ServerSessionImpl session, boolean delayProcessing) {
        SessionState state = sessions.get(session);
        if (state != null) {
            state.setDelaySubscriptions(delayProcessing);
        } else {
            // SessionState (basically an association between QD subscription and CometD transport session)
            // is initialized after subscription request from the client.
            //
            // So under normal operation it is OK to have no SessionState instance for some CometD
            // ServerSessionImpl instance before subscription happens in this channel.
            //
            // This could be a point to check though if something goes wrong, that's why it is logged at debug level.
            log.debug("SessionState not found for ServerSessionImpl " + session);
        }
    }

    private class SessionState implements ServerSession.RemoveListener, ServerSession.DeQueueListener,
        ServerSession.MaxQueueListener, ServerSession.Extension, PropertyChangeListener
    {
        private final ServerSession remote;
        private final ServerSessionImpl sessionImpl;
        private final QDFilter filter;

        private final Map<Class<?>, DXFeedSubscription<Object>> regularSubscriptionsMap = new HashMap<>();
        private final Map<Class<?>, DXFeedSubscription<Object>> timeSeriesSubscriptionsMap = new HashMap<>();
        private final EventSymbolMap symbolMap = new EventSymbolMap();

        private DXFeed feed;
        private OnDemandService onDemand;
        private DXFeedImpl onDemandFeed;
        private volatile boolean closed;
        private SessionStats stats = new SessionStats();

        @SuppressWarnings("deprecation")
        SessionState(@Nonnull ServerSession remote, @Nonnull QDFilter filter) {
            log.info("Create session=" + remote.getId() + ", filter=" + filter);
            this.remote = remote;
            this.sessionImpl = (remote instanceof ServerSessionImpl) ? (ServerSessionImpl) remote : null;
            this.filter = filter;
            this.feed = DXFeedContext.INSTANCE.getFeed(filter);

            stats.sessionId = remote.getId();
            stats.numSessions = 1;
            stats.createTime = stats.lastActiveTime = System.currentTimeMillis();

            remote.setAttribute(CometDMonitoring.STATS_ATTR, stats);
            remote.setAttribute(CometDMonitoring.TMP_STATS_ATTR, new SessionStats());
            remote.addExtension(this);

            deliverStateChange("replaySupported", sharedOnDemand.isReplaySupported());
        }

        private synchronized boolean makeClosedSync() {
            if (closed)
                return false;
            closed = true;
            return true;
        }

        // ServerSession.RemoveListener Interface Implementation
        @Override
        public void removed(ServerSession remote, boolean timeout) {
            if (!makeClosedSync())
                return;
            log.info("Close session=" + remote.getId() + ", timeout=" + timeout);
            OnDemandService onDemand = clearOnDemandButNotCloseItYet();
            if (onDemand != null) {
                onDemandFeed.closeImpl();
                onDemand.getEndpoint().close();
            }
            closeSubscriptions();
            remote.removeExtension(this);
            remote.removeListener(this);
            if (!remote.getExtensions().isEmpty()) {
                log.warn("ServerSession still has extensions after removal: " + remote.getExtensions());
            }
            if (sessionImpl != null) {
                CometReflectionUtil.sessionImplCleanup(sessionImpl);
                if (!sessionImpl.getListeners().isEmpty()) {
                    log.warn("ServerSessionImpl still has listeners after removal: " + sessionImpl.getListeners());
                }
                synchronized (sessionImpl.getLock()) {
                    sessionImpl.getQueue().clear();
                }
            }
            sessions.remove(remote);
        }

        private void applySubscriptionAction(Consumer<DXFeedSubscription<?>> action) {
            for (DXFeedSubscription<?> sub : regularSubscriptionsMap.values())
                action.accept(sub);
            for (DXFeedSubscription<?> sub : timeSeriesSubscriptionsMap.values())
                action.accept(sub);
        }

        private void closeSubscriptions() {
            applySubscriptionAction(DXFeedSubscription::close);
        }

        private void setDelaySubscriptions(boolean delaySubscriptions) {
            applySubscriptionAction(sub -> {
                Executor executor = sub.getExecutor();
                if (executor instanceof DelayableExecutor) {
                    DelayableExecutor de = (DelayableExecutor) executor;
                    de.setDelayProcessing(delaySubscriptions);
                } else {
                    log.warn("Not a DelayableExecutor " + executor + " on sub " + sub);
                }
            });
        }

        private class Listener<T extends EventType<?>> implements DXFeedEventListener<T> {
            private final Class<T> eventType;
            private final boolean timeSeries;
            private boolean sendScheme = true; // send on the first list of events

            private Listener(Class<T> eventType, boolean timeSeries) {
                this.eventType = eventType;
                this.timeSeries = timeSeries;
            }

            @Override
            public void eventsReceived(List<T> events) {
                if ((sessionImpl != null) && sessionImpl.isTerminated()) {
                    log.warn("Received a DXFeed event for terminated session " + sessionImpl.getId());
                    // For some reason the session removal listener was not called by cometd, running it manually
                    removed(sessionImpl, false);
                    return;
                }
                int length = events.size();
                for (int i = 0; i < length; i += messageBatchSize) {
                    // Break list of events into smaller batches
                    List<T> eventsBatch = events.subList(i, Math.min(length, i + messageBatchSize));

                    remote.deliver(server, timeSeries ? TIME_SERIES_DATA_CHANNEL : DATA_CHANNEL,
                        new DataMessage(sendScheme, eventType, eventsBatch, symbolMap), Promise.noop());

                    sendScheme = false; // don't need to send afterwards
                }
            }
        }

        @SuppressWarnings("unchecked")
        public synchronized void sub(ServerMessage.Mutable message) {
            Map<String, ?> map = (Map<String, ?>) message.getData();
            if (closed) {
                log.warn("sub[session=" + remote.getId() + "] ignored closed session: " + map);
                return;
            }
            if (log.debugEnabled())
                log.debug("sub[session=" + remote.getId() + "]: " + map);
            Boolean reset = (Boolean) map.get("reset");
            if (reset != null && reset)
                resetSub();
            processSub(false, false, (Map<String, List<?>>) map.get("remove"));
            processSub(true, false, (Map<String, List<?>>) map.get("add"));
            processSub(false, true, (Map<String, List<?>>) map.get("removeTimeSeries"));
            processSub(true, true, (Map<String, List<?>>) map.get("addTimeSeries"));

            stats.regSubscription(
                regularSubscriptionsMap.values().stream().mapToInt(sub -> sub.getSymbols().size()).sum(), false);
            stats.regSubscription(
                timeSeriesSubscriptionsMap.values().stream().mapToInt(sub -> sub.getSymbols().size()).sum(), true);
        }

        private void resetSub() {
            closeSubscriptions();
            regularSubscriptionsMap.clear();
            timeSeriesSubscriptionsMap.clear();
        }

        private void processSub(boolean addSub, boolean timeSeries, Map<String, List<?>> subMap) {
            if (subMap == null)
                return; // nothing to do
            for (Map.Entry<String, List<?>> entry : subMap.entrySet()) {
                String typeName = entry.getKey();
                Class<?> eventType = DXFeedContext.INSTANCE.getEventTypes().get(typeName);
                if (eventType == null)
                    continue;
                if (timeSeries && !TimeSeriesEvent.class.isAssignableFrom(eventType))
                    continue; // ignore mismatching subscription
                DXFeedSubscription<?> sub = getOrCreateSubscription(eventType, timeSeries);
                // Resolve symbols
                List<Object> symbols = entry.getValue().stream()
                    .map(o -> resolveSymbol(eventType, o, addSub, timeSeries))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (addSub) {
                    sub.addSymbols(symbols);
                } else {
                    sub.removeSymbols(symbols);
                    symbolMap.cleanupEventSymbolMapping(eventType, getSubscriptions(timeSeries).get(eventType));
                }
            }
        }

        // Can return null if JSON format is invalid
        //TODO Proper error handling
        private Object resolveSymbol(Class<?> eventType, Object value, boolean addSub, boolean timeSeries) {
            String eventSymbol;
            long fromTime;

            if (timeSeries && addSub) {
                // Subscription format for timeSeries is: { "eventSymbol" : "SYMBOL", "fromTime" : 1578000000000 }
                if (!(value instanceof Map)) {
                    return null;
                }
                Map<?,?> objMap = (Map<?, ?>) value;
                Object eventSymbolObj = objMap.get("eventSymbol");
                Object fromTimeObj = objMap.get("fromTime");

                if (!(eventSymbolObj instanceof String) || !(fromTimeObj instanceof Number)) {
                    return null;
                }
                eventSymbol = (String) eventSymbolObj;
                fromTime = ((Number) fromTimeObj).longValue();
            } else {
                // Unsubscription format is: "AAA"
                if (!(value instanceof String)) {
                    return null;
                }
                eventSymbol = (String) value;
                fromTime = 0;
            }

            // Resolve symbol
            Object result = symbolMap.resolveEventSymbolMapping(eventType, eventSymbol);
            // Time series must use special class for subscription
            return (timeSeries) ? new TimeSeriesSubscriptionSymbol<>(result, fromTime) : result;
        }

        private Map<Class<?>, DXFeedSubscription<Object>> getSubscriptions(boolean timeSeries) {
            return timeSeries ? timeSeriesSubscriptionsMap : regularSubscriptionsMap;
        }

        // PropertyChangeListener Interface Implementation
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            // deliver only time changes
            if ("time".equals(propertyName))
                deliverStateChange(propertyName, evt.getNewValue());
        }

        private void deliverStateChange(String propertyName, Object value) {
            Map<String, Object> stateChange =
                Collections.singletonMap(propertyName, value);
            remote.deliver(server, STATE_CHANNEL, stateChange, Promise.noop());
        }

        @SuppressWarnings("deprecation")
        private synchronized OnDemandService ensureOnDemand() {
            if (onDemand == null) {
                DXEndpointImpl endpoint = (DXEndpointImpl) DXEndpoint.create(DXEndpoint.Role.ON_DEMAND_FEED);
                onDemand = OnDemandService.getInstance(endpoint);
                onDemand.addPropertyChangeListener(this);
                onDemandFeed = new DXFeedImpl(endpoint, filter);
                attachTo(onDemandFeed);
            }
            return onDemand;
        }

        private synchronized OnDemandService getOnDemand() {
            return onDemand;
        }

        private synchronized OnDemandService clearOnDemandButNotCloseItYet() {
            OnDemandService onDemand = this.onDemand;
            this.onDemand = null;
            if (onDemand != null)
                onDemand.removePropertyChangeListener(this);
            return onDemand;
        }

        private synchronized void attachTo(DXFeed newFeed) {
            for (DXFeedSubscription<?> sub : regularSubscriptionsMap.values()) {
                feed.detachSubscriptionAndClear(sub);
                newFeed.attachSubscription(sub);
            }
            for (DXFeedSubscription<?> sub : timeSeriesSubscriptionsMap.values()) {
                feed.detachSubscriptionAndClear(sub);
                newFeed.attachSubscription(sub);
            }
            feed = newFeed;
        }

        // Available to JS API via /service/onDemand
        public void onDemandReplay(Date time, double speed) {
            log.info("onDemandReplay(" + TimeFormat.DEFAULT.format(time) + ", " + speed + ")");
            ensureOnDemand().replay(time, speed);
        }

        // Available to JS API via /service/onDemand
        public void onDemandSetSpeed(double speed) {
            log.debug("onDemandSetSpeed(" + speed + ")");
            OnDemandService onDemand = getOnDemand();
            if (onDemand != null)
                onDemand.setSpeed(speed);
        }

        // Available to JS API via /service/onDemand
        @SuppressWarnings("deprecation")
        public void onDemandStopAndResume() throws InterruptedException {
            log.info("onDemandStopAndResume()");
            OnDemandService onDemand = clearOnDemandButNotCloseItYet();
            if (onDemand != null) {
                attachTo(DXFeedContext.INSTANCE.getFeed(filter));
                onDemandFeed.awaitTerminationAndCloseImpl();
                onDemand.getEndpoint().closeAndAwaitTermination();
            }
        }

        // Available to JS API via /service/onDemand
        public void onDemandStopAndClear() {
            log.info("onDemandStopAndClear()");
            ensureOnDemand().stopAndClear();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private DXFeedSubscription<?> getOrCreateSubscription(Class<?> eventType, boolean timeSeries) {
            Map<Class<?>, DXFeedSubscription<Object>> subscriptions = getSubscriptions(timeSeries);
            DXFeedSubscription<Object> sub = subscriptions.get(eventType);
            if (sub == null) {
                sub = feed.createSubscription(eventType);
                sub.addEventListener(new Listener(eventType, timeSeries));
                sub.setExecutor(new DelayableExecutor(sharedEndpoint::getOrCreateExecutor));
                subscriptions.put(eventType, sub);
            }
            return sub;
        }

        // ServerSession.Extension Interface Implementation

        @Override
        public boolean rcv(ServerSession session, ServerMessage.Mutable message) {
            if (message instanceof DataMessage) {
                stats.readEvents += ((DataMessage) message).getEvents().size();
            }
            stats.lastActiveTime = System.currentTimeMillis();
            stats.read++;
            return true;
        }

        @Override
        public boolean rcvMeta(ServerSession session, ServerMessage.Mutable message) {
            stats.readMeta++;
            return true;
        }

        @Override
        public ServerMessage send(ServerSession sender, ServerSession session, ServerMessage message) {
            if (message.getData() instanceof DataMessage) {
                stats.writeEvents += ((DataMessage) message.getData()).getEvents().size();
            }
            if (sessionImpl != null) {
                synchronized (sessionImpl.getLock()) {
                    stats.regQueueSize(((ServerSessionImpl) remote).getQueue().size());
                }
            }
            stats.lastActiveTime = System.currentTimeMillis();
            stats.write++;
            return message;
        }

        @Override
        public boolean sendMeta(ServerSession sender, ServerSession session, ServerMessage.Mutable message) {
            stats.writeMeta++;
            return true;
        }

        @Override
        public boolean queueMaxed(ServerSession session, Queue<ServerMessage> queue, ServerSession sender,
            Message message)
        {
            if (session.isConnected()) {
                log.warn("session=" + remote.getId() + " closed due to outgoing queue overflow: " + queue.size());
                session.disconnect();
            }
            return false;
        }

        @Override
        public void deQueue(ServerSession session, Queue<ServerMessage> queue) {
            stats.lastSendTime = System.currentTimeMillis();
        }
    }
}
