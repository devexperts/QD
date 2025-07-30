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
package com.dxfeed.event.candle;

import com.dxfeed.event.market.MarketEventSymbols;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Candle data type attribute of {@link CandleSymbol} defines which types of data shall be used for candles aggregation.
 * This attribute is designed as an open-ended replacement for {@link CandleSession} attribute.
 *
 * <p>For detailed specification and interoperability details, consult Knowledge Base articles for candle aggregation
 * rules.
 *
 * <h3>Implementation details</h3>
 *
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getAttributeStringByKey(String, String) MarketEventSymbols.getAttributeStringByKey},
 * {@link MarketEventSymbols#changeAttributeStringByKey(String, String, String) changeAttributeStringByKey}, and
 * {@link MarketEventSymbols#removeAttributeStringByKey(String, String) removeAttributeStringByKey} methods.
 * The key to use with these methods is available via {@link #ATTRIBUTE_KEY} constant.
 * The value that this key shall be set to is equal to the corresponding {@link #toString() CandleDataType.toString()}.
 */
public final class CandleDataType implements CandleSymbolAttribute<CandleDataType>, Serializable {

    private static final long serialVersionUID = 0;

    /**
     * Aggregation rules are defined per data source (e.g., an exchange).
     */
    public static final CandleDataType SOURCE = new CandleDataType("source");

    /**
     * Selected types of data are identical to those used for {@link CandleSession#REGULAR}.
     */
    public static final CandleDataType THO = new CandleDataType("tho");

    /**
     * All {@link com.dxfeed.event.market.TimeAndSale#isValidTick() valid} TimeAndSales or Quotes are used to build
     * candles.
     */
    public static final CandleDataType ALL = new CandleDataType("all");

    /**
     * All {@link com.dxfeed.event.market.TimeAndSale#isValidTick() valid}
     * {@link com.dxfeed.event.market.TimeAndSale#isExtendedTradingHours() non ETH} TimeAndSales or Quotes from
     * a regular trading hours are used to build candles.
     */
    public static final CandleDataType RTH = new CandleDataType("rth");

    /**
     * All {@link com.dxfeed.event.market.TimeAndSale#isValidTick() valid}
     * {@link com.dxfeed.event.market.TimeAndSale#isExtendedTradingHours() ETH} TimeAndSales or Quotes from
     * an extended trading hours are used to build candles.
     */
    public static final CandleDataType ETH = new CandleDataType("eth");

    /**
     * Data type is selected by a chart server.
     */
    public static final CandleDataType UNDEFINED = new CandleDataType("");

    /**
     * Default data type is {@link #UNDEFINED}.
     */
    public static final CandleDataType DEFAULT = UNDEFINED;

    /**
     * The attribute key that is used to store the value of {@code CandleDataType} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is "dt", which is an abbreviation for "data type".
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandleDataType.toString()}
     */
    public static final String ATTRIBUTE_KEY = "dt";

    private static final CandleDataType[] CANONICAL_DATA_TYPES = {SOURCE, THO, ALL, RTH, ETH, UNDEFINED};

    private final String value;

    private CandleDataType(@Nonnull String value) {
        this.value = Objects.requireNonNull(value);
    }

    /**
     * Returns candle event symbol string with this data type attribute set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this data type attribute set.
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
        if (candleSymbol.dataType != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.dataType = this;
    }

    /**
     * Returns string representation of this data type attribute.
     * Typically, the string representation of candle data type attribute is a lower case string
     * that corresponds to its name. For example, {@link #THO} is represented as "tho".
     * @return string representation of this candle data type attribute.
     */
    @Nonnull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CandleDataType that = (CandleDataType) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Parses string representation of candle session attribute into an object.
     * Any string that was returned by {@link #toString()} can be parsed.
     * @param string string representation of a candle data type attribute.
     * @return candle data type attribute.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandleDataType parse(String string) {
        return valueOf(string);
    }

    /**
     * Parses string representation of candle session attribute into an object.
     * Any string that was returned by {@link #toString()} can be parsed.
     * @param value string representation of a candle data type attribute.
     * @return candle data type attribute.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandleDataType valueOf(String value) {
        if (value == null || value.isEmpty())
            return DEFAULT;
        for (CandleDataType type : CANONICAL_DATA_TYPES) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return new CandleDataType(value);
    }

    /**
     * Returns candle data type attribute of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have the candle data type attribute.
     * @param symbol candle symbol string.
     * @return candle data type attribute of the given candle symbol string.
     */
    public static CandleDataType getAttributeForSymbol(String symbol) {
        String string = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return string == null ? DEFAULT : parse(string);
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle data type attribute.
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the candle data type attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        CandleDataType other = parse(a);
        if (other == DEFAULT)
            return MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (!a.equals(other.toString()))
            return MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, other.toString());
        return symbol;
    }

    private Object readResolve() throws ObjectStreamException {
        if (value == null || value.isEmpty())
            return DEFAULT;
        for (CandleDataType type : CANONICAL_DATA_TYPES) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return this;
    }
}
