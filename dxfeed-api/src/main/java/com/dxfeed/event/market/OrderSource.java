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
package com.dxfeed.event.market;

import com.devexperts.util.SynchronizedIndexedSet;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEventSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Identifies source of {@link Order}, {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder} events.
 * There are the following kinds of order sources:
 * <ul>
 * <li><em>Synthetic</em> sources {@link #COMPOSITE}, {@link #COMPOSITE_BID}, {@link #COMPOSITE_ASK},
 *     {@link #REGIONAL}, {@link #REGIONAL_BID}, and {@link #REGIONAL_ASK} are provided for convenience
 *     of a consolidated order book and are automatically generated based on the corresponding {@link Quote} events.
 * <li><em>Aggregate</em> sources {@link #AGGREGATE}, {@link #AGGREGATE_BID} and {@link #AGGREGATE_ASK} provide
 *     futures depth (aggregated by price level) and NASDAQ Level II (top of book for each market maker).
 *     These sources are automatically generated based on the corresponding {@link MarketMaker} events.
 * <li>{@link #isPublishable(Class) Publishable} sources {@link #DEFAULT}, {@link #NTV}, and {@link #ISE}
 *     support full range of dxFeed API features.
 * </ul>
 */
public final class OrderSource extends IndexedEventSource {

    // ========================= private static =========================

    // Estimated cache size for proper balance of built-in and transient sources
    private static volatile int CACHE_SIZE = 100;
    private static final SynchronizedIndexedSet<Integer, OrderSource> SOURCES_BY_ID =
        SynchronizedIndexedSet.createInt(OrderSource::id).withCapacity(CACHE_SIZE);
    private static final SynchronizedIndexedSet<String, OrderSource> SOURCES_BY_NAME =
        SynchronizedIndexedSet.create(OrderSource::name).withCapacity(CACHE_SIZE);

    private static final int PUB_ORDER = 0x0001;
    private static final int PUB_ANALYTIC_ORDER = 0x0002;
    private static final int PUB_OTC_MARKETS_ORDER = 0x0004;
    private static final int PUB_SPREAD_ORDER = 0x0008;
    private static final int FULL_ORDER_BOOK = 0x0010;
    private static final int FLAGS_SIZE = 5;

    @SuppressWarnings("unchecked")
    private static final List<OrderSource>[] PUBLISHABLE_LISTS = new List[FLAGS_SIZE];
    @SuppressWarnings("unchecked")
    private static final List<OrderSource>[] PUBLISHABLE_VIEWS = new List[FLAGS_SIZE];
    static {
        for (int i = 0; i < FLAGS_SIZE; i++) {
            PUBLISHABLE_LISTS[i] = new ArrayList<>();
            PUBLISHABLE_VIEWS[i] = Collections.unmodifiableList(PUBLISHABLE_LISTS[i]);
        }
    }

    // ========================= public static =========================

    /**
     * Bid side of a composite {@link Quote}.
     * It is a <em>synthetic</em> and <em>separate</em> source.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to.
     * The subscription on composite {@link Quote} event is observed when this source is subscribed to.
     * @deprecated Use the {@link #COMPOSITE} source.
     */
    @Deprecated
    public static final OrderSource COMPOSITE_BID = new OrderSource(1, "COMPOSITE_BID", 0);

    /**
     * Ask side of a composite {@link Quote}.
     * It is a <em>synthetic</em> and <em>separate</em> source.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to.
     * The subscription on composite {@link Quote} event is observed when this source is subscribed to.
     * @deprecated Use the {@link #COMPOSITE} source.
     */
    @Deprecated
    public static final OrderSource COMPOSITE_ASK = new OrderSource(2, "COMPOSITE_ASK", 0);

    /**
     * Bid side of a regional {@link Quote}.
     * It is a <em>synthetic</em> and <em>separate</em> source.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to.
     * The subscription on regional {@link Quote} event is observed when this source is subscribed to.
     * @deprecated Use the {@link #REGIONAL} source.
     */
    @Deprecated
    public static final OrderSource REGIONAL_BID = new OrderSource(3, "REGIONAL_BID", 0);

    /**
     * Ask side of a regional {@link Quote}.
     * It is a <em>synthetic</em> and <em>separate</em> source.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to
     * The subscription on regional {@link Quote} event is observed when this source is subscribed to.
     * @deprecated Use the {@link #REGIONAL} source.
     */
    @Deprecated
    public static final OrderSource REGIONAL_ASK = new OrderSource(4, "REGIONAL_ASK", 0);

    /**
     * Bid side of an aggregate order book (futures depth and NASDAQ Level II).
     * It is a <em>aggregate</em> and <em>separate</em> source.
     * This source cannot be directly published via dxFeed API, but otherwise it is fully operational.
     * @deprecated Use the {@link #AGGREGATE} source.
     */
    @Deprecated
    public static final OrderSource AGGREGATE_BID = new OrderSource(5, "AGGREGATE_BID", 0);

    /**
     * Ask side of an aggregate order book (futures depth and NASDAQ Level II).
     * It is a <em>aggregate</em> and <em>separate</em> source.
     * This source cannot be directly published via dxFeed API, but otherwise it is fully operational.
     * @deprecated Use the {@link #AGGREGATE} source.
     */
    @Deprecated
    public static final OrderSource AGGREGATE_ASK = new OrderSource(6, "AGGREGATE_ASK", 0);

    /**
     * Composite {@link Quote}.
     * It is a <em>synthetic</em> and <em>unitary</em> source, that represents both bid and ask side.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to.
     * The subscription on composite {@link Quote} event is observed when this source is subscribed to.
     * To use this source when subscribing to all sources (e.g., when subscribing to an order without specifying a source),
     * instead of {@link #COMPOSITE_ASK} and {@link #COMPOSITE_BID}, set the system property
     * <var>dxscheme.unitaryOrderSource</var> to {@code true}.
     */
    public static final OrderSource COMPOSITE = new OrderSource(7, "COMPOSITE", 0);

    /**
     * Regional {@link Quote}.
     * It is a <em>synthetic</em> and <em>unitary</em> source, that represents both bid and ask side.
     * It cannot be used with {@link DXFeed#getIndexedEventsPromise(Class, Object, IndexedEventSource) DXFeed.getIndexedEventsPromise}
     * method and it cannot be published directly to.
     * The subscription on regional {@link Quote} event is observed when this source is subscribed to.
     * To use this source when subscribing to all sources (e.g., when subscribing to an order without specifying a source),
     * instead of {@link #REGIONAL_ASK} and {@link #REGIONAL_BID}, set the system property
     * <var>dxscheme.unitaryOrderSource</var> to {@code true}.
     */
    public static final OrderSource REGIONAL = new OrderSource(8, "REGIONAL", 0);

    /**
     * Aggregate order book (futures depth and NASDAQ Level II).
     * It is a <em>aggregate</em> and <em>unitary</em> source, that represents both bid and ask side.
     * This source cannot be directly published via dxFeed API, but otherwise it is fully operational.
     * To use this source when subscribing to all sources (e.g., when subscribing to an order without specifying a source),
     * instead of {@link #AGGREGATE_ASK} and {@link #AGGREGATE_BID}, set the system property
     * <var>dxscheme.unitaryOrderSource</var> to {@code true}.
     */
    public static final OrderSource AGGREGATE = new OrderSource(9, "AGGREGATE", 0);

    /**
     * Default source for publishing custom order books.
     * {@link Order}, {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder}
     * events are {@link #isPublishable(Class) publishable} on this source and the corresponding subscription
     * can be observed via {@link DXPublisher}.
     */
    public static final OrderSource DEFAULT = new OrderSource(0, "DEFAULT",
        PUB_ORDER | PUB_ANALYTIC_ORDER | PUB_OTC_MARKETS_ORDER | PUB_SPREAD_ORDER | FULL_ORDER_BOOK);

    // ======== BEGIN: Custom OrderSource definitions ========

    // ATTENTION: Every time a custom OrderSource constant is added run com.dxfeed.api.codegen.ImplCodeGen
    // and commit updated implementation classes.

    /**
     * NASDAQ Total View.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource NTV = new OrderSource("NTV", PUB_ORDER | FULL_ORDER_BOOK);

    /**
     * NASDAQ Total View. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ntv = new OrderSource("ntv", PUB_ORDER);

    /**
     * NASDAQ Futures Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource NFX = new OrderSource("NFX", PUB_ORDER);

    /**
     * NASDAQ eSpeed.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ESPD = new OrderSource("ESPD", PUB_ORDER);

    /**
     * NASDAQ Fixed Income.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource XNFI = new OrderSource("XNFI", PUB_ORDER);

    /**
     * Intercontinental Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ICE = new OrderSource("ICE", PUB_ORDER);

    /**
     * International Securities Exchange.
     * {@link Order} and {@link SpreadOrder} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ISE = new OrderSource("ISE", PUB_ORDER | PUB_SPREAD_ORDER);

    /**
     * Direct-Edge EDGA Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource DEA = new OrderSource("DEA", PUB_ORDER);

    /**
     * Direct-Edge EDGX Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource DEX = new OrderSource("DEX", PUB_ORDER);

    /**
     * Direct-Edge EDGX Exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */

    public static final OrderSource dex = new OrderSource("dex", PUB_ORDER);

    /**
     * Bats BYX Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource BYX = new OrderSource("BYX", PUB_ORDER);

    /**
     * Bats BZX Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource BZX = new OrderSource("BZX", PUB_ORDER);

    /**
     * Bats BZX Exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource bzx = new OrderSource("bzx", PUB_ORDER);

    /**
     * Bats Europe BXE Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource BATE = new OrderSource("BATE", PUB_ORDER);

    /**
     * Bats Europe CXE Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource CHIX = new OrderSource("CHIX", PUB_ORDER);

    /**
     * Bats Europe DXE Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource CEUX = new OrderSource("CEUX", PUB_ORDER);

    /**
     * Bats Europe TRF.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource BXTR = new OrderSource("BXTR", PUB_ORDER);

    /**
     * Borsa Istanbul Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource IST = new OrderSource("IST", PUB_ORDER);

    /**
     * Borsa Istanbul Exchange. Record for particular top 20 order book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource BI20 = new OrderSource("BI20", PUB_ORDER);

    /**
     * ABE (abe.io) exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ABE = new OrderSource("ABE", PUB_ORDER);

    /**
     * FAIR (FairX) exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource FAIR = new OrderSource("FAIR", PUB_ORDER);

    /**
     * CME Globex.
     * {@link Order} and {@link AnalyticOrder} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource GLBX = new OrderSource("GLBX", PUB_ORDER | PUB_ANALYTIC_ORDER);

    /**
     * CME Globex. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource glbx = new OrderSource("glbx", PUB_ORDER);

    /**
     * Eris Exchange group of companies.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ERIS = new OrderSource("ERIS", PUB_ORDER);

    /**
     * Eurex Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource XEUR = new OrderSource("XEUR", PUB_ORDER);

    /**
     * Eurex Exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource xeur = new OrderSource("xeur", PUB_ORDER);

    /**
     * CBOE Futures Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource CFE = new OrderSource("CFE", PUB_ORDER);

    /**
     * CBOE Options C2 Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource C2OX = new OrderSource("C2OX", PUB_ORDER);

    /**
     * Small Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource SMFE = new OrderSource("SMFE", PUB_ORDER);

    /**
     * Small Exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource smfe = new OrderSource("smfe", PUB_ORDER);

    /**
     * Investors exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource iex = new OrderSource("iex", PUB_ORDER);

    /**
     * Members Exchange.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource MEMX = new OrderSource("MEMX", PUB_ORDER);

    /**
     * Members Exchange. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource memx = new OrderSource("memx", PUB_ORDER);

    /**
     * Blue Ocean Technologies Alternative Trading System.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource OCEA = new OrderSource("OCEA", PUB_ORDER);

    /**
     * Blue Ocean Technologies Alternative Trading System. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ocea = new OrderSource("ocea", PUB_ORDER);

    /**
     * Pink Sheets. Record for price level book.
     * Pink sheets are listings for stocks that trade over-the-counter (OTC).
     * {@link Order} and {@link OtcMarketsOrder} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource pink = new OrderSource("pink", PUB_ORDER | PUB_OTC_MARKETS_ORDER);

    /**
     * NYSE Arca traded securities.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource ARCA = new OrderSource("ARCA", PUB_ORDER);

    /**
     * NYSE Arca traded securities. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource arca = new OrderSource("arca", PUB_ORDER);

    /**
     * Cboe European Derivatives.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource CEDX = new OrderSource("CEDX", PUB_ORDER);

    /**
     * Cboe European Derivatives. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource cedx = new OrderSource("cedx", PUB_ORDER);

    /**
     * IG CFDs Gate.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource IGC = new OrderSource("IGC", PUB_ORDER);

    /**
     * IG CFDs Gate. Record for price level book.
     * {@link Order} events are {@link #isPublishable(Class) publishable} on this
     * source and the corresponding subscription can be observed via {@link DXPublisher}.
     */
    public static final OrderSource igc = new OrderSource("igc", PUB_ORDER);

    // ATTENTION: Every time a custom OrderSource constant is added run com.dxfeed.api.codegen.ImplCodeGen
    // and commit updated implementation classes.

    // ======== END: Custom OrderSource definitions ========

    /**
     * Determines whether specified source identifier refers to special order source.
     * Special order sources are used for wrapping non-order events into order events.
     *
     * @param sourceId the source identifier.
     * @return <code>true</code> if it is a special source identifier
     */
    public static boolean isSpecialSourceId(int sourceId) {
        return sourceId >= 1 && sourceId <= 9;
    }

    /**
     * Returns order source for the specified source identifier.
     * @param sourceId the source identifier.
     * @return order source.
     * @throws IllegalArgumentException if sourceId is negative or zero.
     */
    public static OrderSource valueOf(int sourceId) {
        OrderSource result = SOURCES_BY_ID.getByKey(sourceId);
        if (result != null)
            return result;
        return createAndCacheOrderSource(SOURCES_BY_ID, sourceId, decodeName(sourceId));
    }

    /**
     * Returns order source for the specified source name.
     * The name must be either predefined, or contain at most 4 alphanumeric characters.
     * @param name the name of the source.
     * @return order source.
     * @throws IllegalArgumentException if name is malformed.
     */
    public static OrderSource valueOf(String name) {
        OrderSource result = SOURCES_BY_NAME.getByKey(name);
        if (result != null)
            return result;
        return createAndCacheOrderSource(SOURCES_BY_NAME, composeId(name), name);
    }

    /**
     * Returns a list of publishable order sources for a given event type.
     * These are the sources with {@link #isPublishable(Class) isPublishable}
     * of {@code true}. Events can be directly published with these sources and
     * their subscription can be observed directly via {@link DXPublisher}.
     * Subscription on such sources is observed via instances of {@link IndexedEventSubscriptionSymbol} class.
     *
     * @param eventType <code>{@link Order}.<b>class</b></code>, <code>{@link AnalyticOrder}.<b>class</b></code>,
     *     <code>{@link OtcMarketsOrder}.<b>class</b></code> or <code>{@link SpreadOrder}.<b>class</b></code>.
     * @return a list of publishable order sources.
     * @throws IllegalArgumentException if eventType is not in <code>{@link Order}.<b>class</b></code>,
     *     <code>{@link AnalyticOrder}.<b>class</b></code>, <code>{@link OtcMarketsOrder}.<b>class</b></code>
     *     or <code>{@link SpreadOrder}.<b>class</b></code>.
     */
    public static List<OrderSource> publishable(Class<? extends OrderBase> eventType) {
        return PUBLISHABLE_VIEWS[31 - Integer.numberOfLeadingZeros(getEventTypeMask(eventType))];
    }

    /**
     * Returns a list of publishable order sources that support Full Order Book.
     *
     * @return a list of publishable order sources that support Full Order Book.
     */
    public static List<OrderSource> fullOrderBook() {
        return PUBLISHABLE_VIEWS[31 - Integer.numberOfLeadingZeros(FULL_ORDER_BOOK)];
    }

    // ========================= private instance fields =========================

    private final int pubFlags;
    private final boolean builtin;

    // ========================= private constructors =========================

    private OrderSource(String name, int pubFlags) {
        this(composeId(name), name, pubFlags);
    }

    private OrderSource(int id, String name, int pubFlags) {
        super(id, name);
        this.pubFlags = pubFlags;
        this.builtin = true;

        // Below are sanity and integrity checks for special and builtin pre-defined sources.
        // They also guard against uncoordinated changes of id/name with other methods.
        if (id < 0)
            throw new IllegalArgumentException("id is negative");
        if (id > 0 && id < 0x20 && !isSpecialSourceId(id))
            throw new IllegalArgumentException("id is not marked as special");
        if (id >= 0x20 && (id != composeId(name) || !name.equals(decodeName(id))))
            throw new IllegalArgumentException("id does not match name");

        if (!SOURCES_BY_ID.add(this))
            throw new IllegalArgumentException("duplicate id");
        if (!SOURCES_BY_NAME.add(this))
            throw new IllegalArgumentException("duplicate name");

        // Flag FULL_ORDER_BOOK requires that source must be publishable
        if (isFullOrderBookFlag(pubFlags) && !isPublishableFlag(pubFlags))
            throw new IllegalArgumentException("unpublishable full order book order");

        CACHE_SIZE = Math.max(CACHE_SIZE, SOURCES_BY_ID.size() * 4);

        for (int i = 0; i < FLAGS_SIZE; i++) {
            if ((pubFlags & (1 << i)) != 0)
                PUBLISHABLE_LISTS[i].add(this);
        }
    }

    // For transient objects only (statically unknown source id)
    private OrderSource(int id, String name) {
        super(id, name);
        this.pubFlags = 0;
        this.builtin = false;
    }

    // ========================= public instance methods =========================

    /**
     * Returns {@code true} if the given event type can be directly published with this source.
     * Subscription on such sources can be observed directly via {@link DXPublisher}.
     * Subscription on such sources is observed via instances of {@link IndexedEventSubscriptionSymbol} class.
     *
     * @param eventType <code>{@link Order}.<b>class</b></code>, <code>{@link AnalyticOrder}.<b>class</b></code>,
     *     <code>{@link OtcMarketsOrder}.<b>class</b></code> or <code>{@link SpreadOrder}.<b>class</b></code>.
     * @return {@code true} if {@link Order}, {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder}
     *     events can be directly published with this source.
     * @throws IllegalArgumentException if eventType differs from <code>{@link Order}.<b>class</b></code>,
     *     <code>{@link AnalyticOrder}.<b>class</b></code>, <code>{@link OtcMarketsOrder}.<b>class</b></code>
     *     or <code>{@link SpreadOrder}.<b>class</b></code>.
     */
    public boolean isPublishable(Class<? extends OrderBase> eventType) {
        return (pubFlags & getEventTypeMask(eventType)) != 0;
    }

    /**
     * Returns {@code true} if this source supports Full Order Book.
     *
     * @return {@code true} if this source supports Full Order Book.
     */
    public boolean isFullOrderBook() {
        return isFullOrderBookFlag(pubFlags);
    }

    // ========================= private helper methods =========================

    private static boolean isPublishableFlag(int pubFlags) {
        return (pubFlags & (PUB_ORDER | PUB_ANALYTIC_ORDER | PUB_OTC_MARKETS_ORDER | PUB_SPREAD_ORDER)) != 0;
    }

    private static boolean isFullOrderBookFlag(int pubFlags) {
        return (pubFlags & FULL_ORDER_BOOK) != 0;
    }

    private static void checkChar(char c) {
        if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9')
            return;
        throw new IllegalArgumentException("Source name must contain only alphanumeric characters");
    }

    private static int composeId(String name) {
        int sourceId = 0;
        int n = name.length();
        if (n == 0 || n > 4)
            throw new IllegalArgumentException("Source name must contain from 1 to 4 characters");
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            checkChar(c);
            sourceId = (sourceId << 8) | c;
        }
        return sourceId;
    }

    private static String decodeName(int id) {
        if (id == 0)
            throw new IllegalArgumentException("Source name must contain from 1 to 4 characters");
        char[] name = new char[4];
        int n = 0;
        for (int i = 24; i >= 0; i -= 8) {
            if (id >> i == 0) // skip highest contiguous zeros
                continue;
            char c = (char) ((id >> i) & 0xff);
            checkChar(c);
            name[n++] = c;
        }
        return new String(name, 0, n);
    }

    private static int getEventTypeMask(Class<? extends OrderBase> eventType) {
        if (eventType == Order.class)
            return PUB_ORDER;
        if (eventType == AnalyticOrder.class)
            return PUB_ANALYTIC_ORDER;
        if (eventType == OtcMarketsOrder.class)
            return PUB_OTC_MARKETS_ORDER;
        if (eventType == SpreadOrder.class)
            return PUB_SPREAD_ORDER;
        throw new IllegalArgumentException("Invalid order event type: " + eventType);
    }

    private static OrderSource createAndCacheOrderSource(SynchronizedIndexedSet<?, OrderSource> cache, int sourceId, String name) {
        // The idea is that this method will be called extremely rarely as there are very few order sources and they all will be cached.
        if (cache.size() >= CACHE_SIZE)
            trimCache(cache);
        return cache.putIfAbsentAndGet(new OrderSource(sourceId, name));
    }

    private static void trimCache(SynchronizedIndexedSet<?, OrderSource> cache) {
        // Drop all non-builtin sources. Full concurrency and thread-safety without any book-keeping.
        // Collisions, duplicates and extra work are ignored as not important.
        for (Iterator<OrderSource> it = cache.concurrentIterator(); it.hasNext();) {
            if (!it.next().builtin)
                it.remove();
        }
    }
}
