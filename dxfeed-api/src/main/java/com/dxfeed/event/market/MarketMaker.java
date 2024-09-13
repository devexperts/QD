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

import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.impl.XmlTimeAdapter;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Collection;

/**
 * The Market Maker event captures a snapshot of aggregated top quotes from various market participants,
 * such as market makers, for a specific symbol. The collection of Market Maker events for a symbol
 * provides aggregate data for a given price level or the best bid and offer from selected market makers
 * at any given moment.
 *
 * <p>The Market Maker events offer varying levels of detail based on the data sources.
 * Initially created for Level 2 data, where each market maker's best quote is represented, the Market Maker event
 * aggregates these quotes to present a comprehensive view of the market.
 * In addition to Level 2 data, the Market Maker event can also represent other aggregated order books with
 * limited depth (e.g., top 5 price levels). In these instances, each price level is treated as a distinct
 * "market maker" with an assigned identifier (e.g., 1, 2, 3, 4, 5), enabling a simplified and consistent representation
 * across different data sources.
 *
 * <p>The Market Maker event is particularly valuable for visualizing the combined order book from various
 * market makers or participants, providing a broader view of market liquidity at the top levels.
 * However, for more detailed order book information or full market depth, the {@link Order} event should be utilized.
 *
 * <h3>Properties</h3>
 *
 * {@code MarketMaker} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - underlying symbol of this event;
 * <li>{@link #getSource() source} - source of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this event;
 * <li>{@link #getTime() time} - time of the last bid or ask change;
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of this event;
 * <li>{@link #getMarketMaker() marketMaker} - market maker or other aggregate identifier of this event;
 * <li>{@link #getBidTime() bidTime} - time of the last bid change;
 * <li>{@link #getBidPrice() bidPrice} - bid price;
 * <li>{@link #getBidSize() bidSize} - bid size;
 * <li>{@link #getBidCount() bidCount} - number of individual bid orders in this market maker event;
 * <li>{@link #getAskTime() askTime} - time of the last ask change;
 * <li>{@link #getAskPrice() askPrice} - ask price;
 * <li>{@link #getAskSize() askSize} - ask size;
 * <li>{@link #getAskCount() askCount} - number of individual bid orders in this market maker event.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Certain Market Maker event sources provide a consistent view of the aggregated order book based on identifiers
 * such as "market maker", price level number, or bank identifier. Updates may involve multiple changes
 * to price levels that should be processed simultaneously. The corresponding information is carried in
 * {@link #getEventFlags() eventFlags} property.
 *
 * <p>See <a href="IndexedEvent.html">Event Flags</a> section of {@link IndexedEvent} class documentation for details.
 *
 * <h3>Publishing Market Maker</h3>
 *
 * When publishing a Market Maker event with {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents}
 * method, you can use {@link #setIndex} method for to set the index directly, or use {@link #setExchangeCode}
 * with {@link #setMarketMaker}. The index is a bitwise representation of these two fields.
 *
 * <p>A snapshot has to be published in the <em>descending</em> order of {@link #getIndex() index}, starting with
 * an event with the largest index and marking it with {@link #SNAPSHOT_BEGIN} bit in {@link #getEventFlags() eventFlags},
 * and finishing the snapshot with an event that has zero index.
 * {@link #SNAPSHOT_END} bit in {@link #getEventFlags() eventFlags} is optional during publishing.
 * It will be properly set on receiving end anyway.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code MarketMaker}.
 *
 * <h3 a="bidAskTime">Bid & Ask time precision</h3>
 *
 * {@link #getBidTime() bidTime} & {@link #getAskTime() askTime} fields by default transferred with <em>seconds</em>
 * precision. This behavior may be configured by <var>dxscheme.bat</var> system property that accepts the following values:
 * <ul>
 * <li><b>seconds</b> (default) - transfer the fields with seconds precision
 * <li><b>millis</b> - transfer the fields with milliseconds precision
 * </ul>
 */
