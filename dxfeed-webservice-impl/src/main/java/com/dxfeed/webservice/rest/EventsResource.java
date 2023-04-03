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

import com.devexperts.annotation.Description;
import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFilter;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;
import com.dxfeed.promise.Promises;
import com.dxfeed.webservice.DXFeedContext;
import com.dxfeed.webservice.EventSymbolMap;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.dxfeed.webservice.rest.Secure.SecureRole.AUTH_REQUEST;
import static com.dxfeed.webservice.rest.Secure.SecureRole.AUTH_SESSION;
import static com.dxfeed.webservice.rest.Secure.SecureRole.NONE;

/**
 * @dgen.annotate method { name = "do.*"; access = public; }
 * field { access = public; }
 */
public class EventsResource {

    public static final String HELP_PATH = "/help";
    public static final String DEFAULT_SESSION = "DEFAULT_EVENT_SOURCE";
    public static final long DEFAULT_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty(EventsResource.class, "defaultTimeout", "3s")).getTime();

    // ============================== introspection ==============================

    public static final Map<String, PathInfo> PATHS = new LinkedHashMap<>();
    public static final Map<String, ParamInfo> PARAMS = new LinkedHashMap<>();

    static {
        List<PathInfo> pathInfos = new ArrayList<>();
        for (Method method : EventsResource.class.getDeclaredMethods()) {
            Path path = method.getAnnotation(Path.class);
            if (path != null)
                pathInfos.add(new PathInfo(method));
        }
        Collections.sort(pathInfos);
        for (PathInfo pathInfo : pathInfos) {
            PATHS.put(pathInfo.path, pathInfo);
        }
        for (Field field : EventsResource.class.getFields()) {
            Param param = field.getAnnotation(Param.class);
            if (param != null) {
                String name = param.value();
                PARAMS.put(name, new ParamInfo(name,
                    ParamType.forClass(field.getType()),
                    field.getAnnotation(Description.class).value(), field));
            }
        }
    }

    // ============================== private fields  ==============================

    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private final Format format;
    private final Logging log;

    // ============================== constructor  ==============================

    public EventsResource(HttpServletRequest req, HttpServletResponse resp, Format format) {
        this.req = req;
        this.resp = resp;
        this.format = format;
        /*
         * Design note: if this reflection-based code is ever found to be slow, please replace reflection with
         * a code that is automatically generated during compilation and does the same function.
         */
        for (Map.Entry<String, ParamInfo> entry : PARAMS.entrySet()) {
            try {
                ParamInfo paramInfo = entry.getValue();
                paramInfo.field.set(this, getRequestValue(paramInfo));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        log = new Logging(EventsResource.class.getName()) {
            @Override
            protected String decorateLogMessage(String msg) {
                return "(" + EventsResource.this.req.getRemoteAddr() + ") " + msg;
            }
        };
    }

    Object getRequestValue(ParamInfo paramInfo) {
        return paramInfo.type.getValue(paramInfo.name, req);
    }

    // ============================== param fields  ==============================

    /**
     * The type of event (like "Quote", "Trade", etc).
     */
    @Param("event")
    public List<String> eventList;

    /**
     * The symbol (like "IBM", "MSFT", etc).
     */
    @Param("symbol")
    public List<String> symbolList;

    /**
     * The time from which to return events.
     * It is required for time series events and is ignored for other types of events.
     */
    @Param("fromTime")
    public Date fromTime;

    /**
     * The source for indexed events. It is optional and is ignored for non-indexed events.
     * " For "events" resource DEFAULT source is used by default.
     * " For "eventSource" resource and for subscription changes all sources are used by default.
     */
    @Param("source")
    public List<String> sourceList;

    /**
     * The result is indented for better readability when this parameter is set.
     */
    @Param("indent")
    public String indent;

    // ============================== resource methods  ==============================

    /**
     * Shows human-readable help.
     */
    @Path(HELP_PATH)
    @Secure(NONE)
    @HelpOrder(0)
    public void doHelp() throws ServletException, IOException {
        req.getRequestDispatcher("/jsp/rest/help.jsp").forward(req, resp);
    }

    /**
     * Returns a snapshot of events.
     *
     * @param toTime  The time to which to return events.
     *                It is optional for time series events and is ignored for other types of events.
     * @param timeout The maximal time to wait events from the data sources.
     *                Use timeout=0 to return results from memory cache, given that the subscription was already established.
     * @throws HttpErrorException
     */
    @SuppressWarnings("unchecked")
    @Path("/events")
    @Secure(AUTH_REQUEST)
    @HelpOrder(1)
    public void doEvents(Date toTime, TimePeriod timeout) throws HttpErrorException {
        if (timeout != null && timeout.getTime() == 0) {
            doEventsWithoutTimeout(toTime);
            return;
        }
        // request events
        List<Promise<?>> promiseList = new ArrayList<>(eventList.size() * symbolList.size());
        EventSymbolMap symbolMap = new EventSymbolMap();
        buildPromisesList(toTime, promiseList, symbolMap);
        // handle asynchronously
        AsyncContext async = req.startAsync();
        // defer response creation to async handler
        Promise<Void> allPromises = Promises.allOf(promiseList);
        long timeoutMillis = timeout == null ? DEFAULT_TIMEOUT : timeout.getTime();
        EventRequestHandler requestHandler = new EventRequestHandler(async,
            promiseList, allPromises, symbolMap, timeoutMillis);
        log.info("Processing " + requestHandler);
        // configure timeout
        async.addListener(requestHandler);
        async.setTimeout(timeoutMillis);
        // wait for all promises
        allPromises.whenDone(requestHandler);
    }

    @SuppressWarnings("unchecked")
    private void buildPromisesList(Date toTime, List<Promise<?>> promiseList, EventSymbolMap symbolMap)
        throws HttpErrorException
    {
        DXFeed feed = getFeed(getFilter());
        List<OrderSource> sourceList = resolveSourceList(Collections.singletonList(OrderSource.DEFAULT));
        for (String evt : eventList) {
            Class<?> et = DXFeedContext.INSTANCE.getEventTypes().get(evt);
            if (et == null)
                throw unknownEventType(evt);
            if (TimeSeriesEvent.class.isAssignableFrom(et) && fromTime != null) {
                long fromTimeL = fromTime.getTime();
                long toTimeL = toTime == null ? Long.MAX_VALUE : toTime.getTime();
                for (String sym : symbolList) {
                    promiseList.add(feed.getTimeSeriesPromise((Class<TimeSeriesEvent<?>>) et,
                        symbolMap.resolveEventSymbolMapping(et, sym), fromTimeL, toTimeL));
                }
            } else if (LastingEvent.class.isAssignableFrom(et)) {
                promiseList.addAll(feed.getLastEventsPromises((Class<LastingEvent<?>>) et,
                    symbolMap.resolveEventSymbolMappings(et, symbolList)));
            } else if (IndexedEvent.class.isAssignableFrom(et)) {
                for (String sym : symbolList) {
                    for (OrderSource src : sourceList) {
                        promiseList.add(feed.getIndexedEventsPromise((Class<IndexedEvent<?>>) et,
                            symbolMap.resolveEventSymbolMapping(et, sym), src));
                    }
                }
            } else {
                /**
                 * As a result, if no events of a particular type are received,
                 * the GET-method will return a TIMED_OUT error.
                 * To activate this logic, we include a Promise with a null result
                 * that is triggered in the {@link EventRequestHandler#buildResponse(String)} method.
                 */
                Promise fail = new Promise();
                fail.complete(null);
                promiseList.add(fail);
            }
        }
    }

    private void doEventsWithoutTimeout(Date toTime) throws HttpErrorException {
        int requestSize = eventList.size() * symbolList.size();
        List<EventType<?>> eventsList = new ArrayList<>(requestSize);
        EventSymbolMap symbolMap = new EventSymbolMap();
        boolean ok = buildEventsList(toTime, eventsList, symbolMap);
        Events result = new Events();
        result.setStatus(ok ? Events.Status.OK : Events.Status.NOT_SUBSCRIBED);
        result.setEvents(eventsList);
        result.setSymbolMap(symbolMap);
        String logRespReason = "event request [size=" + requestSize + ", timeout=0] " +
            "with " + eventsList.size() + " events (" + result.getStatus() + ")";
        if (writeResponse(result, logRespReason))
            log.info("Sent response for " + logRespReason);
    }

    @SuppressWarnings("unchecked")
    private boolean buildEventsList(Date toTime, List<EventType<?>> eventsList, EventSymbolMap symbolMap)
        throws HttpErrorException
    {
        boolean ok = true;
        DXFeed feed = getFeed(getFilter());
        List<OrderSource> sourceList = resolveSourceList(Collections.singletonList(OrderSource.DEFAULT));
        for (String evt : eventList) {
            Class<?> et = DXFeedContext.INSTANCE.getEventTypes().get(evt);
            if (et == null)
                throw unknownEventType(evt);
            if (TimeSeriesEvent.class.isAssignableFrom(et) && fromTime != null) {
                long fromTimeL = fromTime.getTime();
                long toTimeL = toTime == null ? Long.MAX_VALUE : toTime.getTime();
                for (String sym : symbolList) {
                    List<TimeSeriesEvent<?>> events = feed.getTimeSeriesIfSubscribed((Class<TimeSeriesEvent<?>>) et,
                        symbolMap.resolveEventSymbolMapping(et, sym), fromTimeL, toTimeL);
                    if (events == null)
                        ok = false;
                    else
                        eventsList.addAll(events);
                }
            } else if (LastingEvent.class.isAssignableFrom(et)) {
                for (String sym : symbolList) {
                    LastingEvent<?> event = feed.getLastEventIfSubscribed((Class<LastingEvent<?>>) et,
                        symbolMap.resolveEventSymbolMapping(et, sym));
                    if (event == null)
                        ok = false;
                    else
                        eventsList.add(event);
                }
            } else if (IndexedEvent.class.isAssignableFrom(et)) {
                for (String sym : symbolList) {
                    for (OrderSource src : sourceList) {
                        List<IndexedEvent<?>> events = feed.getIndexedEventsIfSubscribed((Class<IndexedEvent<?>>) et,
                            symbolMap.resolveEventSymbolMapping(et, sym), src);
                        if (events == null)
                            ok = false;
                        else
                            eventsList.addAll(events);
                    }
                }
            } else {
                /**
                 * Method returns ok: true - events were received, false - NOT_SUBSCRIBED.
                 * In cases where events are not supported by GET methods, it is not possible to confirm the success of
                 * a subscription. Therefore, we throw an exception to prevent any potential confusion for clients
                 * attempting to verify their subscription status.
                 */
                throw getNotSupported(evt);
            }
        }
        return ok;
    }

    private List<OrderSource> resolveSourceList(List<OrderSource> defList) {
        if (sourceList.isEmpty())
            return defList;
        List<OrderSource> result = new ArrayList<>(sourceList.size());
        for (String s : sourceList) {
            result.add(OrderSource.valueOf(s));
        }
        return result;
    }

    /**
     * Subscribes to event updates and returns a stream of Server-Sent Events.
     *
     * @param session Session name. When set, then new web session is created (cookie is set) if needed and this
     *                     subscription is stored into the session under a name specified in this parameter, so that this subscription
     *                     can be later modified. The value of "DEFAULT_EVENT_SOURCE" is used by default when "session" parameter is set
     *                     to an empty string.
     *                     Please notice that the new web session is not created and session ID cookie is not returned
     *                     if the request already contains a valid cookie for existing session ID.
     * @param reconnect    Reconnect flag. When set, then existing session is recovered with its subscription.
     *                     "session" parameter is implied when "reconnect" is set.
     */
    @Path("/eventSource")
    @Secure(AUTH_SESSION)
    @HelpOrder(2)
    public void doEventSource(String session, String reconnect)
        throws IOException, HttpErrorException
    {
        String name = session == null || session.isEmpty() ? DEFAULT_SESSION : session;
        QDFilter filter = getFilter();

        EventConnection conn;
        if (reconnect != null) {
            HttpSession httpSession = req.getSession(false);
            if (httpSession == null)
                throw sessionNotFound("httpSession is null");
            Object attr = httpSession.getAttribute(name);
            if (attr instanceof EventConnection) {
                conn = (EventConnection) attr;
                if (!filter.toString().equals(conn.filter.toString())) {
                    log.warn("Filters in " + name + " differ: " + filter + " vs. " + conn.filter);
                    throw sessionNotFound("bad filters in " + name);
                }
            } else {
                throw sessionNotFound("no connection " + name);
            }
        } else {
            conn = new EventConnection(getFeed(filter), filter);
        }
        // update subscription
        updateSubscription(SubOp.ADD_SUB, conn);
        // store in new session if requested (DO getSession BEFORE STARTING ASYNC)
        req.getSession(true).setAttribute(name, conn);
        // start asynchronous connection
        boolean wasActive = conn.start(req.startAsync(), format, indent);
        log.info((reconnect != null ? "Restarted " : "Started ") + (wasActive ? "active " : "") + conn);
        // immediately send a heartbeat (will flush response, too)
        conn.heartbeat();
    }

    /**
     * Adds subscription to the previously created event stream.
     *
     * @param session Session name.
     * @throws HttpErrorException
     */
    @Path("/addSubscription")
    @Secure(AUTH_REQUEST)
    @HelpOrder(3)
    public void doAddSubscription(String session) throws HttpErrorException, IOException {
        HttpSession httpSession = req.getSession(false);
        if (httpSession == null)
            throw sessionNotFound("httpSession is null");
        String name = session == null || session.isEmpty() ? DEFAULT_SESSION : session;
        EventConnection conn = (EventConnection) httpSession.getAttribute(name);
        if (conn == null)
            throw sessionNotFound("no connection " + name);
        updateSubscription(SubOp.ADD_SUB, conn);
        if (writeResponse(new SubResponse(SubResponse.Status.OK), conn))
            log.info("Added subscription to " + conn);
    }

    /**
     * Removes subscription from the previously created event stream (use same parameters as for subscription).
     *
     * @param session Session name.
     */
    @Path("/removeSubscription")
    @Secure(AUTH_REQUEST)
    @HelpOrder(4)
    public void doRemoveSubscription(String session) throws HttpErrorException, IOException {
        HttpSession httpSession = req.getSession(false);
        if (httpSession == null)
            throw sessionNotFound("httpSession is null");
        String name = session == null || session.isEmpty() ? DEFAULT_SESSION : session;
        EventConnection conn = (EventConnection) httpSession.getAttribute(name);
        if (conn == null)
            throw sessionNotFound("no connection " + name);
        updateSubscription(SubOp.REMOVE_SUB, conn);
        if (writeResponse(new SubResponse(SubResponse.Status.OK), conn))
            log.info("Removed subscription from " + conn);
    }

    // ============================== private methods  ==============================

    private void updateSubscription(SubOp subOp, EventConnection conn) throws HttpErrorException {
        List<Object> symbols = new ArrayList<>();
        for (String evt : eventList) {
            Class<? extends EventType<?>> et = DXFeedContext.INSTANCE.getEventTypes().get(evt);
            if (et == null)
                throw unknownEventType(evt);
            DXFeedSubscription<EventType<?>> sub = conn.subscriptions.get(et);
            if (sub == null && subOp == SubOp.ADD_SUB)
                sub = conn.createSubSync(et);
            if (sub == null)
                continue; // just continue if removing from non-existent subscription

            // If unsubscribing from time series subscription fromTime must be specified!
            if (TimeSeriesEvent.class.isAssignableFrom(et) && fromTime != null) {
                long fromTimeL = fromTime.getTime();
                for (String sym : symbolList) {
                    symbols.add(new TimeSeriesSubscriptionSymbol<>(
                        conn.symbolMap.resolveEventSymbolMapping(et, sym),
                        fromTimeL));
                }
            } else if (LastingEvent.class.isAssignableFrom(et)) {
                symbols.addAll(conn.symbolMap.resolveEventSymbolMappings(et, symbolList));
            } else if (IndexedEvent.class.isAssignableFrom(et)) {
                if (sourceList.isEmpty()) {
                    symbols.addAll(symbolList);
                } else {
                    for (String sym : symbolList) {
                        for (String src : sourceList) {
                            symbols.add(new IndexedEventSubscriptionSymbol<>(
                                conn.symbolMap.resolveEventSymbolMapping(et, sym),
                                OrderSource.valueOf(src)));
                        }
                    }
                }
            } else {
                symbols.addAll(conn.symbolMap.resolveEventSymbolMappings(et, symbolList));
            }
            if (subOp == SubOp.ADD_SUB)
                sub.addSymbols(symbols);
            else
                sub.removeSymbols(symbols);
            symbols.clear(); // reuse list for the next event type
        }
    }

    @Nonnull
    private HttpErrorException unknownEventType(String evt) {
        return new HttpErrorException(HttpServletResponse.SC_BAD_REQUEST, "Unknown event type: " + evt);
    }

    @Nonnull
    private HttpErrorException getNotSupported(String evt) {
        return new HttpErrorException(HttpServletResponse.SC_BAD_REQUEST, "GET method not supported for event type: " + evt);
    }

    @Nonnull
    private HttpErrorException sessionNotFound(String message) {
        return new HttpErrorException(HttpServletResponse.SC_PRECONDITION_FAILED, "Session not found: " + message);
    }

    private boolean writeResponse(Object result, Object logErrReason) {
        resp.setHeader("Content-Type", format.mediaType);
        try {
            format.writeTo(result, resp.getOutputStream(), indent);
            return true;
        } catch (IOException e) {
            log.warn("Failed to write response for " + logErrReason, e);
            // attempt to report internal server error
            try {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to write response");
            } catch (IllegalStateException | IOException e1) {
                // ignore this error -- cannot send error is not critical, but we must terminate normally
            }
            return false;
        }
    }

    private QDFilter getFilter() {
        QDFilter filter = (QDFilter) req.getAttribute(DXFeedContext.FILTER_PARAM);
        return (filter != null) ? filter : QDFilter.ANYTHING;
    }

    @SuppressWarnings("deprecation")
    private DXFeed getFeed(QDFilter filter) {
        return DXFeedContext.INSTANCE.getFeed(filter);
    }

    // ============================== helper classes  ==============================

    private static final AtomicLong REQUEST_ID = new AtomicLong();

    /**
     * Class for "/events" request with timeout.
     */
    private class EventRequestHandler implements AsyncListener, PromiseHandler<Void> {
        private final long id = REQUEST_ID.incrementAndGet();
        private final AsyncContext async;
        private final List<Promise<?>> promiseList;
        private final Promise<Void> allPromises;
        private final EventSymbolMap symbolMap;
        private final long timeoutMillis;
        private final AtomicBoolean responded = new AtomicBoolean();

        EventRequestHandler(AsyncContext async, List<Promise<?>> promiseList, Promise<Void> allPromises,
            EventSymbolMap symbolMap, long timeoutMillis)
        {
            this.async = async;
            this.promiseList = promiseList;
            this.allPromises = allPromises;
            this.symbolMap = symbolMap;
            this.timeoutMillis = timeoutMillis;
        }

        @SuppressWarnings("unchecked")
        void buildResponse(String reason) {
            // build a response at most once (there are be a race between timeout and done)
            if (!responded.compareAndSet(false, true))
                return;
            try {
                // create events list
                List<EventType<?>> eventsList = new ArrayList<>();
                boolean ok = true;
                for (Promise<?> p : promiseList) {
                    Object promiseResult = p.getResult();
                    if (promiseResult instanceof List)
                        eventsList.addAll((List<EventType<?>>) promiseResult);
                    else if (promiseResult != null)
                        eventsList.add((EventType<?>) promiseResult);
                    else
                        ok = false;
                }
                // serialize result
                Events result = new Events();
                result.setStatus(ok ? Events.Status.OK : Events.Status.TIMED_OUT);
                result.setEvents(eventsList);
                result.setSymbolMap(symbolMap);
                // provide result
                String logRespReason = this + " with " + eventsList.size() + " events (" + result.getStatus() + ") on " + reason;
                if (writeResponse(result, logRespReason))
                    log.info("Sent response for " + logRespReason);
            } finally {
                // complete async request even if the above code crashes for any mysterious reason
                async.complete();
            }
        }

        @Override
        public void promiseDone(Promise<? extends Void> promise) {
            buildResponse("done");
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            allPromises.cancel();
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            try {
                buildResponse("timeout");
            } finally {
                // do it just in case container forgets to call "onComplete" and even if it all mysteriously crashes
                allPromises.cancel();
            }
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            allPromises.cancel();
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
        }

        @Override
        public String toString() {
            return "event request #" + id + " " +
                "[size=" + promiseList.size() + ", timeout=" + timeoutMillis + "ms]";
        }
    }

    enum SubOp { ADD_SUB, REMOVE_SUB }

    /**
     * Class for "/eventSource" connection.
     */
    private static class EventConnection extends SSEConnection implements DXFeedEventListener<EventType<?>> {
        private static final long serialVersionUID = 0;

        private final Map<Class<? extends EventType<?>>, DXFeedSubscription<EventType<?>>> subscriptions =
            new ConcurrentHashMap<>();
        private final EventSymbolMap symbolMap = new EventSymbolMap();

        private final QDFilter filter;
        private final DXFeed feed;

        @GuardedBy("this")
        private Format format;
        @GuardedBy("this")
        private String indent;

        @SuppressWarnings("deprecation")
        public EventConnection(@Nonnull DXFeed feed, @Nonnull QDFilter filter) {
            this.feed = feed;
            this.filter = filter;
        }

        // returns null if it is already closed and cannot add anything more
        private synchronized DXFeedSubscription<EventType<?>> createSubSync(Class<? extends EventType<?>> et) {
            DXFeedSubscription<EventType<?>> sub = subscriptions.get(et);
            if (sub != null)
                return sub;
            sub = new DXFeedSubscription<>(et);
            sub.addEventListener(this);
            subscriptions.put(et, sub);
            // only attach subscription on connection that is already active
            if (isActive())
                feed.attachSubscription(sub);
            return sub;
        }

        public synchronized boolean start(AsyncContext async, Format format, String indent) throws IOException {
            this.format = format;
            this.indent = indent;
            return start(async);
        }

        @Override
        @GuardedBy("this")
        protected void startImpl() {
            // attach subscriptions
            for (DXFeedSubscription<EventType<?>> sub : subscriptions.values())
                feed.attachSubscription(sub);
        }

        @Override
        @GuardedBy("this")
        protected void stopImpl() {
            // close subscriptions
            for (DXFeedSubscription<EventType<?>> sub : subscriptions.values())
                feed.detachSubscription(sub);
        }

        @Override
        public void heartbeatImpl() {
            writeEvents(Collections.<EventType<?>>emptyList());
        }

        @Override
        public void eventsReceived(List<EventType<?>> eventsList) {
            writeEvents(eventsList);
        }

        private void writeEvents(List<EventType<?>> eventsList) {
            if (!isActive())
                return;
            Events result = new Events();
            result.setStatus(Events.Status.OK);
            result.setEvents(eventsList);
            result.setSymbolMap(symbolMap);
            try {
                if (!writeObject(result))
                    return; // already closed
            } catch (EOFException e) {
                if (stop())
                    log.info("Stopped, because connection terminated for " + this);
            } catch (IOException e) {
                if (stop())
                    log.warn("Stopped, because failed to write events for " + this, e);
            }
        }

        // Synchronized to avoid race between heartbeat, eventsReceived, and start/stop
        private synchronized boolean writeObject(Events result) throws IOException {
            if (!isActive())
                return false;
            format.writeTo(result, out, indent);
            out.endMessage();
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("event connection #");
            sb.append(id);
            sb.append(", filter=");
            sb.append(filter);
            sb.append(" [");
            int i = 0;
            for (Map.Entry<Class<? extends EventType<?>>, DXFeedSubscription<EventType<?>>> entry : subscriptions.entrySet()) {
                if (i > 0)
                    sb.append(',');
                i++;
                sb.append(entry.getKey().getSimpleName());
                sb.append('=');
                sb.append(entry.getValue().getSymbols().size());
            }
            sb.append(']');
            return sb.toString();
        }

    }
}
