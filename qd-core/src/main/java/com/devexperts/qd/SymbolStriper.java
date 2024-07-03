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
package com.devexperts.qd;

import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.spi.SymbolStriperFactory;

import java.util.BitSet;

/**
 * Symbol striping strategy that allows to split whole symbol universe into several smaller "stripes".
 * Striper can be used for example to split a large QD tape into several smaller parts based on some symbol pattern.
 *
 * <p>Stripes do not intersect, each stripe has a {@link #getStripeFilter(int) filter} that represents it.
 * Striper should split all symbols into more than one stripe (with the exception of
 * {@link MonoStriper} which does no splitting being essentially the "identity" striper).
 */
public interface SymbolStriper {

    /**
     * Returns a string representation of this striper.
     * This string representation should be parseable into this striper via {@link SymbolStriperFactory}.
     */
    public String getName();

    /**
     * Returns a string representation of this striper.
     * @see #getName()
     */
    @Override
    public String toString();

    /**
     * Returns scheme that this striper works for.
     * Returns {@code null} if this striper works for any scheme.
     */
    public DataScheme getScheme();

    /**
     * Returns the positive number of stripes that this striper splits into.
     * @return number of stripes (must be positive)
     */
    public int getStripeCount();

    /**
     * Returns the index of the stripe that the given <i>cipher-symbol</i> pair falls into.
     *
     * <p>Note that {@link SymbolCodec#getWildcardCipher() wildcard} symbol should not be submitted to this method,
     * since it should be accepted by all stripes therefore making stripes intersecting each other.
     *
     * @param cipher symbol cipher
     * @param symbol symbol string
     * @return stripe index
     * @see QDFilter#accept(QDContract, DataRecord, int, String) 
     */
    public int getStripeIndex(int cipher, String symbol);

    /**
     * Returns the index of the stripe that the given symbol falls into.
     * @param symbol symbol string (not-null)
     * @return stripe index
     * @see #getStripeIndex(int, String)
     */
    public default int getStripeIndex(String symbol) {
        return getStripeIndex(getScheme().getCodec().encode(symbol), symbol);
    }

    /**
     * Returns the index of the stripe that the given symbol (represented as character array) falls into.
     * @param symbol array that is the source of the symbol
     * @param offset the initial offset
     * @param length the symbol's length
     * @return stripe index
     * @see #getStripeIndex(int, String)
     */
    public default int getStripeIndex(char[] symbol, int offset, int length) {
        return getStripeIndex(new String(symbol, offset, length));
    }

    /**
     * Returns {@link QDFilter filter} corresponding for the given stripe index.
     * @param stripeIndex stripe index
     * @return QD filter for the stripe
     */
    public QDFilter getStripeFilter(int stripeIndex);

    /**
     * Returns bit set of {@link #getStripeCount()} size that specifies stripes that
     * intersect with the given filter. If intersection cannot be calculated {@code null} can be returned.
     * @param filter QD filter to check for intersection
     * @return bitset of {@link #getStripeCount()} size of intersecting stripes, or {@code null}.
     */
    public default BitSet getIntersectingStripes(QDFilter filter) {
        return null;
    }

    /**
     * Creates {@link SymbolStriper} from the given specification for the given scheme,
     * or {@code null} if specification is unknown.
     *
     * @param scheme data scheme
     * @param spec striper specification
     * @return striper, or {@code null} if specification is unknown
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static SymbolStriper valueOf(DataScheme scheme, String spec) {
        return scheme.getService(SymbolStriperFactory.class).createStriper(spec);
    }

    /**
     * Creates {@link SymbolStriper} from the given specification for the given scheme, or throws exception.
     * 
     * @param scheme data scheme
     * @param spec striper specification
     * @return non-null striper
     * @throws FilterSyntaxException if spec is invalid or unknown
     */
    public static SymbolStriper definedValueOf(DataScheme scheme, String spec) {
        SymbolStriper striper = valueOf(scheme, spec);
        if (striper == null) {
            throw new FilterSyntaxException("Unknown stripe format: " + spec);
        }
        return striper;
    }
}
