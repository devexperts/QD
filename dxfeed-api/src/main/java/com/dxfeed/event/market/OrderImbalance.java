/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import com.devexperts.annotation.Experimental;
import com.devexperts.annotation.Internal;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.impl.EventUtil;
import com.dxfeed.impl.XmlSourceAdapter;
import com.dxfeed.impl.XmlTimeAdapter;

import java.io.Serializable;
import java.util.Objects;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Order imbalance event provides information about order book statistics during auctions.
 *
 * <p>These events describe the imbalance between buy and sell orders for specific security,
 * along with reference pricing information that reflects the current auction state. The event
 * includes metrics such as paired size, imbalance shares, and hypothetical pricing scenarios.
 *
 * <h3>Properties</h3>
 *
 * {@code OrderImbalance} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getSource() source} - source of this event;
 * <li>{@link #getTime() time} - time of the order imbalance event;
 * <li>{@link #getSequence() sequence} - sequence of the order imbalance event;
 * <li>{@link #getRefPrice() refPrice} - reference price for imbalance calculation;
 * <li>{@link #getPairedSize() pairedSize} - total number of shares eligible to be matched at reference price;
 * <li>{@link #getImbalanceSize() imbalanceSize} - number of shares not matched at reference price;
 * <li>{@link #getNearPrice() nearPrice} - hypothetical price for all interest on the book;
 * <li>{@link #getFarPrice() farPrice} - hypothetical price for auction interest only;
 * <li>{@link #getImbalanceSide() imbalanceSide} - side of the order imbalance.
 * <li>{@link #getAuctionType() auctionType} - type of auction.
 * </ul>
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code OrderImbalance#<source-id>},
 * where {@code <source-id>} is up to 4 ASCII characters with a mnemonic for the source like "NUAM".
 */
@XmlRootElement(name = "OrderImbalance")
@XmlType(propOrder = {
    "source", "time", "sequence", "refPrice", "pairedSize", "imbalanceSize", "nearPrice", "farPrice", "imbalanceSide",
    "auctionType"
})
@Experimental
public class OrderImbalance extends MarketEvent implements Serializable {
    private static final long serialVersionUID = 0;

    /**
     * Maximum allowed sequence value.
     *
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..6        5..3            2..0
     * +--------+--------------+----------------+
     * |        | Auction Type | Imbalance Side |
     * +--------+--------------+----------------+
     */

    // IMBALANCE_SIDE values are taken from the ImbalanceSide enum.
    static final int IMBALANCE_SIDE_MASK = 0x07;
    static final int IMBALANCE_SIDE_SHIFT = 0;

    // AUCTION_SIDE values are taken from the AuctionType enum.
    static final int AUCTION_TYPE_MASK = 0x07;
    static final int AUCTION_TYPE_SHIFT = 3;

    // ========================= instance =========================

    private OrderSource source = OrderSource.DEFAULT;
    private long timeSequence;
    private double refPrice = Double.NaN;
    private double pairedSize = Double.NaN;
    private double imbalanceSize = Double.NaN;
    private double nearPrice = Double.NaN;
    private double farPrice = Double.NaN;
    private int flags;

    /**
     * Creates a new order imbalance event with default values.
     */
    public OrderImbalance() {
    }

    /**
     * Creates a new order imbalance event with the specified event symbol.
     *
     * @param eventSymbol event symbol.
     */
    public OrderImbalance(String eventSymbol) {
        setEventSymbol(eventSymbol);
    }

    /**
     * Returns source of this event.
     *
     * @return source of this event.
     */
    @XmlJavaTypeAdapter(type = IndexedEventSource.class, value = XmlSourceAdapter.class)
    @XmlSchemaType(name = "string")
    public OrderSource getSource() {
        return source;
    }

    /**
     * Changes source of this event.
     *
     * @param source source of this event.
     */
    public void setSource(OrderSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Returns time of the order imbalance event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @return time of the order imbalance event.
     */
    @XmlJavaTypeAdapter(type = long.class, value = XmlTimeAdapter.class)
    @XmlSchemaType(name = "dateTime")
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of the order imbalance event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @param time time of the order imbalance event.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) |
            ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns sequence number of this event to distinguish order imbalance events that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     *
     * @return sequence of this order imbalance event.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence() sequence number} of this event.
     *
     * @param sequence the sequence.
     * @throws IllegalArgumentException if the sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns the reference price.
     *
     * <p>The reference price is the price for which the number of paired shares and number of
     * imbalance shares are calculated. Usually, this is the consolidated last sale price before
     * the auction or imbalance event started.
     *
     * @return the reference price.
     */
    public double getRefPrice() {
        return refPrice;
    }

    /**
     * Changes the reference price.
     *
     * @param refPrice the reference price.
     */
    public void setRefPrice(double refPrice) {
        this.refPrice = refPrice;
    }

    /**
     * Returns the paired size.
     *
     * <p>The paired size is the total number of shares that are eligible to be matched at the reference price.
     *
     * @return the paired size.
     */
    public double getPairedSize() {
        return pairedSize;
    }

    /**
     * Changes the paired size.
     *
     * @param pairedSize the paired size.
     */
    public void setPairedSize(double pairedSize) {
        this.pairedSize = pairedSize;
    }

    /**
     * Returns the imbalance size.
     *
     * <p>The imbalance size is the number of shares that are not matched at the reference price.
     *
     * @return the imbalance size.
     */
    public double getImbalanceSize() {
        return imbalanceSize;
    }

    /**
     * Changes the imbalance size.
     *
     * @param imbalanceSize the imbalance size.
     */
    public void setImbalanceSize(double imbalanceSize) {
        this.imbalanceSize = imbalanceSize;
    }

    /**
     * Returns the near price.
     *
     * <p>The near price is a hypothetical price at which all interest (all orders) on the book
     * could trade if the closure happened now, including auction and imbalance offset interest.
     *
     * @return the near price.
     */
    public double getNearPrice() {
        return nearPrice;
    }

    /**
     * Changes the near price.
     *
     * @param nearPrice the near price.
     */
    public void setNearPrice(double nearPrice) {
        this.nearPrice = nearPrice;
    }

    /**
     * Returns the far price.
     *
     * <p>The far price is a hypothetical price at which only auction interest (all auction orders)
     * on the book could trade if the closure happened now. Far price is usually further from the
     * reference price than the near price.
     *
     * @return the far price.
     */
    public double getFarPrice() {
        return farPrice;
    }

    /**
     * Changes the far price.
     *
     * @param farPrice the far price.
     */
    public void setFarPrice(double farPrice) {
        this.farPrice = farPrice;
    }

    /**
     * Returns the imbalance side.
     *
     * @return the imbalance side.
     */
    public ImbalanceSide getImbalanceSide() {
        return ImbalanceSide.valueOf(EventUtil.getBits(flags, IMBALANCE_SIDE_MASK, IMBALANCE_SIDE_SHIFT));
    }

    /**
     * Changes the imbalance side.
     *
     * @param side the imbalance side.
     */
    public void setImbalanceSide(ImbalanceSide side) {
        flags = EventUtil.setBits(flags, IMBALANCE_SIDE_MASK, IMBALANCE_SIDE_SHIFT, side.getCode());
    }

    /**
     * Returns the auction type.
     *
     * @return the auction type.
     */
    public AuctionType getAuctionType() {
        return AuctionType.valueOf(EventUtil.getBits(flags, AUCTION_TYPE_MASK, AUCTION_TYPE_SHIFT));
    }

    /**
     * Changes the auction type.
     *
     * @param type the auction type.
     */
    public void setAuctionType(AuctionType type) {
        flags = EventUtil.setBits(flags, AUCTION_TYPE_MASK, AUCTION_TYPE_SHIFT, type.getCode());
    }

    @Override
    public String toString() {
        return "OrderImbalance{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", source=" + source +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", refPrice=" + refPrice +
            ", pairedSize=" + pairedSize +
            ", imbalanceSize=" + imbalanceSize +
            ", nearPrice=" + nearPrice +
            ", farPrice=" + farPrice +
            ", imbalanceSide=" + getImbalanceSide() +
            ", auctionType=" + getAuctionType() +
            "}";
    }

    // ========================= internal accessors for delegates =========================

    /**
     * Returns time and sequence of the order imbalance packaged into a single long value.
     *
     * @return time and sequence of the order imbalance.
     */
    @XmlTransient
    @Internal
    long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of the order imbalance.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param timeSequence the time and sequence.
     * @see #getTimeSequence()
     */
    @Internal
    void setTimeSequence(long timeSequence) {
        this.timeSequence = timeSequence;
    }

    /**
     * Returns implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @return flags.
     */
    @XmlTransient
    @Internal
    protected int getFlags() {
        return flags;
    }

    /**
     * Changes implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @param flags flags.
     */
    @Internal
    protected void setFlags(int flags) {
        this.flags = flags;
    }
}
