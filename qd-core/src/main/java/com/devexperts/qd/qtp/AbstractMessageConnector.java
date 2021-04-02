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
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.transport.stats.EndpointStats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractMessageConnector implements MessageConnector {
    protected Logging log;

    private ApplicationConnectionFactory factory;

    private final EndpointStats closedConnectionsStats = new EndpointStats();
    private final List<MessageConnectorListener> messageConnectorListeners = new CopyOnWriteArrayList<>();

    private QDStats stats;
    private long reconnectDelay = QTPConstants.RECONNECT_DELAY;
    private int threadPriority = Thread.NORM_PRIORITY;
    private volatile boolean restarting;
    private volatile EndpointStats endpointStatsSnapshot = new EndpointStats();

    /**
     * Constructs new abstract message connector.
     *
     * @param factory application connection factory to use
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    protected AbstractMessageConnector(ApplicationConnectionFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        // Let's check if factory was explicitly named, and if not assign our default name to it
        if (factory.getConfiguration(ApplicationConnectionFactory.NAME) == null) {
            factory = factory.clone();
            factory.setConfiguration(ApplicationConnectionFactory.NAME, getDefaultName(factory));
        }
        this.factory = factory;
        this.log = getLoggingInternal(getName());
    }

    private String getDefaultName(ApplicationConnectionFactory factory) {
        String type = getClass().getSimpleName().replace('$', '-'); // dashes will look nicer in name
        String suffix = "Connector";
        if (type.endsWith(suffix))
            type = type.substring(0, type.length() - suffix.length());
        return type + "-" + factory.toString();
    }

    /**
     * Returns name of this connector.
     * @return name of this connector
     * @see #getName()
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Returns short string description of this connector for management and logging purposes.
     * The result of this method is equal to the {@link ApplicationConnectionFactory#NAME name}
     * of the corresponding {@link ApplicationConnectionFactory}.
     * This description may be manually overridden to arbitrary value via {@link #setName} method.
     *
     * @return short string description of this connector for management and logging purposes
     * @see #setName(String)
     */
    @Override
    public String getName() {
        return factory.getConfiguration(ApplicationConnectionFactory.NAME);
    }

    /**
     * Overrides name of this connector for management and logging purposes.
     * This method changes the {@link ApplicationConnectionFactory#NAME name}
     * of the corresponding {@link ApplicationConnectionFactory}.
     *
     * @param name connector name to set; it may be {@code null} in which case default
     * autogenerated name will be used.
     * @see #getName()
     */
    @Override
    @MessageConnectorProperty("Name of this connector")
    public synchronized void setName(String name) {
        if (name == null)
            name = getDefaultName(factory);
        if (!getName().equals(name)) {
            log = getLoggingInternal(name);
            log.info("Setting name=" + name);
            factory = factory.clone();
            factory.setConfiguration(ApplicationConnectionFactory.NAME, name);
            reconfigure();
        }
    }

    @Override
    public synchronized String getUser() {
        return factory.getConfiguration(MessageConnectors.USER_CONFIGURATION_KEY);
    }

    @Override
    @MessageConnectorProperty("User login name")
    public synchronized void setUser(String user) {
        if (!user.equals(getUser())) { // also checks for null
            factory = factory.clone();
            factory.setConfiguration(MessageConnectors.USER_CONFIGURATION_KEY, user);
            reconfigure();
        }
    }

    @Override
    public synchronized String getPassword() {
        return factory.getConfiguration(MessageConnectors.PASSWORD_CONFIGURATION_KEY);
    }

    @Override
    @MessageConnectorProperty("User password")
    public synchronized void setPassword(String password) {
        if (!password.equals(getPassword())) { // also checks for null
            factory = factory.clone();
            factory.setConfiguration(MessageConnectors.PASSWORD_CONFIGURATION_KEY, password);
            reconfigure();
        }
    }

    @Override
    public ApplicationConnectionFactory getFactory() {
        return factory;
    }

    @Override
    public void setFactory(ApplicationConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long getReconnectDelay() {
        return reconnectDelay;
    }

    @Override
    @MessageConnectorProperty("Delay between reconnection attempts in milliseconds")
    public synchronized void setReconnectDelay(long reconnectDelay) {
        if (this.reconnectDelay != reconnectDelay) {
            log.info("Setting reconnectDelay=" + reconnectDelay);
            this.reconnectDelay = reconnectDelay;
            reconfigure();
        }
    }

    @Override
    public QDStats getStats() {
        QDStats stats = this.stats;
        return stats == null ? QDStats.VOID : stats;
    }

    @Override
    public synchronized void setStats(QDStats stats) {
        if (this.stats != null)
            throw new IllegalStateException("Stats are already initialized. You may change them only before start.");
        if (stats == null)
            throw new NullPointerException("stats is null");
        this.stats = stats;
    }

    @Override
    public abstract void start();

    protected abstract Joinable stopImpl();

    public interface Joinable {
        public void join() throws InterruptedException;
    }

    /**
     * Invoked by handler to notify about associated connector about handler's death.
     * <p>
     * Recommended implementation template:<br>
     * - check if handler is still actual (beware of asynchronous execution) and cut-out local handler reference<br>
     * - restart processing (if needed)
     *
     * @param handler - the notifying handler
     */
    protected void handlerClosed(AbstractConnectionHandler handler) {}

    @Override
    public final void stop() {
        stopImpl();
    }

    @Override
    public final void stopAndWait() throws InterruptedException {
        Joinable j = stopImpl();
        if (j != null)
            j.join();
    }

    @Override
    public synchronized void restart() {
        restarting = true;
        try {
            restartImpl(true);
        } finally {
            restarting = false;
            notifyMessageConnectorListeners();
        }
    }

    @Override
    public synchronized void reconnect() {
        restarting = true;
        try {
            restartImpl(false);
        } finally {
            restarting = false;
            notifyMessageConnectorListeners();
        }
    }

    /**
     * Restart logic implementation; If <var>fullStop</var> is <code>false</code>, connection context
     * (like last chosen address for multi-host connections) may be preserved.
     * @param fullStop
     */
    protected void restartImpl(boolean fullStop) {
        stop();
        start();
    }

    protected synchronized void reconfigure() {
        factory.reinitConfiguration();
        if (isActive())
            restart();
    }

    @Override
    public int getThreadPriority() {
        return threadPriority;
    }

    @Override
    @MessageConnectorProperty("Priority for threads associated with this connector")
    public synchronized void setThreadPriority(int priority) {
        if (this.threadPriority != priority) {
            log.info("Setting threadPriority=" + priority);
            this.threadPriority = priority;
            reconfigure();
        }
    }

    @Override
    public String getEndpointStats() {
        return new EndpointStats(retrieveCompleteEndpointStats(), endpointStatsSnapshot).toString();
    }

    @Override
    public void resetEndpointStats() {
        endpointStatsSnapshot = new EndpointStats(retrieveCompleteEndpointStats());
    }

    @Override
    public synchronized EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = new EndpointStats();
        stats.addEndpointStats(closedConnectionsStats);
        return stats;
    }

    @Override
    public long getClosedConnectionCount() {
        return closedConnectionsStats.getClosedConnectionCount();
    }

    public synchronized void addClosedConnectionStats(ConnectionStats stats) {
        closedConnectionsStats.addClosedConnectionCount(1);
        closedConnectionsStats.addConnectionStats(stats);
    }

    @Override
    public void addMessageConnectorListener(MessageConnectorListener listener) {
        if (listener == null)
            throw new NullPointerException();
        messageConnectorListeners.add(listener);
    }

    @Override
    public void removeMessageConnectorListener(MessageConnectorListener listener) {
        messageConnectorListeners.remove(listener);
    }

    public void notifyMessageConnectorListeners() {
        if (restarting)
            return; // do not notify while restarting is in process.
        for (MessageConnectorListener listener : messageConnectorListeners)
            try {
                listener.stateChanged(this);
            } catch (Throwable t) {
                log.error("Error in MessageConnectorListener", t);
            }
    }

    /**
     * Returns {@link Logging} instance that is tied to this MessageConnector and
     * includes this connector's name into the logs.
     */
    public Logging getLogging() {
        return log;
    }

    private static Logging getLoggingInternal(String name) {
        return Logging.getLogging(MessageConnector.class.getName() + "." + name);
    }
}
