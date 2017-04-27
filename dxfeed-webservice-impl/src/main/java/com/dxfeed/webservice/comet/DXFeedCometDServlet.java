package com.dxfeed.webservice.comet;

import javax.servlet.ServletException;

import com.devexperts.qd.monitoring.MonitoringEndpoint;
import org.cometd.annotation.AnnotationCometDServlet;
import org.cometd.bayeux.server.BayeuxServer;

public class DXFeedCometDServlet extends AnnotationCometDServlet {

    public static final String DEBUG_SESSIONS_PROPERTY = "debugSessions";

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
            debug = Boolean.valueOf(getInitParameter(DEBUG_SESSIONS_PROPERTY));
        }
        BayeuxServer server = (BayeuxServer) getServletContext().getAttribute(BayeuxServer.ATTRIBUTE);

        monitoring = new CometDMonitoring();
        monitoring.init(name, server, debug);
    }

    @Override
    public void destroy() {
        monitoring.destroy();
        super.destroy();
    }
}
