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
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.QDEndpoint;

public class OptionStripe extends OptionString implements EndpointOption {
    public OptionStripe() {
        super('\0', "stripe", "<stripe>", "Specify symbol striping strategy, e.g. \"byhash4\"");
    }

    @Override
    public boolean isSupportedEndpointOption(QDEndpoint.Builder endpointBuilder) {
        return endpointBuilder.supportsProperty(QDEndpoint.DXFEED_STRIPE_PROPERTY);
    }

    @Override
    public void applyEndpointOption(QDEndpoint.Builder endpointBuilder) {
        if (isSet())
            endpointBuilder.withProperty(QDEndpoint.DXFEED_STRIPE_PROPERTY, getValue());
    }
}
