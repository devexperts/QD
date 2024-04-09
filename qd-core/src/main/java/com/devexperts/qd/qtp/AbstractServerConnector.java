/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.util.DxTimer;
import com.devexperts.util.LogUtil;
import com.devexperts.util.MathUtil;
import com.devexperts.util.TimePeriod;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public abstract class AbstractServerConnector extends AbstractMessageConnector
    implements MessageConnector.Bindable, ServerConnectorMBean
{
    protected volatile boolean active;

    protected volatile int port;
    protected volatile InetAddress bindAddress;
    protected volatile String bindAddressString = ANY_BIND_ADDRESS;
    protected volatile int maxConnections; // 0 stands for unlimited number of connections

    private DxTimer.Cancellable postponedTask;

    /**
     * Constructs new abstract message connector.
     *
     * @param factory application connection factory to use
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    protected AbstractServerConnector(ApplicationConnectionFactory factory) {
        super(factory);
    }

    @Override
    public synchronized void start() {
        startImpl(true);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public int getLocalPort() {
        return port;
    }

    @Override
    public synchronized void setLocalPort(int port) {
        if (this.port != port) {
            log.info("Setting localPort=" + port);
            this.port = port;
            reconfigure();
        }
    }

    @Override
    public String getAddress() {
        // Perhaps we need to change the format to avoid confusion in the log.
        // Sometimes it is difficult to distinguish client connections from server connections.
        // It was changed in the jira task https://jira.in.devexperts.com/browse/QD-281
        return bindAddressString + ":" + port;
    }

    @Override
    public String getBindAddr() {
        return bindAddressString;
    }

    public InetAddress getBindInetAddress() {
        return bindAddress;
    }

    @Override
    @MessageConnectorProperty("Network interface address to bind socket to")
    public synchronized void setBindAddr(String newBindAddress) throws UnknownHostException {
        newBindAddress = Bindable.normalizeBindAddr(newBindAddress);
        if (!newBindAddress.equals(this.bindAddressString)) {
            log.info("Setting bindAddr=" + LogUtil.hideCredentials(newBindAddress));
            this.bindAddress = newBindAddress.equals(ANY_BIND_ADDRESS) ? null : InetAddress.getByName(newBindAddress);
            this.bindAddressString = newBindAddress;
            reconfigure();
        }
    }

    @Override
    public int getMaxConnections() {
        return maxConnections;
    }

    @Override
    @MessageConnectorProperty("Max number of connections allowed for connector")
    public synchronized void setMaxConnections(int maxConnections) {
        if (maxConnections != this.maxConnections) {
            log.info("Setting maxConnections=" + maxConnections);
            this.maxConnections = maxConnections;
            reconfigure();
        }
    }

    /**
     * Smooth closing a percent of connections during a specified period.
     * Optionally socket accepting can be disabled in this period.
     *
     * @param percent of connections which will be closed gracefully
     * @param period period in TimePeriod format
     * @param stopAcceptor true if acceptor must be disabled for a specified period
     * @return message with invocation result
     */
    @Override
    public synchronized String closeConnections(double percent, String period, boolean stopAcceptor) {
        log.info("Close connections percent=" + MathUtil.formatDouble(percent, 2) +
            ", period=" + period + ", stopAcceptor=" + stopAcceptor);

        if (!isActive()) {
            return "All connections has already been closed";
        }

        TimePeriod timePeriod = TimePeriod.valueOf(period);
        int periodMillis = Math.toIntExact(timePeriod.getTime());
        if (periodMillis < 0) {
            throw new IllegalArgumentException("Close connections period must be positive");
        }
        if (percent <= 0.0 || percent > 100.0) {
            throw new IllegalArgumentException("Percent must be in range (0, 100]%");
        }

        List<Closeable> connections = new ArrayList<>(getConnections());
        Collections.shuffle(connections);

        int closeCount = Math.toIntExact(Math.round(connections.size() * percent / 100.0));
        List<Closeable> removeList = connections.subList(0, closeCount);
        if (removeList.isEmpty()) {
            return "There's nothing to close";
        }

        boolean schedule = stopAcceptor && stopAcceptorCompletely();
        int maxDelay = scheduleCloseConnections(removeList, periodMillis);
        if (schedule) {
            postponedTask = DxTimer.getInstance().runOnce(() -> invokeTask(this::startAcceptorInternal), maxDelay);
        }

        String message = "Scheduled close for: " + removeList.size() +
            " connections with latest delay: " + TimePeriod.valueOf(maxDelay);
        log.info(message);

        return message;
    }

    /**
     * Stopping server socket acceptor and smooth closing of all connections during a specified period
     *
     * @param period period in TimePeriod format
     * @return message with invocation result
     */
    @Override
    public synchronized String stopGracefully(String period) {
        log.info("Stop gracefully with period=" + period);

        if (!isActive()) {
            return "Server socket connector has already been stopped";
        }

        TimePeriod timePeriod = TimePeriod.valueOf(period);
        int periodMillis = Math.toIntExact(timePeriod.getTime());
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("Graceful period must be positive");
        }

        stopAcceptorCompletely();

        List<Closeable> connections = getConnections();
        int maxDelay = scheduleCloseConnections(connections, periodMillis);
        postponedTask = DxTimer.getInstance().runOnce(() -> invokeTask(this::stopImpl), maxDelay);

        String message = "Scheduled graceful stop for: " + connections.size() +
            " connections with latest delay: " + TimePeriod.valueOf(maxDelay);
        log.info(message);

        return message;
    }

    @Override
    public synchronized void stopAcceptor() {
        stopAcceptorCompletely();
    }

    private boolean stopAcceptorCompletely() {
        return cancelScheduledTask() | stopAcceptorInternal(); // both methods will invoke anyway
    }

    private synchronized void invokeTask(Runnable runnable) {
        postponedTask = null; // deactivate the link
        runnable.run();
    }

    protected abstract void startImpl(boolean startAcceptor);

    protected abstract List<Closeable> getConnections();

    protected abstract boolean stopAcceptorInternal();

    protected abstract void startAcceptorInternal();

    protected boolean cancelScheduledTask() {
        if (postponedTask == null) {
            return false;
        }
        log.info("Canceling the scheduled task");
        postponedTask.cancel();
        postponedTask = null;
        return true;
    }

    private int scheduleCloseConnections(Collection<Closeable> closeables, int period) {
        Random random = new Random();
        int maxDelay = 0;
        for (Closeable closeable: closeables) {
            int delay = random.nextInt(period);
            maxDelay = Math.max(delay, maxDelay);
            DxTimer.getInstance().runOnce(() -> {
                try {
                    closeable.close();
                } catch (IOException e) {
                    log.error("Fail to close connection", e);
                }
            }, delay);
        }
        return maxDelay;
    }
}
