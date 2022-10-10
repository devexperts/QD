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

/**
 * Symbol filter of form {@code hashMofN} that accepts symbols that belong to the specified hash group M out of N
 * (where M and N are integers and M&lt;N and N is a power of 2).
 */
public class HashFilter extends QDFilter {

    public static final String HASH_FILTER_PREFIX = "hash";

    private static final Pattern FILTER_PATTERN = Pattern.compile(HASH_FILTER_PREFIX + "([0-9]+)of([0-9]+)");

    private final HashStriper striper;
    private final int index;
    private final int wildcard;

    public static QDFilter valueOf(DataScheme scheme, String spec) {
        Matcher m = FILTER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches())
            throw new IllegalArgumentException("Invalid hash filter definition: " + spec);
        try {
            int stripeIndex = Integer.parseInt(m.group(1));
            int stripeCount = Integer.parseInt(m.group(2));
            return new HashFilter(new HashStriper(scheme, stripeCount), stripeIndex);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in hash filter definition: " + spec);
        }
    }

    protected HashFilter(HashStriper striper, int index) {
        super(Objects.requireNonNull(striper, "striper").getScheme());
        int count = striper.getStripeCount();
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid index: " + index + " is not in [0, " + count + ")");
        }

        this.striper = striper;
        this.index = index;
        this.wildcard = getScheme().getCodec().getWildcardCipher();
        setName(HASH_FILTER_PREFIX + index + "of" + count);
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
        return striper.getStripeIndex(cipher, symbol) == index;
    }

    @Override
    public QDFilter toStableFilter() {
        return this;
    }
}
