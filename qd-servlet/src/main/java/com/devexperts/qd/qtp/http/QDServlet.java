/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.http;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.*;

import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.Services;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.TypedMap;

public class QDServlet extends HttpServlet {
    private QDServletConfig config;
    private MessageAdapter.Factory messageAdapterFactory;
    private QDStats stats;

    public void init() throws ServletException {
        config = Services.createService(QDServletConfig.class, null,
            getInitParameter(QDServletConfig.class.getName()));
        if (config == null)
            throw new ServletException("Cannot find instance of " + QDServletConfig.class);
        messageAdapterFactory = config.getMessageAdapterFactory();
        if (messageAdapterFactory == null)
            throw new ServletException("Cannot get MessageAdapterFactory");
        stats = config.getStats();
        if (stats == null)
            throw new ServletException("Cannot get Stats");
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletOutputStream out = response.getOutputStream();
        out.println("<html><title>QDServlet</title>");
        out.println("<body>This servlet is not supposed to be used manually. Use QD HttpConnector.</body></html>");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            boolean newSession = "true".equalsIgnoreCase(request.getHeader(HttpConnector.NEW_CONNECTION_HTTP_PROPERTY));
            HttpSession session = request.getSession(newSession);
            if (session == null)
                throw new ServletException("Session is not found");
            String connectionId = request.getParameter(HttpConnector.CONNECTION_ID_PARAMETER);
            if (connectionId == null)
                connectionId = HttpConnector.DEFAULT_CONNECTION_ID;
            QDServletConnection con = (QDServletConnection) session.getAttribute(connectionId);
            if (con == null && !newSession)
                throw new ServletException("Connection " + connectionId + " was lost");
            if (newSession) {
                String connectionName =
                    "session=" + JMXNameBuilder.quoteKeyPropertyValue(session.getId()) + "," +
                    "connection=" + JMXNameBuilder.quoteKeyPropertyValue(connectionId) + "," +
                    "host=" + JMXNameBuilder.quoteKeyPropertyValue(request.getRemoteAddr());
                if (con != null)
                    con.close("it is being overwritten with new " + connectionName);
                QDStats stats = this.stats.getOrCreate(QDStats.SType.CONNECTIONS).create(
                    QDStats.SType.CONNECTION, connectionName);
                MessageAdapter adapter = messageAdapterFactory.createAdapter(stats);
                TypedMap connectionVariables = new TypedMap();
                adapter.setConnectionVariables(connectionVariables);
                connectionVariables.set(TransportConnection.REMOTE_HOST_ADDRESS_KEY, request.getRemoteAddr());
                session.setAttribute(connectionId, con = new QDServletConnection(
                    connectionName, adapter, stats, config));
            }
            if (!con.isValid())
                throw new ServletException(con + " is invalid because it was serialized");
            long readBytes = con.readMessages(request);
            long writtenBytes = con.writeMessages(response);
            config.addClosedConnectionStats(readBytes, writtenBytes);
        } catch (Throwable t) {
            // Our error-handling policy -- log exceptions and do not rethrow them (otherwise they get double-logged)
            failedReponse(response, t);
        }
    }

    /**
     * Writes exception stack-trace in plain text to the response stream and sets error code.
     */
    private void failedReponse(HttpServletResponse response, Throwable t) throws IOException {
        QDLog.log.error("Exception encountered while handling QDServlet request", t);
        response.reset();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("text/plain");
        t.printStackTrace(response.getWriter());
    }
}

