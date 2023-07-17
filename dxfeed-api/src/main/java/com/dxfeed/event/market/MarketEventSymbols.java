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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Helper class to compose and parse symbols for market events.
 *
 * <h3>Regional symbols</h3>
 *
 * Regional symbol subscription receives events only from a designated exchange, marketplace, or venue
 * instead of receiving composite events from all venues (by default). Regional symbol is composed from a
 * <i>base symbol</i>, ampersand character ('&amp;'), and an exchange code character. For example,
 * <ul>
 * <li>"SPY" is the symbol for composite events for SPDR S&amp;P 500 ETF from all exchanges,
 * <li>"SPY&amp;N" is the symbol for event for SPDR S&amp;P 500 ETF that originate only from NYSE marketplace.
 * </ul>
 *
 * <h3>Symbol attributes</h3>
 *
 * Market event symbols can have a number of attributes attached to then in curly braces
 * with {@code <key>=<value>} paris separated by commas. For example,
 * <ul>
 * <li>"SPY{price=bid}" is the market symbol "SPY" with an attribute key "price" set to value "bid".
 * <li>"SPY(=5m,tho=true}" is the market symbol "SPY" with two attributes. One has an empty key and
 * value "5m", while the other has key "tho" and value "true".
 * </ul>
 * The methods in this class always maintain attribute keys in alphabetic order.
 */
