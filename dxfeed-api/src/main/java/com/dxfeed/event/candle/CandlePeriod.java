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
package com.dxfeed.event.candle;

import com.dxfeed.event.market.MarketEventSymbols;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Period attribute of {@link CandleSymbol} defines aggregation period of the candles.
 * Aggregation period is defined as pair of a {@link #getValue()} and {@link #getType() type}.
 *
 * <h3>Implementation details</h3>
 *
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getAttributeStringByKey(String, String) MarketEventSymbols.getAttributeStringByKey},
 * {@link MarketEventSymbols#changeAttributeStringByKey(String, String, String) changeAttributeStringByKey}, and
 * {@link MarketEventSymbols#removeAttributeStringByKey(String, String) removeAttributeStringByKey} methods.
 * The key to use with these methods is available via
 * {@link #ATTRIBUTE_KEY} constant.
 * The value that this key shall be set to is equal to
 * the corresponding {@link #toString() CandlePeriod.toString()}
 */
public class CandlePeriod implements CandleSymbolAttribute<CandlePeriod>, Serializable {

    private static final long serialVersionUID = 0;

    /**
     * Tick aggregation where each candle represents an individual tick.
     */
    public static final CandlePeriod TICK = new CandlePeriod(1, CandleType.TICK);

    /**
     * Day aggregation where each candle represents a day.
     */
    public static final CandlePeriod DAY = new CandlePeriod(1, CandleType.DAY);

    /**
     * Default period is {@link #TICK}.
     */
    public static final CandlePeriod DEFAULT = TICK;

    /**
     * The attribute key that is used to store the value of {@code CandlePeriod} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is an empty string, because this is the
     * main attribute that every {@link CandleSymbol} must have.
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandlePeriod.toString()}
     */
    public static final String ATTRIBUTE_KEY = ""; // empty string as attribute key is allowed!

    private final double value;
    private final CandleType type;

    private String string;

    private CandlePeriod(double value, CandleType type) {
        this.value = value;
        this.type = type;
    }

    /**
     * Returns aggregation period in milliseconds as closely as possible.
     * Certain aggregation types like {@link CandleType#SECOND SECOND} and
     * {@link CandleType#DAY DAY} span a specific number of milliseconds.
     * {@link CandleType#MONTH}, {@link CandleType#OPTEXP} and {@link CandleType#YEAR}
     * are approximate. Candle period of
     * {@link CandleType#TICK}, {@link CandleType#VOLUME}, {@link CandleType#PRICE},
     * {@link CandleType#PRICE_MOMENTUM} and {@link CandleType#PRICE_RENKO}
     * is not defined and this method returns {@code 0}.
     * The result of this method is equal to
     * {@code (long) (this.getType().getPeriodIntervalMillis() * this.getValue())}
     * @see CandleType#getPeriodIntervalMillis()
     * @return aggregation period in milliseconds.
     */
    public long getPeriodIntervalMillis() {
        return (long) (type.getPeriodIntervalMillis() * value);
    }

    /**
     * Returns candle event symbol string with this aggregation period set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this aggregation period set.
     */
    public String changeAttributeForSymbol(String symbol) {
        return this == DEFAULT ?
            MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY) :
            MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, toString());
    }

    /**
     * Internal method that initializes attribute in the candle symbol.
     * @param candleSymbol candle symbol.
     * @throws IllegalStateException if used outside of internal initialization logic.
     */
    public void checkInAttributeImpl(CandleSymbol candleSymbol) {
        if (candleSymbol.period != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.period = this;
    }

    /**
     * Returns aggregation period value. For example, the value of {@code 5} with
     * the candle type of {@link CandleType#MINUTE MINUTE} represents 5 minute
     * aggregation period.
     *
     * @return aggregation period value.
     */
    public double getValue() {
        return value;
    }

    /**
     * Returns aggregation period type.
     * @return aggregation period type.
     */
    public CandleType getType() {
        return type;
    }

    /**
     * Indicates whether this aggregation period is the same as another one.
     * The same aggregation period has the same {@link #getValue() value} and
     * {@link #getType() type}.
     * @return {@code true} if this aggregation period is the same as another one.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CandlePeriod))
            return false;
        CandlePeriod that = (CandlePeriod) o;
        return Double.compare(value, that.value) == 0 && type == that.type;
    }

    /**
     * Returns hash code of this aggregation period.
     * @return hash code of this aggregation period.
     */
    @Override
    public int hashCode() {
        return 31 * Double.hashCode(value) + type.hashCode();
    }

    /**
     * Returns string representation of this aggregation period.
     * The string representation is composed of value and type string.
     * For example, 5 minute aggregation is represented as {@code "5m"}.
     * The value of {@code 1} is omitted in the string representation, so
     * {@link #DAY} (one day) is represented as {@code "d"}.
     * This string representation can be converted back into object
     * with {@link #parse(String)} method.
     * @return string representation of this aggregation period.
     */
    @Override
    public String toString() {
        if (string == null)
            string = value == 1 ? type.toString() : value == (long) value ? (long) value + "" + type : value + "" + type;
        return string;
    }

    /**
     * Parses string representation of aggregation period into object.
     * Any string that was returned by {@link #toString()} can be parsed.
     * This method is flexible in the way candle types can be specified.
     * See {@link CandleType#parse(String)} for details.
     * @param s string representation of aggregation period.
     * @return aggregation period object.
     * @throws IllegalArgumentException if string representation is invalid.
     */
    public static CandlePeriod parse(String s) {
        if (s.equals(CandleType.DAY.toString()))
            return DAY;
        if (s.equals(CandleType.TICK.toString()))
            return TICK;
        int i = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && c != '.' && c != '-' && c != '+' && c != 'e' && c != 'E')
                break;
        }
        String value = s.substring(0, i);
        String type = s.substring(i);
        return valueOf(value.isEmpty() ? 1 : Double.parseDouble(value), CandleType.parse(type));
    }

    /**
     * Returns candle period with the given value and type.
     * @param value candle period value.
     * @param type candle period type.
     * @return candle period with the given value and type.
     */
    public static CandlePeriod valueOf(double value, CandleType type) {
        if (value == 1 && type == CandleType.DAY)
            return DAY;
        if (value == 1 && type == CandleType.TICK)
            return TICK;
        return new CandlePeriod(value, type);
    }

    /**
     * Returns candle period of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have candle period attribute.
     * @param symbol candle symbol string.
     * @return candle period of the given candle symbol string.
     * @throws IllegalArgumentException if string representation is invalid.
     */
    public static CandlePeriod getAttributeForSymbol(String symbol) {
        String string = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return string == null ? DEFAULT : parse(string);
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle period attribute.
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the the candle period attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        try {
            CandlePeriod other = parse(a);
            if (other.equals(DEFAULT))
                MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY);
            if (!a.equals(other.toString()))
                return MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, other.toString());
            return symbol;
        } catch (IllegalArgumentException e) {
            return symbol;
        }
    }

    protected Object readResolve() throws ObjectStreamException {
        if (value == 1 && type == CandleType.DAY)
            return DAY;
        if (value == 1 && type == CandleType.TICK)
            return TICK;
        return this;
    }
}
