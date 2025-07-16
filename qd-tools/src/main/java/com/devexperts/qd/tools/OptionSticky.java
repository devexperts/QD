/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.QDEndpoint;

public class OptionSticky extends OptionTimePeriod implements EndpointOption {

    public OptionSticky() {
        super(EMPTY_SHORT_NAME, "sticky", "<n>", "Specify sticky subscription time period");
    }

    @Override
    public boolean isSupportedEndpointOption(QDEndpoint.Builder endpointBuilder) {
        return endpointBuilder.supportsProperty(QDEndpoint.DXFEED_STICKY_SUBSCRIPTION_PROPERTY);
    }

    @Override
    public void applyEndpointOption(QDEndpoint.Builder endpointBuilder) {
        if (isSet()) {
            endpointBuilder.withStickySubscriptionPeriod(getValue());
        }
    }
}
