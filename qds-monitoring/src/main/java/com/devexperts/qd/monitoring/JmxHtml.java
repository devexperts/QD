/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.net.*;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import com.devexperts.management.Management;
import com.devexperts.qd.QDLog;
import com.sun.jdmk.comm.*;

/**
 * Separate class so that <code>HtmlAdaptorServer</code> class is loaded only when needed.
 */
class JmxHtml {
    static JmxConnector init(Properties props) {
        Integer port = Integer.decode(props.getProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY));
        InetAddress bindAddress = tryResolve(props.getProperty(JMXEndpoint.JMX_HTML_BIND_PROPERTY));
        boolean ssl = props.getProperty(JMXEndpoint.JMX_HTML_SSL_PROPERTY) != null;
        String auth = props.getProperty(JMXEndpoint.JMX_HTML_AUTH_PROPERTY);

        if (!JmxConnectors.isPortAvailable(port))
            return null;

        String name = "com.devexperts.qd.monitoring:type=HtmlAdaptor,port=" + port;
        HtmlAdaptorServer server = createHtmlAdaptor(bindAddress, ssl);
        Connector connector = new Connector(name, server);
        if (!JmxConnectors.addConnector(port, connector))
            return null; // port is already taken

        server.setPort(port);
        if (auth != null && auth.length() > 0)
            for (StringTokenizer st = new StringTokenizer(auth, ","); st.hasMoreTokens();) {
                String[] info = st.nextToken().split(":", 2);
                if (info.length != 2)
                    QDLog.log.error(JMXEndpoint.JMX_HTML_AUTH_PROPERTY + " should contain comma-separated list of <login>:<password> pairs");
                else
                    server.addUserAuthenticationInfo(new AuthInfo(info[0], info[1]));
            }

        connector.setRegistration(Management.registerMBean(server, null, name));
        server.start();
        QDLog.log.info("HTML management port is " + port + (ssl ? " [SSL]" : "") +
            (bindAddress != null ? " bound to " + bindAddress : ""));
        return connector;
    }

    private static InetAddress tryResolve(String bind) {
        if (bind != null && bind.length() > 0)
            try {
                return InetAddress.getByName(bind);
            } catch (UnknownHostException e) {
                QDLog.log.error("Could not resolve bind address, will use unbound socket", e);
            }
        return null;
    }

    private static HtmlAdaptorServer createHtmlAdaptor(final InetAddress bindAddress, boolean ssl) {
        HtmlAdaptorServer server = new HtmlAdaptorServer();
        final ServerSocketFactory ssf = ssl ? SSLServerSocketFactory.getDefault() : null;
        if (bindAddress != null || ssf != null)
            try {
                final Field field = HtmlAdaptorServer.class.getDeclaredField("sockListen");
                field.setAccessible(true);
                field.set(server, null); // Invoke now to truly test accessibility.
                // All preparations are ok - override adaptor with our own.
                server = new HtmlAdaptorServer() {
                    @Override
                    protected void doBind() throws CommunicationException, InterruptedException {
                        try {
                            field.set(this,
                                ssf != null ? ssf.createServerSocket(getPort(), 2 * getMaxActiveClientCount(), bindAddress) :
                                    new ServerSocket(getPort(), 2 * getMaxActiveClientCount(), bindAddress));
                        } catch (SocketException e) {
                            if (e.getMessage().equals("Interrupted system call"))
                                throw new InterruptedException(e.toString());
                            throw new CommunicationException(e);
                        } catch (InterruptedIOException e) {
                            throw new InterruptedException(e.toString());
                        } catch (IOException e) {
                            throw new CommunicationException(e);
                        } catch (IllegalAccessException e) {
                            super.doBind();
                        }
                    }
                };
            } catch (NoSuchFieldException e) {
                QDLog.log.error("Could not resolve socket field, will use default socket", e);
            } catch (IllegalAccessException e) {
                QDLog.log.error("Could not access socket field, will use default socket", e);
            }
        return server;
    }

    private static class Connector extends JmxConnector {
        private final HtmlAdaptorServer server;

        Connector(String name, HtmlAdaptorServer server) {
            super(name);
            this.server = server;
        }

        @Override
        public void stop() throws IOException {
            super.stop();
            server.stop();
        }
    }
}
