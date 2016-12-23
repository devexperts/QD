/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import junit.framework.Assert;

class AsserteableListener implements DataListener, SubscriptionListener {
    private boolean available;

    public void assertAvailable() {
        Assert.assertTrue(available);
        available = false;
    }

    public void assertNotAvailable() {
        Assert.assertFalse(available);
    }

    public void dataAvailable(DataProvider provider) {
        available = true;
    }

    public void subscriptionAvailable(SubscriptionProvider provider) {
        available = true;
    }
}
