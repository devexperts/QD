/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.webservice.comet;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.devexperts.logging.Logging;
import com.devexperts.util.TimeFormat;
import com.dxfeed.api.*;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.ondemand.OnDemandService;
import com.dxfeed.webservice.DXFeedContext;
import com.dxfeed.webservice.EventSymbolMap;
import org.cometd.annotation.*;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.ServerSessionImpl;

@Service
public class DataService {
    private static final Logging log = Logging.getLogging(DataService.class);

    private static final String DATA_CHANNEL = "/service/data";
    private static final String TIME_SERIES_DATA_CHANNEL = "/service/timeSeriesData";
    private static final String STATE_CHANNEL = "/service/state";

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

    private DXEndpoint sharedEndpoint;
    private OnDemandService sharedOnDemand;
    private DXFeed sharedFeed;

    private final ConcurrentHashMap<ServerSession, SessionState> sessions = new ConcurrentHashMap<>();

    @Session
    private ServerSession server;

    @PostConstruct
    public void init() {
        DXFeedContext.INSTANCE.acquire();
        sharedEndpoint = DXFeedContext.INSTANCE.getEndpoint();
        sharedOnDemand = OnDemandService.getInstance(sharedEndpoint);
        sharedFeed = sharedEndpoint.getFeed();
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
        SessionState session = sessions.get(remote);
        if (session != null)
            return session;
        session = new SessionState(remote);
        SessionState result = sessions.putIfAbsent(remote, session);
        if (result != null)
            return result;
        remote.addListener(session);
        return session;
    }

    private class SessionState
        implements ServerSession.RemoveListener, ServerSession.Extension, PropertyChangeListener
    {
        private final ServerSession remote;
        private final ServerSessionImpl sessionImpl;

        private final Map<Class<?>, DXFeedSubscription<Object>> regularSubscriptionsMap = new HashMap<>();
        private final Map<Class<?>, DXFeedSubscription<Object>> timeSeriesSubscriptionsMap = new HashMap<>();
        private final EventSymbolMap symbolMap = new EventSymbolMap();

        private DXFeed feed = sharedFeed;
        private OnDemandService onDemand;
        private volatile boolean closed;
        private SessionStats stats = new SessionStats();
        private SessionStats tmpStats = new SessionStats();

        SessionState(ServerSession remote) {
            log.info("Create session=" + remote.getId());
            this.remote = remote;
            this.sessionImpl = (remote instanceof ServerSessionImpl) ? (ServerSessionImpl) remote : null;

            stats.sessionId = remote.getId();
            stats.numSessions = 1;
            stats.createTime = stats.lastActiveTime = System.currentTimeMillis();

            remote.setAttribute(CometDMonitoring.STATS_ATTR, stats);
            remote.setAttribute(CometDMonitoring.TMP_STATS_ATTR, tmpStats);
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
            if (onDemand != null)
                onDemand.getEndpoint().close();
            closeSubscriptions();
            sessions.remove(remote);
        }

        private void closeSubscriptions() {
            for (DXFeedSubscription<?> sub : regularSubscriptionsMap.values())
                sub.close();
            for (DXFeedSubscription<?> sub : timeSeriesSubscriptionsMap.values())
                sub.close();
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
                synchronized (SessionState.this) {
                    // need to sync for potential candleSymbolMap updates
                    remote.deliver(server, timeSeries ? TIME_SERIES_DATA_CHANNEL : DATA_CHANNEL,
                        new DataMessage(sendScheme, eventType, events, symbolMap));
                }
                sendScheme = false; // don't need to send afterwards
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

        @SuppressWarnings({"unchecked"})
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
                // resolve symbols
                List<Object> value = (List<Object>) entry.getValue();
                List<Object> symbols = new ArrayList<>(value.size());
                if (timeSeries && addSub) {
                    for (Object obj : value) {
                        Map<String,?> objMap = (Map<String, ?>) obj;
                        String eventSymbol = (String) objMap.get("eventSymbol");
                        Object fromTimeObj = objMap.get("fromTime");
                        if (!(fromTimeObj instanceof Number))
                            continue; // broken "fromTime" from JP API user -- ignore.
                        long fromTime = ((Number) fromTimeObj).longValue();
                        symbols.add(new TimeSeriesSubscriptionSymbol<>(
                            symbolMap.resolveEventSymbolMapping(eventType, eventSymbol), fromTime));
                    }
                } else {
                    for (Object obj : value)
                        symbols.add(symbolMap.resolveEventSymbolMapping(eventType, (String) obj));
                }
                if (addSub) {
                    sub.addSymbols(symbols);
                } else {
                    sub.removeSymbols(symbols);
                    symbolMap.cleanupEventSymbolMapping(eventType, getSubscriptions(timeSeries).get(eventType));
                }
            }
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
            remote.deliver(server, STATE_CHANNEL, stateChange);
        }

        private synchronized OnDemandService ensureOnDemand() {
            if (onDemand == null) {
                DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.ON_DEMAND_FEED);
                onDemand = OnDemandService.getInstance(endpoint);
                onDemand.addPropertyChangeListener(this);
                attachTo(endpoint.getFeed());
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
        public void onDemandStopAndResume() throws InterruptedException {
            log.info("onDemandStopAndResume()");
            OnDemandService onDemand = clearOnDemandButNotCloseItYet();
            if (onDemand != null) {
                attachTo(sharedFeed);
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
        public ServerMessage send(ServerSession session, ServerMessage message) {
            if (message.getData() instanceof DataMessage) {
                stats.writeEvents += ((DataMessage) message.getData()).getEvents().size();
            }
            if (sessionImpl != null) {
                stats.regQueueSize(((ServerSessionImpl) remote).getQueue().size());
            }
            stats.lastActiveTime = System.currentTimeMillis();
            stats.write++;
            return message;
        }

        @Override
        public boolean sendMeta(ServerSession session, ServerMessage.Mutable message) {
            stats.writeMeta++;
            return true;
        }
    }
}
