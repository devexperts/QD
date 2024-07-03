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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.RangeFilter;
import com.devexperts.qd.kit.RangeUtil;

import java.util.Objects;
import java.util.regex.Matcher;

import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;

// An attempt to create a range filter without iteration over char array and only work with long codes
public class LongCodePrefixRangeFilter extends RangeFilter {

    public static LongCodePrefixRangeFilter valueOf(DataScheme scheme, String spec) {
        Matcher m = FILTER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches())
            throw new IllegalArgumentException("Invalid range filter definition: " + spec);

        String left = m.group(1);
        String right = m.group(2);

        return new LongCodePrefixRangeFilter(scheme, left, right, spec);
    }

    protected LongCodePrefixRangeFilter(DataScheme scheme, String left, String right, String spec) {
        super(scheme, left, right, spec);
    }

    @Override
    protected boolean acceptString(String symbol) {
        int length = symbol.length();
        int symbolIdx = RangeUtil.skipPrefix(symbol, length);
        return compareByCode(symbol, symbolIdx, length);
    }

    @Override
    protected boolean acceptCode(long symbolCode) {
        long code = RangeUtil.skipPrefix(symbolCode);
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
            // If character is outside the byte range replace it with the largest positive byte char
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