public class MarketEventSymbols {
    /**
     * All exchange codes for regional events must match the following pattern
     */
    public static final Pattern EXCHANGES_PATTERN = Pattern.compile("[0-9A-Za-z]");
    /**
     * All exchange codes matching the exchange code pattern
     */
    public static final String SUPPORTED_EXCHANGES = IntStream.rangeClosed(0, 127)
        .mapToObj(c -> String.valueOf((char) c))
        .filter(s -> EXCHANGES_PATTERN.matcher(s).matches())
        .collect(Collectors.joining());
    /**
     * Default exchange codes (if no overrides are specified)
     */
    public static final String DEFAULT_EXCHANGES =
        IntStream.rangeClosed('A', 'Z').mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining());

    private static final char EXCHANGE_SEPARATOR = '&';
    private static final char ATTRIBUTES_OPEN = '{';
    private static final char ATTRIBUTES_CLOSE = '}';
    private static final char ATTRIBUTES_SEPARATOR = ',';
    private static final char ATTRIBUTE_VALUE = '=';

    private MarketEventSymbols() {}

    /**
     * Returns {@code true} is the specified symbol has the exchange code specification.
     * The result is {@code false} if symbol is {@code null}.
     * @param symbol symbol.
     * @return {@code true} is the specified symbol has the exchange code specification.
     */
    public static boolean hasExchangeCode(String symbol) {
        return symbol != null && hasExchangeCodeInternal(symbol, getLengthWithoutAttributesInternal(symbol));
    }

    /**
     * Returns exchange code of the specified symbol or {@code '\0'} if none is defined.
     * The result is {@code '\0'} if symbol is {@code null}.
     * @param symbol symbol.
     * @return exchange code of the specified symbol or {@code '\0'} if none is defined.
     */
    public static char getExchangeCode(String symbol) {
        return hasExchangeCode(symbol) ? symbol.charAt(getLengthWithoutAttributesInternal(symbol) - 1) : 0;
    }

    /**
     * Changes exchange code of the specified symbol or removes it
     * if new exchange code is {@code '\0'}.
     * The result is {@code null} if old symbol is {@code null}.
     * @param symbol old symbol.
     * @param exchangeCode new exchange code.
     * @return new symbol with the changed exchange code.
     */
    public static String changeExchangeCode(String symbol, char exchangeCode) {
        if (symbol == null)
            return exchangeCode == 0 ? null : "" + EXCHANGE_SEPARATOR + exchangeCode;
        int i = getLengthWithoutAttributesInternal(symbol);
        String result = exchangeCode == 0 ?
            getBaseSymbolInternal(symbol, i) :
            getBaseSymbolInternal(symbol, i) + EXCHANGE_SEPARATOR + exchangeCode;
        return i == symbol.length() ? result : result + symbol.substring(i);
    }

    /**
     * Returns base symbol without exchange code and attributes.
     * The result is {@code null} if symbol is {@code null}.
     * @param symbol symbol.
     * @return base symbol without exchange code and attributes.
     */
    public static String getBaseSymbol(String symbol) {
        if (symbol == null)
            return null;
        return getBaseSymbolInternal(symbol, getLengthWithoutAttributesInternal(symbol));
    }

    /**
     * Changes base symbol while leaving exchange code and attributes intact.
     * The result is {@code null} if old symbol is {@code null}.
     * @param symbol old symbol.
     * @param baseSymbol new base symbol.
     * @return new symbol with new base symbol and old symbol's exchange code and attributes.
     */
    public static String changeBaseSymbol(String symbol, String baseSymbol) {
        if (symbol == null)
            return baseSymbol;
        int i = getLengthWithoutAttributesInternal(symbol);
        return hasExchangeCodeInternal(symbol, i) ?
            baseSymbol + EXCHANGE_SEPARATOR + symbol.charAt(i - 1) + symbol.substring(i) :
            i == symbol.length() ? baseSymbol : baseSymbol + symbol.substring(i);
    }

    /**
     * Returns true if the specified symbol has any attributes.
     */
    public static boolean hasAttributes(String symbol) {
        return symbol != null && getLengthWithoutAttributesInternal(symbol) < symbol.length();
    }

    /**
     * Returns value of the attribute with the specified key.
     * The result is {@code null} if attribute with the specified key is not found.
     * The result is {@code null} if symbol is {@code null}.
     * @param symbol symbol.
     * @param key attribute key.
     * @return value of the attribute with the specified key.
     * @throws NullPointerException if key is {@code null}.
     */
    public static String getAttributeStringByKey(String symbol, String key) {
        if (key == null)
            throw new NullPointerException();
        if (symbol == null)
            return null;
        return getAttributeInternal(symbol, getLengthWithoutAttributesInternal(symbol), key);
    }

    /**
     * Changes value of one attribute value while leaving exchange code and other attributes intact.
     * The {@code null} symbol is interpreted as empty one by this method..
     * @param symbol old symbol.
     * @param key attribute key.
     * @param value attribute value.
     * @return new symbol with key attribute with the specified value and everything else from the old symbol.
     * @throws NullPointerException if key is {@code null}.
     */
    public static String changeAttributeStringByKey(String symbol, String key, String value) {
        if (key == null)
            throw new NullPointerException();
        if (symbol == null)
            return value == null ? null : ATTRIBUTES_OPEN + key + ATTRIBUTE_VALUE + value + ATTRIBUTES_CLOSE;
        int i = getLengthWithoutAttributesInternal(symbol);
        if (i == symbol.length())
            return value == null ? symbol : symbol + ATTRIBUTES_OPEN + key + ATTRIBUTE_VALUE + value + ATTRIBUTES_CLOSE;
        return value == null ? removeAttributeInternal(symbol, i, key) : addAttributeInternal(symbol, i, key, value);
    }

    /**
     * Removes one attribute with the specified key while leaving exchange code and other attributes intact.
     * The result is {@code null} if symbol is {@code null}.
     * @param symbol old symbol.
     * @param key attribute key.
     * @return new symbol without the specified key and everything else from the old symbol.
     * @throws NullPointerException if key is {@code null}.
     */
    public static String removeAttributeStringByKey(String symbol, String key) {
        if (key == null)
            throw new NullPointerException();
        if (symbol == null)
            return null;
        return removeAttributeInternal(symbol, getLengthWithoutAttributesInternal(symbol), key);
    }

    /**
     * Builds canonical spread symbol for specified spread components.
     * The canonical representation uses special prefix, leg ordering and formatting.
     * <p>
     * Spread legs with zero ratio are ignored (not included in the spread symbol).
     * If spread is empty then empty string (with zero length) is returned.
     * If spread consists of one leg with ratio of 1 then pure leg symbol is returned.
     *
     * @param spread maps spread leg symbol to its ratio in the spread
     * @return canonical spread symbol for specified spread components.
     */
    public static String buildSpreadSymbol(Map<String, ? extends Number> spread) {
        List<SpreadLeg> legs = new ArrayList<>();
        for (Map.Entry<String, ? extends Number> e : spread.entrySet())
            if (e.getValue().doubleValue() != 0)
                legs.add(new SpreadLeg(e.getKey(), e.getValue().doubleValue()));
        if (legs.isEmpty())
            return "";
        if (legs.size() == 1 && legs.get(0).ratio == 1)
            return legs.get(0).symbol;
        Collections.sort(legs);
        StringBuilder sb = new StringBuilder("=");
        for (SpreadLeg leg : legs) {
            double ratio = leg.ratio;
            if (ratio < 0)
                sb.append('-');
            else if (sb.length() > 1)
                sb.append('+'); // use plus only when not first
            ratio = Math.abs(ratio);
            if (ratio != 1) {
                if (ratio == (int) ratio)
                    sb.append((int) ratio);
                else
                    sb.append(ratio);
                sb.append('*');
            }
            sb.append(leg.symbol);
        }
        return sb.toString();
    }

    /**
     * Returns string with all exchange codes matching the specified character class. For greater flexibility, the
     * outer square brackets in the character class can be omitted.
     *
     * @param characterClass regex character class.
     * @return all exchange codes matching the specified character class.
     * @throws IllegalArgumentException if the exchange codes violate {@link MarketEventSymbols#EXCHANGES_PATTERN}
     */
    public static String getExchangesByPattern(String characterClass) {
        if (characterClass.isEmpty())
            return "";
        if (characterClass.charAt(0) != '[' && characterClass.charAt(0) != '\\')
            characterClass = '[' + characterClass + ']';
        Pattern pattern = Pattern.compile(characterClass);
        String exchanges = IntStream.rangeClosed(0, 127)
            .mapToObj(c -> String.valueOf((char) c))
            .filter(s -> pattern.matcher(s).matches())
            .collect(Collectors.joining());
        String unsupportedExchanges = exchanges.chars()
            .mapToObj(c -> String.valueOf((char) c))
            .filter(s -> !EXCHANGES_PATTERN.matcher(s).matches())
            .collect(Collectors.joining(", "));
        if (!unsupportedExchanges.isEmpty())
            throw new IllegalArgumentException("Unsupported exchange codes: " + unsupportedExchanges);
        return exchanges;
    }

    private static class SpreadLeg implements Comparable<SpreadLeg> {
        final String symbol;
        final double ratio;

        SpreadLeg(String symbol, double ratio) {
            this.symbol = symbol;
            this.ratio = ratio;
        }

        @Override
        public int compareTo(SpreadLeg other) {
            if (ratio > other.ratio)
                return -1;
            if (ratio < other.ratio)
                return 1;
            return symbol.compareTo(other.symbol) * (ratio >= 0 ? 1 : -1);
        }
    }

    private static boolean hasExchangeCodeInternal(String symbol, int length) {
        return length >= 2 && symbol.charAt(length - 2) == EXCHANGE_SEPARATOR;
    }

    private static String getBaseSymbolInternal(String symbol, int length) {
        return hasExchangeCodeInternal(symbol, length) ? symbol.substring(0, length - 2) : symbol.substring(0, length);
    }

    private static boolean hasAttributesInternal(String symbol, int length) {
        if (length >= 3 && symbol.charAt(length - 1) == ATTRIBUTES_CLOSE) {
            int i = symbol.lastIndexOf(ATTRIBUTES_OPEN, length - 2);
            return i >= 0 && i < length - 2;
        } else
            return false;
    }

    private static int getLengthWithoutAttributesInternal(String symbol) {
        int length = symbol.length();
        return hasAttributesInternal(symbol, length) ? symbol.lastIndexOf(ATTRIBUTES_OPEN) : length;
    }

    private static String getKeyInternal(String symbol, int i) {
        int val = symbol.indexOf(ATTRIBUTE_VALUE, i);
        return val < 0 ? null : symbol.substring(i, val);
    }

    private static int getNextKeyInternal(String symbol, int i) {
        int val = symbol.indexOf(ATTRIBUTE_VALUE, i) + 1;
        int sep = symbol.indexOf(ATTRIBUTES_SEPARATOR, val);
        return sep < 0 ? symbol.length() : sep + 1;
    }

    private static String getValueInternal(String symbol, int i, int j) {
        return symbol.substring(symbol.indexOf(ATTRIBUTE_VALUE, i) + 1, j - 1);
    }

    private static String dropKeyAndValueInternal(String symbol, int length, int i, int j) {
        return j == symbol.length() ? i == length + 1 ? symbol.substring(0, length) :
            symbol.substring(0, i - 1) + symbol.substring(j - 1) :
            symbol.substring(0, i) + symbol.substring(j);
    }

    private static String getAttributeInternal(String symbol, int length, String key) {
        if (length == symbol.length())
            return null;
        int i = length + 1;
        while (i < symbol.length()) {
            String cur = getKeyInternal(symbol, i);
            if (cur == null)
                break;
            int j = getNextKeyInternal(symbol, i);
            if (key.equals(cur))
                return getValueInternal(symbol, i, j);
            i = j;
        }
        return null;
    }

    private static String removeAttributeInternal(String symbol, int length, String key) {
        if (length == symbol.length())
            return symbol;
        int i = length + 1;
        while (i < symbol.length()) {
            String cur = getKeyInternal(symbol, i);
            if (cur == null)
                break;
            int j = getNextKeyInternal(symbol, i);
            if (key.equals(cur))
                symbol = dropKeyAndValueInternal(symbol, length, i, j);
            else
                i = j;
        }
        return symbol;
    }

    private static String addAttributeInternal(String symbol, int length, String key, String value) {
        if (length == symbol.length())
            return symbol + ATTRIBUTES_OPEN + key + ATTRIBUTE_VALUE + value + ATTRIBUTES_CLOSE;
        int i = length + 1;
        boolean added = false;
        while (i < symbol.length()) {
            String cur = getKeyInternal(symbol, i);
            if (cur == null)
                break;
            int j = getNextKeyInternal(symbol, i);
            int cmp = cur.compareTo(key);
            if (cmp == 0) {
                if (added) {
                    // drop, since we've already added this key
                    symbol = dropKeyAndValueInternal(symbol, length, i, j);
                } else {
                    // replace value
                    symbol = symbol.substring(0, i) + key + ATTRIBUTE_VALUE + value + symbol.substring(j - 1);
                    added = true;
                    i += key.length() + value.length() + 2;
                }
            } else if (cmp > 0 && !added) {
                // insert value here
                symbol = symbol.substring(0, i) + key + ATTRIBUTE_VALUE + value + ATTRIBUTES_SEPARATOR + symbol.substring(i);
                added = true;
                i += key.length() + value.length() + 2;
            } else
                i = j;
        }
        return added ? symbol : symbol.substring(0, i - 1) + ATTRIBUTES_SEPARATOR + key + ATTRIBUTE_VALUE + value + symbol.substring(i - 1);
    }
}
