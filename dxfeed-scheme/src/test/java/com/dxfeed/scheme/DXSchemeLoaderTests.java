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
package com.dxfeed.scheme;

import com.dxfeed.scheme.impl.DXSchemeFactory;
import com.dxfeed.scheme.model.SchemeModel;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DXSchemeLoaderTests {
    private static final DXSchemeFactory loader = new DXSchemeFactory();

    @Test
    public void testDefaultScheme() {
        assertNotNull(loader.createDataScheme("ext:dxfeed"));
    }

    @Test
    public void testEmptyScheme() {
        assertNotNull(loader.createDataScheme("ext:resource:01-empty.xml"));
    }

    @Test
    public void testEmptySchemeIsEmpty() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader().fromSpecification("resource:01-empty.xml").load();
        assertNotNull(file);
        assertTrue(file.isEmpty());
        assertEquals(1, file.mergedInCount());
    }
}
