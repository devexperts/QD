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
package com.dxfeed.event.market;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.impl.TimeNanosUtil;
import com.dxfeed.impl.XmlSourceAdapter;
import com.dxfeed.impl.XmlTimeAdapter;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.market.OrderBookModel;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Base class for common fields of {@link Order}, {@link AnalyticOrder}, {@link OtcMarketsOrder}
 * and {@link SpreadOrder} events.
 * Order events represent a snapshot for a full available market depth for a symbol.
 * The collection of order events of a symbol represents the most recent information that is
 * available about orders on the market at any given moment of time.
 *
 * <p>{@link Order} event represents market depth for a <b>specific symbol</b>.
 *
 * <p>{@link AnalyticOrder} event represents market depth for a <b>specific symbol</b> extended with an analytic
 * information, for example, whether particular order represent an iceberg or not.
 *
 * <p>{@link OtcMarketsOrder} event represents market depth for a <b>specific symbol</b> traded on the OTC Markets.
 * The event is extended with the additional OTC Markets quote data, such as Quote Access Payment (QAP) value,
 * Quote Price Type, Open/Closed status, and Unsolicited state indication, when applicable.
 *
 * <p>{@link SpreadOrder} event represents market depth for
 *    <b>all spreads on a given underlying symbol</b>.
 *
 * <p> Order events arrive from
 * multiple sources for the same market symbol and are distinguished by their
 * {@link #getIndex index}. Index is a unique per symbol identifier of the event.
 * It is unique across all the sources of depth information for the symbol.
 * The event with {@link #getSizeAsDouble() sizeAsDouble} either {@code 0} or {@link Double#NaN NaN}
 * is a signal to remove previously received order for the corresponding index.
 * The method {@link #hasSize() hasSize} is a convenient method to test for size presence.
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some order event sources provide a consistent view of the price-level or detailed order book. Their updates
 * may incorporate multiple changes to price levels or to individual orders that have to be processed at the same time.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 *
 * <p> The event {@link #getSource() source} identifier for an order is a part of the unique event {@link #getIndex() index}.
 * It occupies highest bits of the {@link #getIndex() index} (index is not-negative).
 * The lowest bits of
 * {@link #getIndex() index} contain source-specific event index which is always zero in
 * an event that is marked with {@link #SNAPSHOT_END} bit in {@link #getEventFlags() eventFlags}.
 *
 * <p> Note that for an order with {@link #REMOVE_EVENT} bit in {@link #getEventFlags() eventFlags}
 * it is always the case that {@link #getSizeAsDouble() sizeAsDouble} is either {@code 0} or {@link Double#NaN NaN},
 * so no additional logic to process this bit is required for orders.
 * Transactions and snapshots may include orders with {@link #getSizeAsDouble() sizeAsDouble} of {@code 0}.
 * The filtering that distinguishes those events as removals of orders shall be performed after
 * all transactions and snapshot processing.
 *
 * <p> Some aggregated feeds (like CME market depth) are mapped into two distinct source ids (one for
 * buy orders and one for sell orders), but updates and transactions may span both. It is important to keep a
 * separate track of transactional state for each source id, but, at the same time, when
 * {@link DXFeedEventListener#eventsReceived(List) DXFeedEventListener.eventsReceived} method is called for a list
 * of events, the order book shall be considered complete and consistent only when all events from the given
 * list are processed.
 *
 * <p> {@link OrderBookModel} class contains all the appropriate logic to deal with transactions and snapshots
 * for {@link Order} events. The client-visible changes to the model are reported only when the snapshot for the
 * specific source id is received completely and when there is no ongoing transaction for the specific source id.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 */
@XmlType(propOrder = {
    "eventFlags", "index", "time", "timeNanoPart", "sequence", "source", "action", "actionTime",
    "orderId", "auxOrderId", "price", "sizeAsDouble", "executedSize", "count", "exchangeCode", "orderSide", "scope",
    "tradeId", "tradePrice", "tradeSize"
})
public class OrderBase extends MarketEvent implements IndexedEvent<String> {
    private static final long serialVersionUID = 3;

    // ========================= public static =========================

    /**
     * Order to buy (bid).
     * @deprecated use {@link Side#BUY} instead.
     */
    @Deprecated
    public static final int SIDE_BUY = 0;

    /**
     * Order to sell (ask or offer).
     * @deprecated use {@link Side#SELL} instead.
     */
    @Deprecated
    public static final int SIDE_SELL = 1;


    /**
     * Represents best bid and offer on the whole market.
     * @deprecated use {@link Scope#COMPOSITE} instead.
     */
    @Deprecated
    public static final int LEVEL_COMPOSITE = 0;

    /**
     * Represents best bid and offer for a given exchange code.
     * @deprecated use {@link Scope#REGIONAL} instead.
     */
    @Deprecated
    public static final int LEVEL_REGIONAL = 1;

    /**
     * Represents aggregate information for each price level or
     * best bid and offer for each market maker.
     * @deprecated use {@link Scope#AGGREGATE} instead.
     */
    @Deprecated
    public static final int LEVEL_AGGREGATE = 2;

    /**
     * Represents individual order at the market.
     * @deprecated use {@link Scope#ORDER} instead.
     */
    @Deprecated
    public static final int LEVEL_ORDER = 3;


    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..15   14..11    10..4    3    2    1    0
     * +--------+--------+--------+----+----+----+----+
     * |        | Action |Exchange|  Side   |  Scope  |
     * +--------+--------+--------+----+----+----+----+
     */

    // ACTION values are taken from OrderAction enum.
    static final int ACTION_MASK = 0x0f;
    static final int ACTION_SHIFT = 11;

    // EXCHANGE values are ASCII chars in [0, 127].
    static final int EXCHANGE_MASK = 0x7f;
    static final int EXCHANGE_SHIFT = 4;

    // SIDE values are taken from Side enum.
    static final int SIDE_MASK = 3;
    static final int SIDE_SHIFT = 2;

    // SCOPE values are taken from Scope enum.
    static final int SCOPE_MASK = 3;
    static final int SCOPE_SHIFT = 0;

    // ========================= instance =========================

    /*
     * Index field contains source identifier, optional exchange code and low-end index (virtual id or MMID).
     * Index field has 2 formats depending on whether source is "special" (see OrderSource.isSpecialSourceId()).
     * Note: both formats are IMPLEMENTATION DETAILS, they are subject to change without notice.
     *   63..48   47..32   31..16   15..0
     * +--------+--------+--------+--------+
     * | Source |Exchange|      Index      |  <- "special" order sources (non-printable id with exchange)
     * +--------+--------+--------+--------+
     *   63..48   47..32   31..16   15..0
     * +--------+--------+--------+--------+
     * |     Source      |      Index      |  <- generic order sources (alphanumeric id without exchange)
     * +--------+--------+--------+--------+
     * Note: when modifying formats track usages of getIndex/setIndex, getSource/setSource and isSpecialSourceId
     * methods in order to find and modify all code dependent on current formats.
     */

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +---------+----+----+----+----+----+----+----+
     * |         | SM |    | SS | SE | SB | RE | TX |
     * +---------+----+----+----+----+----+----+----+
     */

    private int eventFlags;

    private long index;
    private long timeSequence;
    private int timeNanoPart;

    private long actionTime;
    private long orderId;
    private long auxOrderId;

    private double price = Double.NaN;
    private double size = Double.NaN;
    private double executedSize = Double.NaN;
    private long count;
    private int flags;

    private long tradeId;
    private double tradePrice = Double.NaN;
    private double tradeSize = Double.NaN;

    /**
     * Creates new order with default values.
     */
    OrderBase() {}

    /**
     * Creates new order with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    OrderBase(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns source of this event.
     * The source is stored in the highest bits of the {@link #getIndex() index} of this event.
     *
     * @return source of this event.
     */
    @Override
    @XmlJavaTypeAdapter(type=IndexedEventSource.class, value=XmlSourceAdapter.class)
    @XmlSchemaType(name="string")
    public OrderSource getSource() {
        int sourceId = (int) (index >> 48);
        if (!OrderSource.isSpecialSourceId(sourceId))
            sourceId = (int) (index >> 32);
        return OrderSource.valueOf(sourceId);
    }

    /**
     * Changes source of this event.
     * This method changes highest bits of the {@link #getIndex() index} of this event.
     *
     * @param source source of this event.
     */
    public void setSource(OrderSource source) {
        int shift = OrderSource.isSpecialSourceId(source.id()) ? 48 : 32;
        long mask = OrderSource.isSpecialSourceId((int) (index >> 48)) ? ~(-1L << 48) : ~(-1L << 32);
        index = ((long) source.id() << shift) | (index & mask);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventFlags() {
        return eventFlags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventFlags(int eventFlags) {
        this.eventFlags = eventFlags;
    }

    /**
     * Returns unique per-symbol index of this order. Index is non-negative.
     *
     * @return unique per-symbol index of this order.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this order. Note, that this method also changes
     * {@link #getSource() source}, whose id occupies highest bits of index.
     * Use {@link #setSource(OrderSource) setSource} after invocation of this method to set the
     * desired value of source.
     *
     * @param index unique per-symbol index of this order.
     * @throws IllegalArgumentException when index is negative.
     */
    @Override
    public void setIndex(long index) {
        if (index < 0)
            throw new IllegalArgumentException("Negative index: " + index);
        this.index = index;
    }

    /**
     * Returns time and sequence of this order packaged into single long value.
     * This method is intended for efficient order time priority comparison.
     * @return time and sequence of this order.
     */
    @XmlTransient
    public long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of this order.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param timeSequence the time and sequence.
     * @see #getTimeSequence()
     */
    public void setTimeSequence(long timeSequence) {
        this.timeSequence = timeSequence;
    }

    /**
     * Returns time of this order.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of this order.
     */
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of this order.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time time of this order.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Changes microseconds and nanoseconds time part of this order.
     * @param timeNanoPart microseconds and nanoseconds time part of this order.
     */
    public void setTimeNanoPart(int timeNanoPart) {
        this.timeNanoPart = timeNanoPart;
    }

    /**
     * Returns microseconds and nanoseconds time part of this order.
     * @return microseconds and nanoseconds time part of this order.
     */
    public int getTimeNanoPart() {
        return timeNanoPart;
    }

    /**
     * Returns sequence number of this order to distinguish orders that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of this order.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number} of this order.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns time of this order in nanoseconds.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of this order in nanoseconds..
     */
    @XmlTransient
    public long getTimeNanos() {
        return TimeNanosUtil.getNanosFromMillisAndNanoPart(getTime(), timeNanoPart);
    }

    /**
     * Changes time of this order.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @param timeNanos time of this order in nanoseconds.
     */
    public void setTimeNanos(long timeNanos) {
        setTime(TimeNanosUtil.getMillisFromNanos(timeNanos));
        timeNanoPart = TimeNanosUtil.getNanoPartFromNanos(timeNanos);
    }

    /**
     * Returns order action if available, otherwise - {@link OrderAction#UNDEFINED}.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return order action or {@link OrderAction#UNDEFINED}.
     */
    @XmlElement
    public OrderAction getAction() {
        return OrderAction.valueOf(Util.getBits(flags, ACTION_MASK, ACTION_SHIFT));
    }

    /**
     * Changes action of this order.
     * @param action side of this order.
     */
    public void setAction(OrderAction action) {
        flags = Util.setBits(flags, ACTION_MASK, ACTION_SHIFT, action.getCode());
    }

    /**
     * Returns time of the last {@link #getAction() action}.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return time of the last order action.
     */
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    public long getActionTime() {
        return actionTime;
    }

    /**
     * Changes time of the last action
     * @param actionTime last order action time.
     */
    public void setActionTime(long actionTime) {
        this.actionTime = actionTime;
    }

    /**
     * Returns order ID if available. Some actions ({@link OrderAction#TRADE},
     * {@link OrderAction#BUST}) have no order ID since they are not related to any order in Order book.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return order ID or 0 if not available.
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Changes order ID.
     * @param orderId order ID.
     */
    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    /**
     * Returns auxiliary order ID if available:
     * <ul>
     * <li>in {@link OrderAction#NEW} - ID of the order replaced by this new order</li>
     * <li>in {@link OrderAction#DELETE} - ID of the order that replaces this deleted order</li>
     * <li>in {@link OrderAction#PARTIAL} - ID of the aggressor order</li>
     * <li>in {@link OrderAction#EXECUTE} - ID of the aggressor order</li>
     * </ul>
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return auxiliary order ID or 0 if not available.
     */
    public long getAuxOrderId() {
        return auxOrderId;
    }

    /**
     * Changes auxiliary order ID.
     * @param auxOrderId auxiliary order ID.
     */
    public void setAuxOrderId(long auxOrderId) {
        this.auxOrderId = auxOrderId;
    }

    /**
     * Returns price of this order.
     * @return price of this order.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes price of this order.
     * @param price price of this order.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns size of this order as integer number (rounded toward zero).
     * @return size of this order as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getSize() {
        return (long) size;
    }

    /**
     * Changes size of this order as integer number (rounded toward zero).
     * @param size size of this order as integer number (rounded toward zero).
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Returns size of this order as floating number with fractions.
     * @return size of this order as floating number with fractions.
     */
    @XmlElement(name = "size")
    public double getSizeAsDouble() {
        return size;
    }

    /**
     * Changes size of this order as floating number with fractions.
     * @param size size of this order as floating number with fractions.
     */
    public void setSizeAsDouble(double size) {
        this.size = size;
    }

    /**
     * Returns {@code true} if this order has some size (sizeAsDouble is neither {@code 0} nor {@link Double#NaN NaN}).
     * @return {@code true} if this order has some size (sizeAsDouble is neither {@code 0} nor {@link Double#NaN NaN}).
     */
    public boolean hasSize() {
        return size != 0 && !Double.isNaN(size);
    }

    /**
     * Returns executed size of this order.
     *
     * @return executed size of this order.
     */
    public double getExecutedSize() {
        return executedSize;
    }

    /**
     * Changes executed size of this order.
     *
     * @param executedSize executed size of this order.
     */
    public void setExecutedSize(double executedSize) {
        this.executedSize = executedSize;
    }

    /**
     * Returns number of individual orders in this aggregate order.
     * @return number of individual orders in this aggregate order.
     */
    public long getCount() {
        return count;
    }

    /**
     * Changes number of individual orders in this aggregate order.
     * @param count number of individual orders in this aggregate order.
     */
    public void setCount(long count) {
        this.count = count;
    }

    /**
     * Returns trade (order execution) ID for events containing trade-related action.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return trade ID or 0 if not available.
     */
    public long getTradeId() {
        return tradeId;
    }

    /**
     * Changes trade ID.
     * @param tradeId trade ID.
     */
    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }

    /**
     * Returns trade price for events containing trade-related action.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return trade price of this action.
     */
    public double getTradePrice() {
        return tradePrice;
    }

    /**
     * Changes trade price.
     * @param tradePrice trade price.
     */
    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    /**
     * Returns trade size for events containing trade-related action.
     * <p>This field is a part of the <a href="Order.html#fobSection">FOB</a> support.
     * @return trade size.
     */
    public double getTradeSize() {
        return tradeSize;
    }

    /**
     * Changes trade size.
     * @param tradeSize trade size.
     */
    public void setTradeSize(double tradeSize) {
        this.tradeSize = tradeSize;
    }

    /**
     * Returns exchange code of this order.
     * @return exchange code of this order.
     */
    public char getExchangeCode() {
        return (char) Util.getBits(flags, EXCHANGE_MASK, EXCHANGE_SHIFT);
    }

    /**
     * Changes exchange code of this order.
     * @param exchangeCode exchange code of this order.
     * @throws IllegalArgumentException if exchange code is greater than 127
     */
    public void setExchangeCode(char exchangeCode) {
        Util.checkChar(exchangeCode, EXCHANGE_MASK, "exchangeCode");
        flags = Util.setBits(flags, EXCHANGE_MASK, EXCHANGE_SHIFT, exchangeCode);
    }

    /**
     * Returns side of this order.
     * @return side of this order.
     */
    @XmlElement(name="side")
    public Side getOrderSide() {
        return Side.valueOf(Util.getBits(flags, SIDE_MASK, SIDE_SHIFT));
    }

    /**
     * Changes side of this order.
     * @param side side of this order.
     */
    public void setOrderSide(Side side) {
        flags = Util.setBits(flags, SIDE_MASK, SIDE_SHIFT, side.getCode());
    }

    /**
     * Returns scope of this order.
     * @return scope of this order.
     */
    public Scope getScope() {
        return Scope.valueOf(Util.getBits(flags, SCOPE_MASK, SCOPE_SHIFT));
    }

    /**
     * Changes scope of this order.
     * @param scope scope of this order.
     */
    public void setScope(Scope scope) {
        flags = Util.setBits(flags, SCOPE_MASK, SCOPE_SHIFT, scope.getCode());
    }

    /**
     * Returns side of this order.
     * @return side of this order.
     * @deprecated use {@link #getOrderSide} instead
     */
    @Deprecated
    @XmlTransient
    public int getSide() {
        return getOrderSide() == Side.SELL ? SIDE_SELL : SIDE_BUY;
    }

    /**
     * Changes side of this order.
     * @deprecated use {@link #setOrderSide} instead
     */
    @Deprecated
    public void setSide(int side) {
        if (side == SIDE_BUY)
            setOrderSide(Side.BUY);
        else if (side == SIDE_SELL)
            setOrderSide(Side.SELL);
        else
            throw new IllegalArgumentException("Invalid side: " + side);
    }

    /**
     * Returns detail level of this order.
     * @return detail level of this order.
     * @deprecated use {@link #getScope} instead
     */
    @Deprecated
    @XmlTransient
    public int getLevel() {
        return getScope().getCode();
    }

    /**
     * Changes detail level of this order.
     * @param level detail level of this order.
     * @deprecated use {@link #setScope} instead
     */
    @Deprecated
    public void setLevel(int level) {
        setScope(Scope.valueOf(level));
    }

    /**
     * Returns string representation of this order.
     * @return string representation of this order.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('{');
        fieldsToString(sb);
        sb.append('}');
        return sb.toString();
    }

    // ========================= package private access for other subclasses =========================

    /**
     * Appends the field values of this event to a provided {@link StringBuilder}.
     * This method is responsible for converting each field to a string representation and appending it
     * to the {@link StringBuilder} passed as a parameter.
     * <p> Inheritor classes are encouraged to override this method to add additional fields specific
     * to their implementations. When overriding, the subclass should first call
     * {@code super.fieldsToString(sb)} before appending its own fields to maintain the
     * structure of the data representation.
     * @param sb instance to which field values are appended.
     * @return instance passed in, after field values have been appended.
     */
    StringBuilder fieldsToString(StringBuilder sb) {
        return sb.append(getEventSymbol())
            .append(", eventTime=").append(TimeFormat.DEFAULT.withMillis().format(getEventTime()))
            .append(", source=").append(getSource())
            .append(", eventFlags=0x").append(Integer.toHexString(getEventFlags()))
            .append(", index=0x").append(Long.toHexString(index))
            .append(", time=").append(TimeFormat.DEFAULT.withMillis().format(getTime()))
            .append(", sequence=").append(getSequence())
            .append(", timeNanoPart=").append(timeNanoPart)
            .append(", action=").append(getAction())
            .append(", actionTime=").append(TimeFormat.DEFAULT.withMillis().format(actionTime))
            .append(", orderId=").append(orderId)
            .append(", auxOrderId=").append(auxOrderId)
            .append(", price=").append(price)
            .append(", size=").append(size)
            .append(", executedSize=").append(executedSize)
            .append(", count=").append(count)
            .append(", exchange=").append(Util.encodeChar(getExchangeCode()))
            .append(", side=").append(getOrderSide())
            .append(", scope=").append(getScope())
            .append(", tradeId=").append(tradeId)
            .append(", tradePrice=").append(tradePrice)
            .append(", tradeSize=").append(tradeSize);
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @return flags.
     */
    int getFlags() {
        return flags;
    }

    /**
     * Changes implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @param flags flags.
     */
    void setFlags(int flags) {
        this.flags = flags;
    }
}
