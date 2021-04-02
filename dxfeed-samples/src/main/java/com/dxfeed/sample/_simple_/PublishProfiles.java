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
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.market.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Using address like ":7700" it starts a server on a specified port where it provides Profile
 * event for any symbol ending with ":TEST" suffix.
 */
public class PublishProfiles {
    public static void main(String[] args) {
        String address = args[0];

        // Create publisher endpoint and connect it to the specified address
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER).connect(address);
        final DXPublisher publisher = endpoint.getPublisher();

        // Observe Profile subscriptions and publish profiles for "xxx:TEST" symbols
        publisher.getSubscription(Profile.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                List<Profile> events = new ArrayList<>();
                for (Object symbol : symbols) {
                    if (symbol instanceof String) {
                        String s = (String) symbol;
                        if (s.endsWith(":TEST")) {
                            Profile profile = new Profile(s);
                            profile.setDescription("Test symbol");
                            events.add(profile);
                        }
                    }
                }
                publisher.publishEvents(events);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                // nothing to do here
            }

            @Override
            public void subscriptionClosed() {
                // nothing to do here
            }
        });
    }
}
