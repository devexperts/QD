/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.Service;
import com.devexperts.services.SupersedesService;

/**
 * Factory interface for {@link SubscriptionFilter} instances.
 * The implementation of this interface is available for a {@link DataScheme} and can be retrieved
 * with {@link DataScheme#getService(Class) DataScheme.getService(SubscriptionFilterFactory.class)}
 * method.
 *
 * @deprecated Use {@link QDFilterFactory} instead.
 */
@Service(
    upgradeMethod = "com.devexperts.qd.spi.QDFilterFactory.fromFilterFactory",
    combineMethod = "com.devexperts.qd.spi.QDFilterFactory.combineFactories")
@SupersedesService(com.devexperts.qd.qtp.SubscriptionFilterFactory.class)
public interface SubscriptionFilterFactory {
    /**
     * Creates {@code SubscriptionFilter} based on the given {@code spec}.
     * This method may return null. New implementations of this method
     * shall return instances of classes that extend {@link QDFilter} class.
     * {@code chainedFilter} shall be integrated with "and" logic operation
     * using {@link CompositeFilters#makeAnd(QDFilter, QDFilter)} method.
     *
     * @param spec Filter specification, may be null.
     * @param chainedFilter Other filter to take into account, may be null.
     * @throws IllegalArgumentException if {@code spec} is invalid.
     */
    public SubscriptionFilter createFilter(String spec, SubscriptionFilter chainedFilter);
}
