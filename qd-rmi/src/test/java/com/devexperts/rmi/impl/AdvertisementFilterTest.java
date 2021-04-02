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
package com.devexperts.rmi.impl;

import com.devexperts.util.InvalidFormatException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdvertisementFilterTest {

    @Test
    public void testFilter() {
        AdvertisementFilter filter = AdvertisementFilter.valueOf("all ");
        assertTrue(filter.isSendAdvertisement());

        filter = AdvertisementFilter.valueOf("\tNoNe  ");
        assertFalse(filter.isSendAdvertisement());
    }

    @Test(expected = InvalidFormatException.class)
    public void testInvalidFormat() {
        AdvertisementFilter.valueOf("something");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyFilterConfig() {
        AdvertisementFilter.valueOf("");
    }

    @Test(expected = NullPointerException.class)
    public void testNullFilterConfig() {
        AdvertisementFilter.valueOf(null);
    }
}
