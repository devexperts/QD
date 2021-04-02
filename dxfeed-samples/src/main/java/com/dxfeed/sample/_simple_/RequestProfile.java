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
package com.dxfeed.sample._simple_;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.event.market.Profile;

import java.util.concurrent.TimeUnit;

/**
 * Using address like "localhost:7700" and a symbol list "A:TEST" it connects to the running
 * "PublishProfiles" sample, prints symbol profile event, and exits.
 */
public class RequestProfile {
    public static void main(String[] args) {
        String address = args[0];
        String symbol = args[1];
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.FEED).connect(address);
        Profile profile = endpoint.getFeed().getLastEventPromise(Profile.class, symbol).await(5, TimeUnit.SECONDS);
        System.out.println(profile);
        endpoint.close(); // terminate the endpoint we've created when done
    }
}
