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

/**
 * Stable subscription filter has a fixed logic (it cannot depend on any external resources or state)
 * and it is reconstructible from string by {@link SubscriptionFilterFactory}.
 *
 * <h3>Legacy interface</h3>
 *
 * <p>This is a legacy interface. All new implementations shall extend
 * {@link QDFilter} class. Legacy implementations of this interface are coerced into QDFilter
 * class using {@link QDFilter#fromFilter} method.
 */
public interface StableSubscriptionFilter extends SubscriptionFilter {
    /**
     * Returns a stable filter that is the same or more encompassing as this filter.
     * It is always safe to return {@link QDFilter#ANYTHING}, which means that this filter is not stable
     * (it is <b>dynamic</b>) and the only stable extension of it constitutes all symbols.
     * Stable filters must return {@code this} as a result of this method. The result of this method
     * satisfies the following constrains:
     * <ol>
     * <li>Result must be stable filter, that is {@code result} is null or {@code result.toStableFilter() == result}
     * <li>Result must be more encompassing, that is {@code result} is null or {@code this.acceptRecord(...)}
     *     implies {@code result.acceptRecord(...)}.
     * <li>Result must be reconstructible from string, that is {@code result} is null or
     *     {@code result.toString()} must parse back to the same filter via data scheme's
     *     {@link SubscriptionFilterFactory}.
     * </ol>
     *
     * <h3>Legacy method</h3>
     *
     * <p>All new implementations of this method shall return an instance of class that extends {@link QDFilter}
     * and should never return null.
     */
    public StableSubscriptionFilter toStableFilter();
}
