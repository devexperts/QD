/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.devexperts.qd.kit.RangeUtil.compareByString;
import static com.devexperts.qd.kit.RangeUtil.encodeSymbol;
import static com.devexperts.qd.kit.RangeUtil.skipPrefix;

/**
 * Range symbol filter of form {@code range_<A>_<B>_}
 * (where instead of '_' delimiter any character from the set of [_a-zA-Z0-9] can be used),
 * that accepts symbols that are equal or greater than A and less than B in dictionary order.
 */
public class RangeFilter extends QDFilter {

    public static final String RANGE_FILTER_PREFIX = "range";

    // Capture delimiter into group(1), reluctant match left(2) and right(3),
    // greedy match tail(4) to capture extra delimiters - tail must be empty
    private static final Pattern FILTER_PATTERN = Pattern.compile(
        RANGE_FILTER_PREFIX + "([_a-zA-Z0-9])([_a-zA-Z0-9]*?)\\1([_a-zA-Z0-9]*?)\\1(.*)");

    private final char[] leftChars;
    private final long leftCode;

    private final char[] rightChars;
    private final long rightCode;

    private final int wildcard;

    public static QDFilter valueOf(DataScheme scheme, String spec) {
        Matcher m = FILTER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches() || !m.group(4).isEmpty())
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        String left = m.group(2);
        String right = m.group(3);
        if (left.isEmpty() && right.isEmpty())
            return QDFilter.ANYTHING;

        if (!left.isEmpty() && !right.isEmpty() && left.compareTo(right) >= 0)
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        return new RangeFilter(scheme, spec, left, right);
    }

    protected RangeFilter(DataScheme scheme, String spec, String left, String right) {
        super(scheme);
        setName(spec);

        this.leftChars = left.toCharArray();
        this.leftCode = !left.isEmpty() ? encodeSymbol(left) : 0;
        this.rightChars = right.toCharArray();
        this.rightCode = !right.isEmpty() ? encodeSymbol(right) : Long.MAX_VALUE;

        this.wildcard = getScheme().getCodec().getWildcardCipher();
    }

    @Override
    public boolean isFast() {
        return true;
    }

    @Override
    public Kind getKind() {
        return Kind.OTHER_SYMBOL_ONLY;
    }

    @Override
    public QDFilter toStableFilter() {
        return this;
    }

    @Override
    public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        if (cipher == wildcard) {
            // Always allow wildcard
            return true;
        }

        // Only symbol is specified
        if (symbol != null) {
            return acceptString(symbol);
        }

        // Only cipher is specified
        long code = getScheme().getCodec().decodeToLong(cipher);
        if ((code >>> 56) == '=') {
            // Use ineffective route for spread ciphers since they should never happen,
            // e.g. PentaCodec can only encode cipher for 4-letter spread only ("=A+B")
            return acceptString(getScheme().getCodec().decode(cipher));
        }
        return acceptCode(code);
    }

    private boolean acceptString(String symbol) {
        int length = symbol.length();
        int symbolIdx = skipPrefix(symbol, length);

        return (leftCode == 0 || compareByString(leftChars, symbol, symbolIdx) <= 0) &&
            (rightCode == Long.MAX_VALUE || compareByString(rightChars, symbol, symbolIdx) > 0);
    }

    private boolean acceptCode(long symbolCode) {
        long code = skipPrefix(symbolCode);
        return (leftCode <= code && code < rightCode);
    }
}
