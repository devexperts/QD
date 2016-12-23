/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.test;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import junit.framework.TestCase;

public class DXEndpointTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @Override
    protected void tearDown() throws Exception {
        ThreadCleanCheck.after();
    }

    public void testCloseNotConnected() {
        DXEndpoint endpoint = DXEndpoint.create();
        endpoint.close();
    }

}
