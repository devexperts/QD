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

import com.devexperts.qd.ng.RecordConsumer;

/**
 * The <code>SubscriptionConsumer</code> processes incoming subscription.
 * See {@link SubscriptionIterator} for description of corresponding contracts.
 * @deprecated Use {@link RecordConsumer}.
 */
public interface SubscriptionConsumer {
    public static SubscriptionConsumer VOID = Void.VOID;

    /**
     * Processes subscription from specified subscription iterator.
     * @deprecated Use {@link RecordConsumer#process} method.
     */
    public void processSubscription(SubscriptionIterator iterator);
}
