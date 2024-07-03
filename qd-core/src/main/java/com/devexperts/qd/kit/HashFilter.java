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

import static com.devexperts.qd.kit.HashStriper.index;

/**
 * Symbol filter of form {@code hashMofN} that accepts symbols that belong to the specified hash group M out of N
 * (where M and N are integers and M&lt;N and N is a power of 2).
 *
 * <p>HashFilter can be matched by the following regex: <b>{@code hash([0-9]+)of([0-9]+)}</b>,
 * where the first matched group defines the hash group, and the second group - total number of groups.
 */
public class HashFilter extends QDFilter {

    public static final String HASH_FILTER_PREFIX = "hash";

    private static final Pattern FILTER_PATTERN = Pattern.compile(HASH_FILTER_PREFIX + "([0-9]+)of([0-9]+)");

    protected final int index;
    protected final int shift;
    protected final int wildcard;

    /**
     * Parses a given specification as hash filter for a given scheme.
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
            throw new FilterSyntaxException("Invalid hash filter definition: " + spec);
        try {
            int stripeIndex = Integer.parseInt(m.group(1));
            int stripeCount = Integer.parseInt(m.group(2));
            return new HashFilter(new HashStriper(scheme, stripeCount), stripeIndex, spec);
        } catch (NumberFormatException e) {
            throw new FilterSyntaxException("Invalid number in hash filter definition: " + spec);
        }
    }

    /**
     * Returns default {@link HashFilter} name where index is left-padded with zeros for better alignment,
     * e.g. "hash001of256" instead of "hash1of256". Note that input parameters are not validated.
     *
     * @param stripeIndex stripe index
     * @param stripeCount stripe count
     * @return filter name
     */
    public static String formatName(int stripeIndex, int stripeCount) {
        String count = Integer.toString(stripeCount);
        String index = String.format("%0" + count.length() + "d", stripeIndex);
        return HASH_FILTER_PREFIX + index + "of" + count;
    }

    protected HashFilter(HashStriper striper, int index) {
        this(striper, index, null);
    }

    protected HashFilter(HashStriper striper, int index, String spec) {
        super(Objects.requireNonNull(striper, "striper").getScheme());
        int count = striper.getStripeCount();
        if (index < 0 || index >= count) {
            throw new FilterSyntaxException("Invalid index: " + index + " is not in [0, " + count + ")");
        }

        this.index = index;
        this.shift = striper.getShift();
        this.wildcard = getScheme().getCodec().getWildcardCipher();
        setName((spec != null) ? spec : formatName(index, count));
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
    public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        if (cipher == wildcard) {
            // Always allow wildcard
            return true;
        }
        return index(cipher != 0 ? getScheme().getCodec().hashCode(cipher) : symbol.hashCode(), shift) == index;
    }

    @Override
    public QDFilter toStableFilter() {
        return this;
    }
}
