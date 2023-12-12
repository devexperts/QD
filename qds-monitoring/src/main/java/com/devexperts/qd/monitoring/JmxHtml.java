/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.sun.jdmk.comm.AuthInfo;
import com.sun.jdmk.comm.CommunicationException;
import com.sun.jdmk.comm.HtmlAdaptorServer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * Separate class so that <code>HtmlAdaptorServer</code> class is loaded only when needed.
 */
class JmxHtml {
    private static final Logging log = Logging.getLogging(JmxHtml.class);

    // server socket bind retry delay
    static final long BIND_RETRY_DELAY =
        TimePeriod.valueOf(SystemProperties.getProperty(JmxHtml.class, "bindRetryDelay", "10s")).getTime();

    static JmxConnector init(Properties props) {
        Integer port = Integer.decode(props.getProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY));
        InetAddress bindAddress = tryResolve(props.getProperty(JMXEndpoint.JMX_HTML_BIND_PROPERTY));
        boolean ssl = props.getProperty(JMXEndpoint.JMX_HTML_SSL_PROPERTY) != null;
        String auth = props.getProperty(JMXEndpoint.JMX_HTML_AUTH_PROPERTY);

        if (!JmxConnectors.isPortAvailable(port))
            return null;

        String name = "com.devexperts.qd.monitoring:type=HtmlAdaptor,port=" + port;
        HtmlAdaptorServer server = new JmxHtmlAdaptorServer(bindAddress, ssl);
        server.setPort(port);
        Connector connector = new Connector(port, name, server);
        if (!JmxConnectors.addConnector(connector))
            return null; // port is already taken

        if (auth != null && auth.length() > 0) {
            for (String token : auth.split(",")) {
                String[] info = token.split(":", 2);
                if (info.length != 2) {
                    log.error(JMXEndpoint.JMX_HTML_AUTH_PROPERTY +
                        " should contain comma-separated list of <login>:<password> pairs");
                } else {
                    server.addUserAuthenticationInfo(new AuthInfo(info[0], info[1]));
                }
            }
        }

        connector.setRegistration(Management.registerMBean(server, null, name));
        server.start();
        return connector;
    }

    private static InetAddress tryResolve(String bind) {
        if (bind != null && bind.length() > 0) {
            try {
                return InetAddress.getByName(bind);
            } catch (UnknownHostException e) {
                log.error("Could not resolve bind address, will use unbound socket", e);
            }
        }
        return null;
    }

    static class JmxHtmlAdaptorServer extends HtmlAdaptorServer {

        private final InetAddress bindAddress;
        private final boolean ssl;
        private Field serverSocketField;

        JmxHtmlAdaptorServer(InetAddress bindAddress, boolean ssl) {
            this.bindAddress = bindAddress;
            this.ssl = ssl;

            try {
                Field field = HtmlAdaptorServer.class.getDeclaredField("sockListen");
                field.setAccessible(true);
                field.set(this, null); // Invoke now to truly test accessibility.
                serverSocketField = field;
            } catch (NoSuchFieldException e) {
                log.error("Could not resolve socket field, will use default socket", e);
            } catch (IllegalAccessException e) {
                log.error("Could not access socket field, will use default socket", e);
            }
        }

        @Override
        protected void doBind() throws CommunicationException, InterruptedException {
            while (true) {
                try {
                    tryBind();
                    log.info("HTML management port is " + getPort() + (ssl ? " [SSL]" : "") +
                        (bindAddress != null ? " bound to " + bindAddress : ""));
                    return;
                } catch (CommunicationException e) {
                    log.error("Failed to bind HTML management port", e);
                    if (JmxHtml.BIND_RETRY_DELAY == 0)
                        return;
                    Thread.sleep(JmxHtml.BIND_RETRY_DELAY);
                }
            }
        }

        private void tryBind() throws InterruptedException {
            if (serverSocketField == null) {
                super.doBind();
                return;
            }
            ServerSocket serverSocket;
            try {
                ServerSocketFactory ssf = ssl ? SSLServerSocketFactory.getDefault() : ServerSocketFactory.getDefault();
                serverSocket = ssf.createServerSocket(getPort(), 2 * getMaxActiveClientCount(), bindAddress);
            } catch (SocketException e) {
                if (e.getMessage().equals("Interrupted system call"))
                    throw new InterruptedException(e.toString());
                throw new CommunicationException(e);
            } catch (InterruptedIOException e) {
                throw new InterruptedException(e.toString());
            } catch (IOException e) {
                throw new CommunicationException(e);
            }
            try {
                serverSocketField.set(this, serverSocket);
            } catch (IllegalAccessException e) {
                // this should never happen
                throw new CommunicationException(e, "Unexpected error");
            }
        }

        @Override
        protected void doError(Exception e) throws CommunicationException {
            log.error("HTML management adaptor initialization error", e);
            super.doError(e);
        }
    }

    private static class Connector extends JmxConnector {
        private final HtmlAdaptorServer server;

        Connector(int port, String name, HtmlAdaptorServer server) {
            super(port, name);
            this.server = server;
        }

        @Override
        public void stop() throws IOException {
            super.stop();
            server.stop();
        }
    }
}
