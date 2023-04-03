/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

class AsserteableListener implements DataListener, SubscriptionListener {
    private boolean available;

    public void assertAvailable() {
        assertTrue(available);
        available = false;
    }

    public void assertNotAvailable() {
        assertFalse(available);
    }

    public void dataAvailable(DataProvider provider) {
        available = true;
    }

    public void subscriptionAvailable(SubscriptionProvider provider) {
        available = true;
    }
}
