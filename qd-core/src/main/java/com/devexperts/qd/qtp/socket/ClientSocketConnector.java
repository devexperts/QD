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
package com.devexperts.qd.qtp.socket;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.codec.CodecFactory;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

import java.util.Arrays;
import java.util.Objects;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * The <code>ClientSocketConnector</code> handles standard client socket using blocking API.
 */
@MessageConnectorSummary(
    info = "Connects to some host using TCP/IP client socket.",
    addressFormat = "<host>:<port>"
)
public class ClientSocketConnector extends AbstractMessageConnector
    implements SocketHandler.CloseListener, ClientSocketConnectorMBean
{
    private static final String AUTO_STRIPE_CONFIG = "auto";

    protected String host;
    protected int port;
    protected String proxyHost = SystemProperties.getProperty("https.proxyHost", "");
    protected int proxyPort = SystemProperties.getIntProperty("https.proxyPort", 80);
    protected ConnectOrder connectOrder;
    protected SymbolStriper striper = MonoStriper.INSTANCE; // never null
    protected String stripeConfig;
    protected boolean useTls;
    protected TrustManager trustManager;

    protected volatile boolean active;
    protected volatile SocketHandler[] handlers;

    /**
     * Creates new client socket connector.
     *
     * @param factory message adapter factory to use
     * @param host host to connect to
     * @param port TCP port to connect to
     * @throws NullPointerException if {@code factory} or {@code host} is {@code null}
     * @deprecated use {@link #ClientSocketConnector(ApplicationConnectionFactory, String, int)}
     */
    @Deprecated
    public ClientSocketConnector(MessageAdapter.Factory factory, String host, int port) {
        this(MessageConnectors.applicationConnectionFactory(factory), host, port);
    }

    /**
     * Creates new client socket connector.
     *
     * @param factory application connection factory to use
     * @param host host to connect to
     * @param port TCP port to connect to
     * @throws NullPointerException if {@code factory} or {@code host} is {@code null}
     */
    public ClientSocketConnector(ApplicationConnectionFactory factory, String host, int port) {
        super(factory);
        if (host == null)
            throw new NullPointerException();
        QDConfig.setDefaultProperties(this, ClientSocketConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, ClientSocketConnectorMBean.class, ClientSocketConnector.class.getName());
        this.host = host;
        this.port = port;
    }

    @Override
    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public String getHost() {
        return host;
    }

    /**
     * Changes connection host string and restarts connector
     * if new host string is different from the old one and the connector was running.
     */
    @Override
    public synchronized void setHost(String host) {
        if (!host.equals(this.host)) { // also checks for null
            log.info("Setting host=" + LogUtil.hideCredentials(host));
            this.host = host;
            reconfigure();
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    /**
     * Changes connection port and restarts connector
     * if new port is different from the old one and the connector was running.
     */
    @Override
    public synchronized void setPort(int port) {
        if (port != this.port) {
            log.info("Setting port=" + port);
            this.port = port;
            reconfigure();
        }
    }

    @Override
    public String getProxyHost() {
        return proxyHost;
    }

    @Override
    @MessageConnectorProperty("HTTP proxy host name")
    public synchronized void setProxyHost(String proxyHost) {
        if (!proxyHost.equals(this.proxyHost)) { // also checks for null
            log.info("Setting proxyHost=" + LogUtil.hideCredentials(proxyHost));
            this.proxyHost = proxyHost;
            reconfigure();
        }
    }

    @Override
    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    @MessageConnectorProperty("HTTP proxy port")
    public synchronized void setProxyPort(int proxyPort) {
        if (proxyPort != this.proxyPort) {
            log.info("Setting proxyPort=" + proxyPort);
            this.proxyPort = proxyPort;
            reconfigure();
        }
    }

    @Override
    public ConnectOrder getConnectOrder() {
        return connectOrder;
    }

    @Override
    @MessageConnectorProperty(
        "Order of considering specified server addresses during connect/reconnect: " +
        "\"shuffle\" (default), \"random\", \"ordered\", \"priority\"")
    public synchronized void setConnectOrder(ConnectOrder connectOrder) {
        if (!Objects.equals(connectOrder, this.connectOrder)) {
            log.info("Setting connectOrder=" + connectOrder);
            this.connectOrder = connectOrder;
            reconfigure();
        }
    }

    @Override
    public String getStripe() {
        return stripeConfig;
    }

    @Override
    @MessageConnectorProperty(
        "Symbol striper (e.g. \"byhash4\"), or \"auto\" for using system default striping. " +
        "Empty (by default) means no striping (e.g. \"by1\" striper).")
    public synchronized void setStripe(String stripeConfig) {
        if (!Objects.equals(stripeConfig, this.stripeConfig)) {
            log.info("Setting stripe=" + stripeConfig);

            final SymbolStriper striper;
            if (stripeConfig != null && !stripeConfig.isEmpty() && !stripeConfig.equals(AUTO_STRIPE_CONFIG)) {
                striper = SymbolStriper.valueOf(findDataScheme(), stripeConfig);
                if (striper == null)
                    throw new IllegalArgumentException("Unknown striper: " + stripeConfig);
            } else {
                // Reset striper to empty, including "auto" config.
                // If striper is "auto" it will be resolved on connector's start.
                striper = MonoStriper.INSTANCE;
            }

            this.stripeConfig = stripeConfig;
            this.striper = striper;
            reconfigure();
        }
    }

    @Deprecated
    public boolean getTls() {
        return useTls;
    }

    @MessageConnectorProperty(
        value = "Use SSLConnectionFactory",
        deprecated = "Use tls or ssl codec in address string. For example tls+<address>"
    )
    @Deprecated
    public synchronized void setTls(boolean useTls) {
        if (this.useTls != useTls) {
            if (useTls) {
                CodecFactory sslCodecFactory = Services.createService(CodecFactory.class, null,
                    "com.devexperts.connector.codec.ssl.SSLCodecFactory");
                if (sslCodecFactory == null) {
                    log.error("SSLCodecFactory is not found. Using the SSL protocol is not supported");
                    return;
                }
                setFactory(sslCodecFactory.createCodec("ssl", getFactory()));
            } else {
                CodecConnectionFactory sslFactory = (CodecConnectionFactory) getFactory();
                if (!sslFactory.getClass().getSimpleName().contains("SSLCodecFactory")) {
                    log.error("SSLCodecFactory not found. SSL protocol is not used");
                    return;
                }
                setFactory(sslFactory.getDelegate());
            }
            this.useTls = useTls;
            log.info("Setting useTls=" + useTls);
            reconfigure();
        }
        log.warn("WARNING: DEPRECATED use \"setTls()\" method from program or \"tls\" property from address string. " +
            "Use tls or ssl codec in address string. For example tls+<address>");
    }

    @Deprecated
    public TrustManager getTrustManager() {
        return trustManager;
    }

    /**
     * Sets the custom {@link TrustManager trust manager} to be used by {@link SSLSocketFactory}.
     * This property has effect only when the connector is configured to use {@link #setTls(boolean) TLS}.
     *
     * @param trustManager trust manager to use instead of the default one, or {@code null} in order to use default one.
     */
    @Deprecated
    public void setTrustManager(TrustManager trustManager) {
        log.warn("WARNING: DEPRECATED use \"setTrustManager()\" method on ClientSocketConnector. " +
            "Use this method on SSL codec or in address string. For example tls+<address>");
        ApplicationConnectionFactory factory = getFactory();
        if (factory instanceof CodecConnectionFactory) {
            factory = factory.clone();
            ((CodecConnectionFactory) factory).setTrustManager(trustManager);
            setFactory(factory);
            reconfigure();
        }
    }

    /**
     * Sets stats for this connector. Stats should be of type {@link QDStats.SType#CLIENT_SOCKET_CONNECTOR} or
     * a suitable substitute. This method may be invoked only once.
     * @throws IllegalStateException if already set.
     */
    @Override
    public void setStats(QDStats stats) {
        super.setStats(stats);
        stats.addMBean("ClientSocketConnector", this);
    }

    @Override
    public boolean isActive() {
        return active; // Atomic read
    }

    @Override
    public MessageConnectorState getState() {
        SocketHandler[] handlers = this.handlers; // Atomic read.
        if (handlers == null)
            return MessageConnectorState.DISCONNECTED;
        for (SocketHandler handler : handlers) {
            if (handler.isConnected())
                return MessageConnectorState.CONNECTED;
        }
        return MessageConnectorState.CONNECTING;
    }

    @Override
    public int getConnectionCount() {
        SocketHandler[] handlers = this.handlers; // Atomic read.
        if (handlers == null)
            return 0;
        return (int) Arrays.stream(handlers).filter(SocketHandler::isConnected).count();
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        SocketHandler[] handlers = this.handlers; // Atomic read.
        if (handlers != null) {
            Arrays.stream(handlers)
                .map(SocketHandler::getActiveConnectionStats)
                .filter(Objects::nonNull)
                .forEach(connectionStats -> {
                    stats.addActiveConnectionCount(1);
                    stats.addConnectionStats(connectionStats);
                });
        }
        return stats;
    }

    @Override
    public synchronized void start() {
        if (isActive())
            return;

        log.info("Starting ClientSocketConnector to " + LogUtil.hideCredentials(getAddress()));
        // create default stats instance if specific one was not provided.
        if (getStats() == null)
            setStats(QDFactory.createStats(QDStats.SType.CLIENT_SOCKET_CONNECTOR, null));

        boolean isSameStriper = true;
        if (Objects.equals(stripeConfig, AUTO_STRIPE_CONFIG)) {
            SymbolStriper newStriper = findCollectorSymbolStriper();
            isSameStriper = (this.striper == newStriper);
            this.striper = newStriper;
        }
        int stripeCount = striper.getStripeCount();

        SocketHandler[] oldHandlers = handlers; // Atomic read
        SocketHandler[] newHandlers = new SocketHandler[stripeCount];

        // Reuse old handler only on reconnect if striper has not changed
        boolean reuseSource = (oldHandlers != null && oldHandlers.length == stripeCount && isSameStriper);
        // Initialize handlers (publish the array with non-null elements)
        for (int i = 0; i < stripeCount; i++) {
            QDFilter stripeFilter = (stripeCount > 1) ? striper.getStripeFilter(i) : null;
            newHandlers[i] = reuseSource ?
                SocketHandler.createFromClosed(oldHandlers[i]) :
                new SocketHandler(this, new ClientSocketSource(this), stripeFilter);
            newHandlers[i].setCloseListener(this);
        }

        handlers = newHandlers; // Atomic write
        active = true; // Atomic write.

        // Start handlers (may fail)
        for (int i = 0; i < stripeCount; i++) {
            newHandlers[i].start();
        }
    }

    @Override
    protected synchronized Joinable stopImpl() {
        return stopImpl(true);
    }

    private Joinable stopImpl(boolean fullStop) {
        if (!isActive())
            return null;
        active = false; // Atomic write

        log.info("Stopping ClientSocketConnector");

        SocketHandler[] oldHandlers = handlers; // Atomic read
        if (oldHandlers == null)
            return null;
        if (fullStop)
            handlers = null;

        Arrays.stream(oldHandlers).forEach(SocketHandler::close);
        return () -> {
            for (SocketHandler handler : oldHandlers) {
                handler.join();
            }
        };
    }

    @Override
    protected synchronized void restartImpl(boolean fullStop) {
        stopImpl(fullStop);
        start();
    }

    @Override
    public synchronized void handlerClosed(SocketHandler handler) {
        if (!isActive())
            return;

        SocketHandler[] handlers = this.handlers; // Atomic read
        if (handlers == null)
            return;

        for (int i = 0; i < handlers.length; i++) {
            if (handlers[i] == handler) {
                handlers[i] = SocketHandler.createFromClosed(handler);
                handlers[i].setCloseListener(this);
                handlers[i].start();
                break;
            }
        }
    }

    @Override
    public String getCurrentHost() {
        // Do not show host for stopped or striped connector
        SocketHandler handler = findSingleHandler(handlers); // Atomic read
        return (handler != null) ? handler.getHost() : "";
    }

    @Override
    public int getCurrentPort() {
        // Do not show port for stopped or striped connector
        SocketHandler handler = findSingleHandler(handlers); // Atomic read
        return (handler != null) ? handler.getPort() : 0;
    }

    @Override
    public String[] getCurrentAddresses() {
        SocketHandler[] handlers = this.handlers; // Atomic read
        if (handlers == null)
            return new String[0];
        return Arrays.stream(handlers)
            .map(SocketHandler::getCurrentAddress)
            .toArray(String[]::new);
    }

    // Utility methods

    private SocketHandler findSingleHandler(SocketHandler[] handlers) {
        return (handlers != null && handlers.length == 1) ? handlers[0] : null;
    }

    private DataScheme findDataScheme() {
        //noinspection resource
        return lookupQDEndpoint().getScheme();
    }

    private SymbolStriper findCollectorSymbolStriper() {
        //noinspection resource
        for (QDCollector collector : lookupQDEndpoint().getCollectors()) {
            //TODO When QD-86 is implemented with "[stripe=auto,stripeContract=true]"
            // use individual stripes per contract
            if (collector != null) {
                return collector.getStriper();
            }
        }
        return MonoStriper.INSTANCE;
    }

    private QDEndpoint lookupQDEndpoint() {
        MessageAdapter.Factory factory = MessageConnectors.retrieveMessageAdapterFactory(getFactory());
        if (!(factory instanceof MessageAdapter.ConfigurableFactory))
            throw new IllegalArgumentException("Unsupported application connection class: " + factory);

        QDEndpoint endpoint = ((MessageAdapter.ConfigurableFactory) factory).getEndpoint(QDEndpoint.class);
        if (endpoint == null)
            throw new IllegalArgumentException("Application connection created without QDEndpoint: " + factory);

        return endpoint;
    }
}
