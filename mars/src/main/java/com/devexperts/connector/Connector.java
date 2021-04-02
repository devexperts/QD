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
package com.devexperts.connector;

import com.devexperts.logging.Logging;

import java.net.Socket;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Connector manages single set of communication links (defined as address string).
 */
public abstract class Connector implements ConnectorMBean {
    private static final Logging log = Logging.getLogging(Connector.class);

    public static final String SYS_PROPERTY_NO_ADV_STATISTICS = "com.devexperts.connector.no-adv-stat";
    protected static final boolean isAdvancedStatEnabled = !Boolean.getBoolean(SYS_PROPERTY_NO_ADV_STATISTICS);

    private int heartbeat_period;
    private int heartbeat_timeout;
    private int reconnection_period;

    private final Random skew_random = new Random();
    private double skew_factor; // From [0, 1] interval.
    private int skew_multiplier; // From [0, 65535] interval.

    private final Map<String, SocketController> controllers = new HashMap<>();
    private boolean started;
    private String address = "";
    private ConnectorListener connectorListener;

    public Connector() {
        setReconnectionPeriod(10000);
        setSkewFactor(0.3);
    }

    public String toString() {
        return getClass().getSimpleName() + "-" + address;
    }

    // ========== ConnectorMBean Implementation ==========

    @Override
    public int getHeartbeatPeriod() {
        return heartbeat_period;
    }

    @Override
    public void setHeartbeatPeriod(int heartbeat_period) {
        if (heartbeat_period < 0) {
            throw new IllegalArgumentException("Heartbeat period is negative.");
        }
        this.heartbeat_period = heartbeat_period;
    }

    @Override
    public int getHeartbeatTimeout() {
        return heartbeat_timeout;
    }

    /**
     * The timeout must be <tt>&gt;= 0</tt>. A timeout of zero is interpreted as an infinite timeout.
     *
     * @param heartbeat_timeout timeout in ms
     */
    @Override
    public void setHeartbeatTimeout(int heartbeat_timeout) {
        if (heartbeat_timeout < 0) {
            throw new IllegalArgumentException("Heartbeat timeout is negative.");
        }
        this.heartbeat_timeout = heartbeat_timeout;
    }

    @Override
    public int getReconnectionPeriod() {
        return reconnection_period;
    }

    @Override
    public void setReconnectionPeriod(int reconnection_period) {
        if (reconnection_period < 0) {
            throw new IllegalArgumentException("Recconection period is negative.");
        }
        this.reconnection_period = reconnection_period;
    }

    @Override
    public double getSkewFactor() {
        return skew_factor;
    }

    @Override
    public void setSkewFactor(double skew_factor) {
        if (Double.isNaN(skew_factor) || skew_factor < 0 || skew_factor > 1) {
            throw new IllegalArgumentException("Skew factor is outside [0, 1] interval.");
        }
        this.skew_factor = skew_factor;
        this.skew_multiplier = (int) (skew_factor * (1 << 16));
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public synchronized void setAddress(String address) {
        address = address == null ? "" : address.trim();
        this.address = address;
        Set<String> addresses = new HashSet<>();
        for (StringTokenizer st = new StringTokenizer(address, "/"); st.hasMoreTokens();) {
            addresses.add(st.nextToken().trim());
        }
        addresses.remove("");
        // Add new controllers.
        for (String s : addresses)
            if (!controllers.containsKey(s)) {
                controllers.put(s, null);
            }
        // Remove gone controllers.
        for (Iterator<String> it = controllers.keySet().iterator(); it.hasNext();) {
            String s = it.next();
            if (addresses.contains(s)) {
                continue;
            }
            SocketController controller = controllers.get(s);
            it.remove();
            if (controller != null) {
                controller.close();
            }
        }
        // Ensure start state of all controllers.
        if (started)
            start();
    }

    @Override
    public synchronized void start() {
        started = true;
        for (String key : controllers.keySet()) {
            if (controllers.get(key) != null)
                continue;
            try {
                SocketController controller;
                if (key.charAt(0) == ':') {
                    controller = new SocketAcceptor(this, key);
                } else {
                    controller = new SocketConnector(this, key);
                }
                controllers.put(key, controller);
                controller.start();
            } catch (ParseException e) {
                log("Parsing failed key=" + key, e, null);
            }
        }
    }

    @Override
    public synchronized void stop() {
        started = false;
        for (String s : controllers.keySet()) {
            SocketController controller = controllers.put(s, null);
            if (controller != null) {
                controller.close();
            }
        }
    }

    @Override
    public synchronized void restart() {
        stop();
        start();
    }

    public ConnectorListener getConnectorListener() {
        return connectorListener;
    }

    public synchronized void setConnectorListener(ConnectorListener connectorListener) {
        this.connectorListener = connectorListener;
    }

    // ========== Internal API for Extension ==========

    /**
     * Creates and returns new {@link ConnectionAdapter} for specified {@link Socket}.<br> This method may block if
     * required.
     *
     * <p>If this method returns 'null' or throws an exception, then connection is closed.
     */
    protected abstract ConnectionAdapter createConnectionAdapter(Socket socket) throws Throwable;

    /**
     * Applies skew factor to specified period and returns result.
     */
    protected int getSkewedPeriod(int period) {
        if (period <= 0 || skew_multiplier == 0) {
            return period;
        }
        return period - (int) (((long) period * (long) skew_multiplier * (long) (skew_random.nextInt() >>> 16)) >>> 32);
    }

    /**
     * Logs specified message and error if not null.
     *
     * @param message a message to log
     * @param error   is null for information messages
     * @param state   see and use <tt>ConnectorStates</tt> for this field only or <tt>null</tt>
     */
    protected void log(String message, Throwable error, String state) {
        log(message, error, true, state);
    }

    protected void fireConnectorListenerProcessing(String message, Throwable error, String state) {
        if (connectorListener != null) {
            if (message != null) {
                if (message.charAt(0) == '[') {
                    int index = message.indexOf(']');
                    if (index != -1) {
                        message = message.substring(index + 1);
                    }
                }
            }

            if (state == null) {
                if (error == null)
                    connectorListener.info(message);
                else
                    connectorListener.errorOccured(message, error);
            } else if (ConnectorStates.DISCONNECTED_STATE.equals(state)) {
                connectorListener.connectionLost(message, error);
            } else if (ConnectorStates.ESTABLISHED_STATE.equals(state)) {
                connectorListener.connectionEstablished(message);
            } else { // unknown state
                connectorListener.errorOccured(message, error);
                log.error("Wrong unknown state value for: " + message, error);
            }
        }
    }

    /**
     * Logs specified message and error if not null.
     *
     * @param message                 info
     * @param error                   is null for information messages.
     * @param shouldBePrintStackTrace print StackTrace?
     */
    protected void log(String message, Throwable error, boolean shouldBePrintStackTrace, String state) {
        if (shouldBePrintStackTrace && error != null)
            log.error(message, error);
        else if (error != null)
            log.info(message + ": " + error);
        else
            log.info(message);
        if (isAdvancedStatEnabled)
            fireConnectorListenerProcessing(message, error, state);
    }

    /**
     * Returns string representation of specified socket address.
     *
     * <p>By default, returns <tt>"host:port"</tt> string using numeric IP address as <tt>"host"</tt>.
     */
    protected String getSocketAddress(Socket socket) {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }
}
