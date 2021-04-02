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
package com.devexperts.qd.tools;

import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.qd.qtp.QDEndpoint;

public class OptionStat extends OptionTimePeriod implements EndpointOption {
    public OptionStat() {
        super('s', "stat", "<n>", "Log stats on specified periods (in seconds by default).");
    }

    @Override
    public boolean isSupportedEndpointOption(QDEndpoint.Builder endpointBuilder) {
        return endpointBuilder.supportsProperty(MonitoringEndpoint.MONITORING_STAT_PROPERTY);
    }

    @Override
    public void applyEndpointOption(QDEndpoint.Builder endpointBuilder) {
        if (isSet())
            endpointBuilder.withProperty(MonitoringEndpoint.MONITORING_STAT_PROPERTY, getValue().toString());
    }
}