@XmlType(propOrder = {
    "eventFlags", "index", "exchangeCode", "marketMaker", "bidTime", "bidPrice", "bidSize", "bidCount", "askTime",
    "askPrice", "askSize", "askCount"
})
public class MarketMaker extends MarketEvent implements IndexedEvent<String> {
    private static final long serialVersionUID = 0;

    // ========================= instance =========================

    /*
     * Index field contains exchange code and MMID.
     * This format is IMPLEMENTATION DETAILS, they are subject to change without notice:
     *   63..48   47..32   31..0
     * +--------+--------+------+
     * |        |Exchange| MMID |
     * +--------+--------+------+
     * Note: when modifying formats track usages of getIndex/setIndex, getExchangeCode/setExchangeCode and
     * getMarketMaker/setMarketMaker methods in market maker to find and modify all code dependent on current formats.
     */

    // EXCHANGE values are char.
    private static final int EXCHANGE_MASK = 0xFFFF;
    private static final int EXCHANGE_SHIFT = 32;

    // MMID values are string in ShortString format.
    private static final long MMID_MASK = 0xFFFFFFFFL;
    private static final int MMID_SHIFT = 0;

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +---------+----+----+----+----+----+----+----+
     * |         | SM |    | SS | SE | SB | RE | TX |
     * +---------+----+----+----+----+----+----+----+
     */

    private int eventFlags;
    private long index;

    private long bidTime;
    private double bidPrice = Double.NaN;
    private double bidSize = Double.NaN;
    private long bidCount;

    private long askTime;
    private double askPrice = Double.NaN;
    private double askSize = Double.NaN;
    private long askCount;

    /**
     * Creates new market maker with default values.
     */
    public MarketMaker() {}

    /**
     * Creates new market maker with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public MarketMaker(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public IndexedEventSource getSource() {
        return IndexedEventSource.DEFAULT;
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
     * Returns unique per-symbol index of this market maker. Index is non-negative.
     * @return unique per-symbol index of this market maker.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this market maker.
     * Note, that this method also changes {@link #getExchangeCode() exchangeCode}
     * and {@link #getMarketMaker() marketMaker}, whose bit values represent the index.
     * @param index unique per-symbol index of this market maker.
     * @throws IllegalArgumentException when index is negative.
     */
    @Override
    public void setIndex(long index) {
        if (index < 0)
            throw new IllegalArgumentException("Negative index: " + index);
        this.index = index;
    }

    /**
     * Returns time of the last bid or ask change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p> This method is the same as
     * <code>Math.max({@link #getBidTime() getBidTime}(), {@link #getAskTime() getAskTime})</code>.
     * Use {@link #setBidTime(long)} and {@link #setAskTime(long)} in market maker event to change this time.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the result of this method is usually a multiple of 1000.</b>
     *
     * @return time of the last bid or ask change.
     */
    @XmlTransient
    public long getTime() {
        return Math.max(bidTime, askTime);
    }

    /**
     * Returns exchange code of this market maker event.
     * @return exchange code of this market maker event.
     */
    public char getExchangeCode() {
        return (char) Util.getBits(index, EXCHANGE_MASK, EXCHANGE_SHIFT);
    }

    /**
     * Changes exchange code of this market maker event.
     * This method modifies the corresponding bits in the {@link #getIndex() index} field of this event.
     * @param exchangeCode exchange code of this market maker event.
     */
    public void setExchangeCode(char exchangeCode) {
        Util.checkChar(exchangeCode, EXCHANGE_MASK, "exchangeCode");
        index = Util.setBits(index, EXCHANGE_MASK, EXCHANGE_SHIFT, exchangeCode);
    }

    /**
     * Returns market maker or other aggregate identifier of this market maker event.
     * @return market maker or other aggregate identifier of this market maker event.
     */
    public String getMarketMaker() {
        return Util.decodeShortString(Util.getBits(index, MMID_MASK, MMID_SHIFT));
    }

