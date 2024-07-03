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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;

import java.util.BitSet;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy that splits symbol universe into stripes by hash groups.
 * 
 * <p>Example: striper {@code byhash16} splits the symbol universe into 16 stripes by hash.
 *
 * <p>HashStriper can be matched by the following regex: <b>{@code byhash([0-9]+)}</b>,
 * where the matched group defines the number of stripes (must be a power of 2).
 *
 * @see HashFilter
 */
public class HashStriper implements SymbolStriper {
    
    public static final String HASH_STRIPER_PREFIX = "byhash";

    private static final Pattern STRIPER_PATTERN = Pattern.compile(HASH_STRIPER_PREFIX + "([0-9]+)");

    private static final int MAGIC = 0xB46394CD;

    private final String name;
    private final DataScheme scheme;
    private final SymbolCodec codec;
    private final int stripeCount;
    private final int shift;

    // Cached filters
    private final HashFilter[] filters;

    /**
     * Parses a given specification as hash striper for a given scheme.
     *
     * @param scheme the scheme.
     * @param spec the striper specification.
     * @return symbol striper.
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static HashStriper valueOf(DataScheme scheme, String spec) {
        Matcher m = STRIPER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches())
            throw new FilterSyntaxException("Invalid hash striper definition: " + spec);
        try {
            return new HashStriper(scheme, Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            throw new FilterSyntaxException("Invalid number in hash striper definition: " + spec);
        }
    }

    /**
     * Constructs a symbol striper for a given number of hash stripes.
     * Note that if the number of stripes is 1 then {@link MonoStriper#INSTANCE} striper will be returned.
     *
     * @param scheme the scheme.
     * @param stripeCount the number of hash stripes.
     * @return symbol striper.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static SymbolStriper valueOf(DataScheme scheme, int stripeCount) {
        if (stripeCount < 1)
            throw new FilterSyntaxException("Invalid stripe count: " + stripeCount);
        if (stripeCount == 1)
            return MonoStriper.INSTANCE;
        return new HashStriper(scheme, stripeCount);
    }

    protected HashStriper(DataScheme scheme, int stripeCount) {
        Objects.requireNonNull(scheme, "scheme");
        if ((stripeCount < 2) || ((stripeCount & (stripeCount - 1)) != 0))
            throw new FilterSyntaxException("Stripe count should a power of 2 and at least 2");

        this.name = HASH_STRIPER_PREFIX + stripeCount;
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.stripeCount = stripeCount;
        this.shift = 32 - Integer.numberOfTrailingZeros(stripeCount);
        this.filters = new HashFilter[stripeCount];
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public int getStripeCount() {
        return stripeCount;
    }

    @Override
    public int getStripeIndex(int cipher, String symbol) {
        return index(cipher != 0 ? codec.hashCode(cipher) : symbol.hashCode(), shift);
    }

    @Override
    public int getStripeIndex(String symbol) {
        return index(symbol.hashCode(), shift);
    }

    @Override
    public int getStripeIndex(char[] chars, int offset, int length) {
        int hash = 0;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + chars[offset + i];
        }
        return index(hash, shift);
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (filters[stripeIndex] == null) {
            filters[stripeIndex] = new HashFilter(this, stripeIndex);
        }
        return filters[stripeIndex];
    }

    @Override
    public BitSet getIntersectingStripes(QDFilter filter) {
        if (!(filter instanceof HashFilter))
            return null;

        BitSet result = new BitSet(getStripeCount());
        HashFilter hashFilter = (HashFilter) filter;

        int diff = this.shift - hashFilter.shift;
        if (diff > 0) {
            // Case: "byhash8" vs "hash1of16"
            // Smaller filter is completely contained within one larger stripe
            result.set(hashFilter.index >>> diff);
        } else {
            // Case: "byhash8" vs "hash1of4"
            // Larger filter occupies several small stripes
            result.set(hashFilter.index << -diff, (hashFilter.index + 1) << -diff);
        }
        return result;
    }

    @Override
    public String toString() {
        return getName();
    }

    protected int getShift() {
        return shift;
    }

    protected static int index(int hash, int shift) {
        return hash * MAGIC >>> shift;
    }
}
