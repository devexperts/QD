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
 * The {@code SubscriptionFilter} is used to block unwanted subscription.
 * Its main purpose is to control subscription and corresponding data flows in
 * a situation with multiple providers and consumers. For example, it allows
 * to block sending local data subscription to remote data provider or to block
 * remote data consumers from subscribing to local data.
 * <p>
 * The {@code SubscriptionFilter} must be stable - it must return the same
 * result for the same record no matter of external circumstances, otherwise
 * the behavior of QD components is unspecified.
 * <p>
 * The {@code SubscriptionFilter} imposes the same performance restrictions
 * as other visiting interfaces - the implementer of {@code SubscriptionFilter}
 * must perform its operations quickly without need for synchronization and prolonged
 * external actions.
 * <p>
 * As a general rule  {@link SymbolCodec#getWildcardCipher wildcard} subscription should
 * always be accepted (when the corresponding record is allowed at all), however filtering
 * wildcard subscription is actually allowed (you can install filters to decide which
 * agents or distributors are allowed to use wildcard subscription).
 *
 * <h3>Legacy interface</h3>
 *
 * <p>This is a legacy interface. All new implementations shall extend
 * {@link QDFilter} class. Legacy implementations of this interface are coerced into QDFilter
 * class using {@link QDFilter#fromFilter} method.
 */
public interface SubscriptionFilter {
    /**
     * Determines if specified record shall be processed by corresponding subsystem.
     *
     * @return true if the record should be processed; false if it should be ignored.
     */
    public boolean acceptRecord(DataRecord record, int cipher, String symbol);
}
