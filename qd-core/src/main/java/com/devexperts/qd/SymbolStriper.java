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
package com.devexperts.qd;

import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.spi.SymbolStriperFactory;

/**
 * Strategy that allows to split the whole symbol universe into several smaller "stripes".
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
     * Returns {@link QDFilter filter} corresponding for the the given stripe index.
     * @param stripeIndex stripe index
     * @return QD filter for the stripe
     */
    public QDFilter getStripeFilter(int stripeIndex);

    /**
     * Creates {@link SymbolStriper} from the given specification for the given scheme,
     * or {@code null} if specification is unknown.
     *
     * @param scheme data scheme
     * @param spec striper specification
     * @return striper, or {@code null} if specification is unknown
     * @throws IllegalArgumentException if spec is invalid
     */
    public static SymbolStriper valueOf(DataScheme scheme, String spec) {
        return scheme.getService(SymbolStriperFactory.class).createStriper(spec);
    }
}
