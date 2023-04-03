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
package com.dxfeed.webservice;

import com.devexperts.qd.QDFilter;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.impl.DXFeedImpl;
import com.dxfeed.event.EventType;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.market.MarketEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * dxFeed instance that is shared among various web services.
 */
public class DXFeedContext {
    public static final DXFeedContext INSTANCE = new DXFeedContext();
    public static final String CONFIG_CONTEXT = "java:comp/env/" + DXEndpoint.DXFEED_PROPERTIES_PROPERTY;
    public static final String FILTER_PARAM = DXFeedContext.class.getName() + ".filter";

    public enum Group {
        MARKET("Market events", "javadoc/com/dxfeed/event/market/MarketEvent.html", "MarketEvent"),
        CANDLE("Candle symbol events", "javadoc/com/dxfeed/event/candle/CandleSymbol.html", "CandleSymbol"),
        TIME_SERIES("Other non-candle time series events", "javadoc/com/dxfeed/event/TimeSeriesEvent.html", "TimeSeriesEvent"),
        INDEXED("Indexed events", "javadoc/com/dxfeed/event/IndexedEvent.html", "IndexedEvent"),
        OTHER("Other events", "javadoc/com/dxfeed/event/EventType.html", "EventType"),
        ;

        public final String title;
        public final String seeHRef;
        public final String seeName;

        Group(String title, String seeHRef, String seeName) {
            this.title = title;
            this.seeHRef = seeHRef;
            this.seeName = seeName;
        }
    }

    // ------------------------ instance ------------------------

    private final Map<String, Class<? extends EventType<?>>> eventTypes = new TreeMap<>();
    private int refCount;
    private DXEndpoint endpoint;
    private DXFeed feed;
    private Map<String, DXFeedImpl> filteredFeeds = new ConcurrentHashMap<>();

    private DXFeedContext() {}

    public synchronized void acquire() {
        if (refCount++ > 0)
            return;
        // use scratch tread to perform initialization to avoid pollution of web container's initialization thread
        // with our ThreadLocal variables
        runInScratchThread("DXFeedContext-Init", new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    public synchronized void release() {
        if (--refCount > 0)
            return;
        // use scratch tread to perform shutdown to avoid pollution of web container's initialization thread
        // with our ThreadLocal variables
        runInScratchThread("DXFeedContext-Shutdown", new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }

    private void init() {
        // will connect automatically using address from configuration file
        endpoint = newBuilder().build();
        feed = endpoint.getFeed();
        for (Class<? extends EventType<?>> eventType : endpoint.getEventTypes()) {
            eventTypes.put(eventType.getSimpleName(), eventType);
        }
    }

    private void shutdown() {
        for (DXFeedImpl feed : filteredFeeds.values()) {
            feed.closeImpl();
        }
        endpoint.close();
        // let GC do its job faster
        endpoint = null;
        feed = null;
        filteredFeeds.clear();
        eventTypes.clear();
    }

    public Map<String, Class<? extends EventType<?>>> getEventTypes() {
        return eventTypes;
    }

    // group events by their type (regular, indexed, time series) and sort by full name (with package)
    public EnumMap<Group, Map<String, Class<? extends EventType<?>>>> getGroupedEventTypes() {
        EnumMap<Group, Map<String, Class<? extends EventType<?>>>> result = new EnumMap<>(Group.class);
        for (Class<? extends EventType<?>> eventType : eventTypes.values()) {
            Group group =
                Candle.class.isAssignableFrom(eventType) ? Group.CANDLE :
                TimeSeriesEvent.class.isAssignableFrom(eventType) ? Group.TIME_SERIES :
                IndexedEvent.class.isAssignableFrom(eventType) ? Group.INDEXED :
                MarketEvent.class.isAssignableFrom(eventType) ? Group.MARKET : Group.OTHER;
            Map<String, Class<? extends EventType<?>>> m = result.get(group);
            if (m == null)
                result.put(group, m = new TreeMap<>());
            m.put(eventType.getSimpleName(), eventType);
        }
        return result;
    }

    public DXEndpoint getEndpoint() {
        return endpoint;
    }

    public DXFeed getFeed() {
        return feed;
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public DXFeed getFeed(QDFilter filter) {
        Objects.requireNonNull(filter, "filter");
        if (!(endpoint instanceof DXEndpointImpl))
            throw new IllegalStateException("Unsupported DXEndpoint implementation!");

        return filteredFeeds.computeIfAbsent(filter.toString(),
            (key) -> new DXFeedImpl((DXEndpointImpl) endpoint, filter));
    }

    public DXEndpoint.Builder newBuilder() {
        DXEndpoint.Builder builder = DXEndpoint.newBuilder();
        // use properties file path from the context
        String configFilePath = loadConfigFilePath();
        if (configFilePath != null)
            builder.withProperty(DXEndpoint.DXFEED_PROPERTIES_PROPERTY, configFilePath);
        return builder;
    }

    private String loadConfigFilePath() {
        try {
            InitialContext ctx = new InitialContext();
            try {
                return (String) ctx.lookup(CONFIG_CONTEXT);
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            // just ignore exception to avoid log pollution
            return null;
        }
    }

    private void runInScratchThread(String threadName, Runnable runnable) {
        Thread thread = new Thread(runnable, threadName);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // reassert interruption flag
        }
    }
}
