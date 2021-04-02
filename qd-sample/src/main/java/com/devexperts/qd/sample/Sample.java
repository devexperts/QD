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
package com.devexperts.qd.sample;

import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.qd.samplecert.SampleCert;

import java.util.Properties;

/**
 * The <code>Sample</code> demonstrates how to build simple server & client GUI with QD.
 */
public class Sample {
    static {
        // Init SSL server & client properties for sample
        SampleCert.init();
    }

    public static Properties getMonitoringProps(int jmxHtmlPort) {
        // define monitoring properties
        Properties props = new Properties();
        props.setProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY, "" + jmxHtmlPort);
        props.setProperty(JMXEndpoint.JMX_HTML_SSL_PROPERTY, "");
        props.setProperty(JMXEndpoint.JMX_HTML_AUTH_PROPERTY, "admin:secret");
        props.setProperty(MonitoringEndpoint.MONITORING_STAT_PROPERTY, "10s");
        return props;
    }

    public static void main(String[] args) {
        // Server QD creation.
        SampleServer.initServer("(tls+:4444[customAuth])(nio::5555)", 1234);
        // Client QD creation.
        SampleClient.initClient("(tls+127.0.0.1:4444[customAuth])(:6666)", 1234);
    }
}
