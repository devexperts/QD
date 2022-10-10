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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;

import java.util.Objects;

import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;
import static com.devexperts.qd.kit.RangeUtil.encodeSymbol;
import static com.devexperts.qd.kit.RangeUtil.skipPrefix;

// An attempt to create a range filter without iteration over char array and only work with long codes
public class LongCodePrefixRangeFilter extends QDFilter {

    public static final String RANGE_FILTER_PREFIX = "range";

    protected final char[] leftChars;
    protected final long leftCode;

    protected final char[] rightChars;
    protected final long rightCode;

    protected final int wildcard;

    public static LongCodePrefixRangeFilter valueOf(DataScheme scheme, String spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.length() < RANGE_FILTER_PREFIX.length() + 3)
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        char delimiter = spec.charAt(RANGE_FILTER_PREFIX.length());
        int idx1 = spec.indexOf(delimiter, RANGE_FILTER_PREFIX.length() + 1);
        if (idx1 < 0)
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);
        int idx2 = spec.indexOf(delimiter, idx1 + 1);
        if (idx2 < 0 || idx2 != spec.length() - 1)
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        String left = spec.substring(RANGE_FILTER_PREFIX.length() + 1, idx1);
        String right = spec.substring(idx1 + 1, idx2);
        if (!left.isEmpty() && !right.isEmpty() && left.compareTo(right) >= 0)
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        return new LongCodePrefixRangeFilter(scheme, spec, left, right);
    }

    protected LongCodePrefixRangeFilter(DataScheme scheme, String spec, String left, String right) {
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
        return compareByCode(symbol, symbolIdx, length);
    }

    private boolean acceptCode(long symbolCode) {
        long code = skipPrefix(symbolCode);
        return (leftCode <= code && code < rightCode);
    }

    private boolean compareByCode(String s, int from, int to) {
        // For an empty symbol only check that the left border allows it
        if (from >= to)
            return (leftCode <= 0);

        int length = to - from;
        int codeLength = Math.min(CODE_LENGTH, length);

        long code = 0;
        long mask = 0;
        int shift = 64;
        for (int i = 0; i < codeLength; i++) {
            // If character is outside of the byte range replace it with the largest positive byte char
            long c = Math.min(0x7F, s.charAt(from + i));

            shift -= 8;
            code |= c << shift;
            mask |= 0xFFL << shift;

            // Check for early returns: strictly out or inside boundaries
            if (code < (leftCode & mask) || code > (rightCode & mask)) {
                return false;
            } else if ((leftCode & mask) < code && code < (rightCode & mask)) {
                return true;
            }
            // Otherwise (touching some boundary) - continue
        }
        // Symbol code is now complete
        // Solution is not found because the code touches some boundary

        // Check boundaries again
        if (code < leftCode || code > rightCode || (code == rightCode && rightChars.length <= CODE_LENGTH)) {
            return false;
        } else if (leftCode < code && code < rightCode) {
            return true;
        }

        // For equal prefixes compare the rest of the long strings
        if (to - from >= CODE_LENGTH) {
            if (code == leftCode && compareRemaining(leftChars, s, from) > 0) {
                return false;
            }
            //noinspection RedundantIfStatement
            if (code == rightCode && compareRemaining(rightChars, s, from) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static int compareRemaining(char[] range, String symbol, int from) {
        for (int i = CODE_LENGTH, j = CODE_LENGTH + from; i < range.length && j < symbol.length(); i++, j++) {
            char c1 = range[i];
            char c2 = symbol.charAt(j);
            if (c1 != c2)
                return c1 - c2;
        }
        return range.length - symbol.length();
    }
}
