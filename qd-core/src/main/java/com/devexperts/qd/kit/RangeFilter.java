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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Range symbol filter with specification {@code range-<A>-<B>-}, where A and B are range boundaries
 * using dictionary [a-zA-Z0-9], accepts symbols that are greater or equal than A and less than B skipping
 * non-dictionary prefix.
 *
 * <p>The {@link #SYMBOL_PATTERN} regex describes the symbol prefix that will be skipped so,
 * for example, symbols "ES", "/ESU23", "./ESH23C1800", and ""=2*./ESU22-/ESU23"
 * will all start with "ES" after removing unused prefixes and will all fall into {@code range-ES-ET-} stripe.
 * The range boundary can be empty, so {@code range-M--} will accept all symbols starting from "M" and greater.
 *
 * <p>RangeFilter can be matched by the following regex: <b>{@code range-([a-zA-Z0-9]{0,8})-([a-zA-Z0-9]{0,8})-}</b>,
 * where the first matched group defines range's left boundary, and the second group - range's right boundary.
 * For performance reasons ranges must not be greater than 8 characters long.
 *
 * @see #SYMBOL_PATTERN
 */
public class RangeFilter extends QDFilter {

    /** Range filter prefix. */
    public static final String RANGE_FILTER_PREFIX = "range";

    /** Range filter delimiter character. */
    public static final char RANGE_DELIMITER_CHAR = '-';

    /** Range filter delimiter string. */
    public static final String RANGE_DELIMITER = String.valueOf(RANGE_DELIMITER_CHAR);
    
    /**
     * Java regex {@link Pattern pattern} string that would capture the part of the symbol
     * that would be used by {@link RangeStriper} and {@link RangeFilter}.
     *
     * <p>This pattern can be broken down to several parts:
     * <ul>
     *     <li>Optional spread prefix: {@code (?:=[-+]?[0-9]+(?:\.[0-9]*)?\\*)?}
     *     <ul>
     *         <li>Spread prefix: {@code =}</li>
     *         <li>Optional sign: {@code [-+]?}</li>
     *         <li>Decimal number: {@code [0-9]+(?:\.[0-9]*)?}</li>
     *         <li>Multiplication sign: {@code \\*}</li>
     *     </ul>
     *     </li>
     *     <li>Skipped symbol prefix: {@code [^a-zA-Z0-9]*}</li>
     *     <li>Symbol (that would be used by stripers and filters) starting with one of
     *     lower and capital letters, digits: {@code ([a-zA-Z0-9].*)}, or empty</li>
     * </ul>
     * If symbol contains characters out of [0..127] range they will be replaced with character 127 (0x7F).
     */
    public static final String SYMBOL_PATTERN = "(?:=[-+]?[0-9]+(?:\\.[0-9]*)?\\*)?[^a-zA-Z0-9]*(.*)";

    protected static final String BOUNDARY_PATTERN = "([a-zA-Z0-9]{0,8})";
    protected static final Pattern FILTER_PATTERN = Pattern.compile(RANGE_FILTER_PREFIX +
        RANGE_DELIMITER + BOUNDARY_PATTERN + RANGE_DELIMITER + BOUNDARY_PATTERN + RANGE_DELIMITER);

    protected final char[] leftChars;
    protected final long leftCode;

    protected final char[] rightChars;
    protected final long rightCode;

    protected final int wildcard;

    /**
     * Parses a given specification as range filter for a given scheme.
     * Note that range filter "range---" that accepts everything is replaced with {@link QDFilter#ANYTHING}.
     *
     * @param scheme the scheme.
     * @param spec the filter specification.
     * @return filter.
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static QDFilter valueOf(DataScheme scheme, String spec) {
        Matcher m = FILTER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches())
            throw new FilterSyntaxException("Invalid range filter definition: " + spec);

        String left = m.group(1);
        String right = m.group(2);
        if (left.isEmpty() && right.isEmpty())
            return QDFilter.ANYTHING;

        return new RangeFilter(scheme, left, right, spec);
    }

    /**
     * Returns default {@link RangeFilter} name. Note that input parameters are not validated.
     *
     * @param left range left boundary (inclusive)
     * @param right range right boundary (exclusive)
     * @return filter name
     */
    public static String formatName(String left, String right) {
        return RANGE_FILTER_PREFIX + RANGE_DELIMITER + left + RANGE_DELIMITER + right + RANGE_DELIMITER;
    }

    protected RangeFilter(DataScheme scheme, String left, String right) {
        this(scheme, left, right, null);
    }

    protected RangeFilter(DataScheme scheme, String left, String right, String spec) {
        super(scheme);

        // Constructor invariant: spec must be valid
        assert(spec == null || spec.equals(formatName(left, right)));
        setName((spec != null) ? spec : formatName(left, right));

        if (!left.isEmpty() && !right.isEmpty() && left.compareTo(right) >= 0)
            throw new FilterSyntaxException("Invalid range filter definition: " + this);

        this.leftChars = left.toCharArray();
        this.leftCode = !left.isEmpty() ? RangeUtil.encodeSymbol(left) : 0;

        this.rightChars = right.toCharArray();
        this.rightCode = !right.isEmpty() ? RangeUtil.encodeSymbol(right) : Long.MAX_VALUE;

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

    protected boolean acceptString(String symbol) {
        int length = symbol.length();
        int symbolIdx = RangeUtil.skipPrefix(symbol, length);

        return (leftCode == 0 || RangeUtil.compareByString(leftChars, symbol, symbolIdx) <= 0) &&
            (rightCode == Long.MAX_VALUE || RangeUtil.compareByString(rightChars, symbol, symbolIdx) > 0);
    }

    protected boolean acceptCode(long symbolCode) {
        long code = RangeUtil.skipPrefix(symbolCode);
        return (leftCode <= code && code < rightCode);
    }
}
