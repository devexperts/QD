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
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.impl.TimeNanosUtil;
import com.dxfeed.impl.XmlTimeAdapter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Quote event is a snapshot of the best bid and ask prices, and other fields that change with each quote.
 * It represents the most recent information that is available about the best quote on the market
 * at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Quote} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds part of time of the last bid or ask change;
 * <li>{@link #getSequence() sequence} - sequence of this quote;
 * <li>{@link #getBidTime() bidTime} - time of the last bid change;
 * <li>{@link #getBidExchangeCode() bidExchangeCode} -  bid exchange code;
 * <li>{@link #getBidPrice() bidPrice} - bid price;
 * <li>{@link #getBidSize() bidSize} - bid size as integer number (rounded toward zero);
 * <li>{@link #getBidSizeAsDouble() bidSizeAsDouble} - bid size as floating number with fractions;
 * <li>{@link #getAskTime() askTime} - time of the last ask change;
 * <li>{@link #getAskExchangeCode() askExchangeCode} -  ask exchange code;
 * <li>{@link #getAskPrice() askPrice} - ask price;
 * <li>{@link #getAskSize() askSize} - ask size as integer number (rounded toward zero);
 * <li>{@link #getAskSizeAsDouble() askSizeAsDouble} - ask size as floating number with fractions.
 * </ul>
 *
 * Bid corresponds to the best (maximal price) order to buy,
 * ask corresponds to the best (minimal price) order to sell.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code Quote} and {@code Quote&X}
 * for regional exchange best quotes.
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
@XmlRootElement(name = "Quote")
@XmlType(propOrder = {
    "sequence", "timeNanoPart", "bidTime", "bidExchangeCode", "bidPrice", "bidSizeAsDouble",
    "askTime", "askExchangeCode", "askPrice", "askSizeAsDouble"
})
public class Quote extends MarketEvent implements LastingEvent<String> {
    private static final long serialVersionUID = 1;

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    private int timeMillisSequence;
    private int timeNanoPart;
    private long bidTime;
    private char bidExchangeCode;
    private double bidPrice = Double.NaN;
    private double bidSize = Double.NaN;
    private long askTime;
    private char askExchangeCode;
    private double askPrice = Double.NaN;
    private double askSize = Double.NaN;

    /**
     * Creates new quote with default values.
     */
    public Quote() {}

    /**
     * Creates new quote with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Quote(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns sequence number of this quote to distinguish quotes that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of this quote.
     */
    public int getSequence() {
        return timeMillisSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number} of this quote.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeMillisSequence = (timeMillisSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns time of the last bid or ask change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p> This method is the same as
     * <code>Math.max({@link #getBidTime() getBidTime}(), {@link #getAskTime() getAskTime}()}</code>.
     * Use {@link #setBidTime(long)} and {@link #setAskTime(long)} in order to change this time.
     * Note, that unlike bid/ask times, that are transmitted over network in a second-precision, this
     * time is transmitted up to a millisecond and even nano-second precision (see {@link #getTimeNanoPart()})
     * if {@link com.dxfeed.api.DXEndpoint#DXSCHEME_NANO_TIME_PROPERTY DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY}
     * is set to {@code true}.
     *
     * @return time of the last bid or ask change.
     */
    @XmlTransient
    public long getTime() {
        return Math.floorDiv(Math.max(bidTime, askTime), 1000) * 1000 + (timeMillisSequence >>> 22);
    }

    /**
     * Returns time of the last bid or ask change in nanoseconds.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @return time of the last bid or ask change in nanoseconds.
     */
    @XmlTransient
    public long getTimeNanos() {
        return TimeNanosUtil.getNanosFromMillisAndNanoPart(getTime(), timeNanoPart);
    }

    /**
     * Changes microseconds and nanoseconds part of time of the last bid or ask change.
     * <p><b>This method changes {@link #getTimeNanos()} result.</b>
     * @param timeNanoPart microseconds and nanoseconds part of time of the last bid or ask change.
     */
    public void setTimeNanoPart(int timeNanoPart) {
        this.timeNanoPart = timeNanoPart;
    }

    /**
     * Returns microseconds and nanoseconds part of time of the last bid or ask change.
     * @return microseconds and nanoseconds part of time of the last bid or ask change.
     */
    public int getTimeNanoPart() {
        return timeNanoPart;
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
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getBidTime() {
        return bidTime;
    }

    /**
     * Changes time of the last bid change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p>You can set the actual millisecond-precision time here to publish an event, and the millisecond part
     * will make the {@link #getTime() time} of this quote even precise up to a millisecond.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the value of this field for receiver usually a multiple of 1000.</b>
     *
     * @param bidTime time of the last bid change.
     */
    public void setBidTime(long bidTime) {
        this.bidTime = bidTime;
        recomputeTimeMillisPart();
    }

    /**
     * Returns bid exchange code.
     * @return bid exchange code.
     */
    public char getBidExchangeCode() {
        return bidExchangeCode;
    }

    /**
     * Changes bid exchange code.
     * @param bidExchangeCode bid exchange code.
     */
    public void setBidExchangeCode(char bidExchangeCode) {
        this.bidExchangeCode = bidExchangeCode;
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
     * Returns bid size as integer number (rounded toward zero).
     * @return bid size as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getBidSize() {
        return (long) bidSize;
    }

    /**
     * Changes bid size as integer number (rounded toward zero).
     * @param bidSize bid size as integer number (rounded toward zero).
     */
    public void setBidSize(long bidSize) {
        this.bidSize = bidSize;
    }

    /**
     * Returns bid size as floating number with fractions.
     * @return bid size as floating number with fractions.
     */
    @XmlElement(name = "bidSize")
    public double getBidSizeAsDouble() {
        return bidSize;
    }

    /**
     * Changes bid size as floating number with fractions.
     * @param bidSize bid size as floating number with fractions.
     */
    public void setBidSizeAsDouble(double bidSize) {
        this.bidSize = bidSize;
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
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getAskTime() {
        return askTime;
    }

    /**
     * Changes time of the last ask change.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * <p>You can set the actual millisecond-precision time here to publish an event, and the millisecond part
     * will make the {@link #getTime() time} of this quote even precise up to a millisecond.
     *
     * <p><b><a href="#bidAskTime">By default</a> this time is transmitted with seconds precision,
     * so the value of this field for receiver usually a multiple of 1000.</b>
     *
     * @param askTime time of the last ask change.
     */
    public void setAskTime(long askTime) {
        this.askTime = askTime;
        recomputeTimeMillisPart();
    }

    /**
     * Returns ask exchange code.
     * @return ask exchange code.
     */
    public char getAskExchangeCode() {
        return askExchangeCode;
    }

    /**
     * Changes ask exchange code.
     * @param askExchangeCode ask exchange code.
     */
    public void setAskExchangeCode(char askExchangeCode) {
        this.askExchangeCode = askExchangeCode;
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
     * Returns ask size as integer number (rounded toward zero).
     * @return ask size as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getAskSize() {
        return (long) askSize;
    }

    /**
     * Changes ask size as integer number (rounded toward zero).
     * @param askSize ask size as integer number (rounded toward zero).
     */
    public void setAskSize(long askSize) {
        this.askSize = askSize;
    }

    /**
     * Returns ask size as floating number with fractions.
     * @return ask size as floating number with fractions.
     */
    @XmlElement(name = "askSize")
    public double getAskSizeAsDouble() {
        return askSize;
    }

    /**
     * Changes ask size as floating number with fractions.
     * @param askSize ask size as floating number with fractions.
     */
    public void setAskSizeAsDouble(double askSize) {
        this.askSize = askSize;
    }

    /**
     * Returns string representation of this quote event.
     * @return string representation of this quote event.
     */
    @Override
    public String toString() {
        return "Quote{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", timeNanoPart=" + timeNanoPart +
            ", sequence=" + getSequence() +
            ", bidTime=" + TimeFormat.DEFAULT.withMillis().format(bidTime) +
            ", bidExchange=" + Util.encodeChar(bidExchangeCode) +
            ", bidPrice=" + bidPrice +
            ", bidSize=" + bidSize +
            ", askTime=" + TimeFormat.DEFAULT.withMillis().format(askTime) +
            ", askExchange=" + Util.encodeChar(askExchangeCode) +
            ", askPrice=" + askPrice +
            ", askSize=" + askSize +
            '}';
    }

    // ========================= package private access for delegate =========================

    int getTimeMillisSequence() {
        return timeMillisSequence;
    }

    void setTimeMillisSequence(int timeMillisSequence) {
        this.timeMillisSequence = timeMillisSequence;
    }

    // =================================== internal methods ==================================

    private void recomputeTimeMillisPart() {
        timeMillisSequence = TimeUtil.getMillisFromTime(Math.max(askTime, bidTime)) << 22 | getSequence();
    }
}