    /**
     * Changes market maker or other aggregate identifier of this market maker event.
     * This method modifies the corresponding bits in the {@link #getIndex() index} field of this event.
     * @param marketMaker market maker or other aggregate identifier of this market maker event.
     */
    public void setMarketMaker(String marketMaker) {
        index = Util.setBits(index, MMID_MASK, MMID_SHIFT, Util.encodeShortString(marketMaker));
    }

    /**
     * Returns time of the last bid change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the result of this method is usually a multiple of 1000.</b>
     *
     * @return time of the last bid change.
     */
    @XmlJavaTypeAdapter(type=long.class, value= XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getBidTime() {
        return bidTime;
    }

    /**
     * Changes time of the last bid change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the value of this field for receiver usually a multiple of 1000.</b>
     *
     * @param bidTime time of the last bid change.
     */
    public void setBidTime(long bidTime) {
        this.bidTime = bidTime;
    }

    /**
     * Returns bid price.
     * @return bid price.
     */
    public double getBidPrice() {
        return bidPrice;
    }

    /**
     * Changes bid price.
     * @param bidPrice bid price.
     */
    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }

    /**
     * Returns bid size.
     * @return bid size.
     */
    public double getBidSize() {
        return bidSize;
    }

    /**
     * Changes bid size.
     * @param bidSize bid size.
     */
    public void setBidSize(double bidSize) {
        this.bidSize = bidSize;
    }

    /**
     * Returns number of individual bid orders in this market maker event.
     * @return number of individual bid orders in this market maker event.
     */
    public long getBidCount() {
        return bidCount;
    }

    /**
     * Changes number of individual bid orders in this market maker event.
     * @param bidCount number of individual bid orders in this market maker event.
     */
    public void setBidCount(long bidCount) {
        this.bidCount = bidCount;
    }

    /**
     * Returns time of the last ask change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the result of this method is usually a multiple of 1000.</b>
     *
     * @return time of the last ask change.
     */
    @XmlJavaTypeAdapter(type=long.class, value= XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getAskTime() {
        return askTime;
    }

    /**
     * Changes time of the last ask change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the value of this field for receiver usually a multiple of 1000.</b>
     *
     * @param askTime time of the last ask change.
     */
    public void setAskTime(long askTime) {
        this.askTime = askTime;
    }

    /**
     * Returns ask price.
     * @return ask price.
     */
    public double getAskPrice() {
        return askPrice;
    }

    /**
     * Changes ask price.
     * @param askPrice ask price.
     */
    public void setAskPrice(double askPrice) {
        this.askPrice = askPrice;
    }

    /**
     * Returns ask size.
     * @return ask size.
     */
    public double getAskSize() {
        return askSize;
    }

    /**
     * Changes ask size.
     * @param askSize ask size.
     */
    public void setAskSize(double askSize) {
        this.askSize = askSize;
    }

    /**
     * Returns number of individual ask orders in this market maker event.
     * @return number of individual ask orders in this market maker event.
     */
    public long getAskCount() {
        return askCount;
    }

    /**
     * Changes number of individual ask orders in this market maker event.
     * @param askCount number of individual ask orders in this market maker event.
     */
    public void setAskCount(long askCount) {
        this.askCount = askCount;
    }

    /**
     * Returns string representation of this market maker event.
     * @return string representation of this market maker event.
     */
    @Override
    public String toString() {
        return "MarketMaker{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", source=" + getSource() +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", index=0x" + Long.toHexString(index) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", exchange=" + Util.encodeChar(getExchangeCode()) +
            ", marketMaker='" + getMarketMaker() + "'" +
            ", bidTime=" + TimeFormat.DEFAULT.withMillis().format(bidTime) +
            ", bidPrice=" + bidPrice +
            ", bidSize=" + bidSize +
            ", bidCount=" + bidCount +
            ", askTime=" + TimeFormat.DEFAULT.withMillis().format(askTime) +
            ", askPrice=" + askPrice +
            ", askSize=" + askSize +
            ", askCount=" + askCount +
            '}';
    }
}
