/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.devexperts.qd.dxlink.websocket.application.DxLinkWebSocketApplicationConnectionFactory.parseAcceptEventFields;
import static org.junit.Assert.assertEquals;

public class DxLinkWebSocketApplicationConnectionFactoryTest {

    @Test
    public void setAcceptEventFields() {
        assertEquals(
            new HashMap<String, List<String>>() {{
                put("Quote", Arrays.asList("bidPrice", "askPrice"));
                put("Order", Arrays.asList("price"));
            }},
            parseAcceptEventFields("(Quote[bidPrice,askPrice],Order[price])")
        );
    }
}