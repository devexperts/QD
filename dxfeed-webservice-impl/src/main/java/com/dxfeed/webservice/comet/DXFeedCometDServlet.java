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
package com.dxfeed.webservice.comet;

import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.util.SystemProperties;
import org.cometd.annotation.server.AnnotationCometDServlet;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.server.AbstractServerTransport;

import javax.servlet.ServletException;

public class DXFeedCometDServlet extends AnnotationCometDServlet {

    public static final String DEBUG_SESSIONS_PROPERTY = "debugSessions";
    public static final String MAX_QUEUE_PROPERTY = AbstractServerTransport.MAX_QUEUE_OPTION;

    private static final int DEFAULT_MAX_QUEUE_SIZE = SystemProperties.getIntProperty(
        DataService.class, MAX_QUEUE_PROPERTY, 100_000, 1, 1_000_000);

    private CometDMonitoring monitoring;

    @Override
    public void init() throws ServletException {
        super.init();

        String name = getServletName();
        if (getInitParameter(MonitoringEndpoint.NAME_PROPERTY) != null) {
            name = getInitParameter(MonitoringEndpoint.NAME_PROPERTY);
        }
        boolean debug = false;
        if (getInitParameter(DEBUG_SESSIONS_PROPERTY) != null) {
            debug = Boolean.parseBoolean(getInitParameter(DEBUG_SESSIONS_PROPERTY));
        }
        BayeuxServer server = (BayeuxServer) getServletContext().getAttribute(BayeuxServer.ATTRIBUTE);

        // Initialize max queue size: servlet init parameter has higher priority than system property
        if (server.getOption(MAX_QUEUE_PROPERTY) == null) {
            server.setOption(MAX_QUEUE_PROPERTY, DEFAULT_MAX_QUEUE_SIZE);
        }

        monitoring = new CometDMonitoring();
        monitoring.init(name, server, debug);
    }

    @Override
    public void destroy() {
        monitoring.destroy();
        super.destroy();
    }
}
