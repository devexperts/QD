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

import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.qd.qtp.QDEndpoint;

public class OptionManagementHtml extends OptionInteger implements EndpointOption {
    private static OptionManagementHtml instance;

    public static OptionManagementHtml getInstance() {
        if (instance == null) {
            instance = new OptionManagementHtml();
        }
        return instance;
    }

    private OptionManagementHtml() {
        super('h', "html-management", "<port>", "Install JMX HTML management console at a specified port.");
        setDeprecated("Use -D" + JMXEndpoint.JMX_HTML_PORT_PROPERTY + "=<port> (and optional -D" + JMXEndpoint.JMX_HTML_BIND_PROPERTY + "=<bindAddress>) options.");
    }

    public boolean isSupportedEndpointOption(QDEndpoint.Builder endpointBuilder) {
        return endpointBuilder.supportsProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY);
    }

    public void applyEndpointOption(QDEndpoint.Builder endpointBuilder) {
        if (isSet())
            endpointBuilder.withProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY, String.valueOf(getValue()));
    }
}
