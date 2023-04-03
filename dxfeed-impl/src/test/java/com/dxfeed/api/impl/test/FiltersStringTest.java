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
package com.dxfeed.api.impl.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FiltersStringTest {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();

    @Test
    public void testFilterString() {
        assertStringOk("chartdata");
        assertStringOk("!chartdata");
        assertStringOk("feed");
        assertStringOk("fxsymbol");
        assertStringOk("cs");
        assertStringOk("opt");
        assertStringOk("fut");

        assertStringOk("chartdata", "chartdata&*");
        assertStringOk("chartdata", "*&chartdata");

        assertStringOk("!chartdata", "!chartdata&*");
        assertStringOk("!chartdata", "*&!chartdata");

        assertStringOk("chartdata,feed", "((chartdata,feed))");
        assertStringOk("!chartdata&!feed", "!(chartdata,feed)");
        assertStringOk("!chartdata&!feed", "!(((chartdata,feed)))");

        assertStringOk("IBM,MSFT", "IBM,MSFT");
        assertStringOk("A,B,C,D", "A,B,C,D");
        assertStringOk("!A&!B&!C&!D", "!((A,B,C,D))");
    }

    private void assertStringOk(String original) {
        assertStringOk(original, original);
    }

    private void assertStringOk(String expected, String original) {
        QDFilter filter = CompositeFilters.valueOf(original, SCHEME);
        assertEquals(expected, filter.toString());
    }
}
