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

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.qtp.auth.BasicAuthRealmFactory;
import com.devexperts.qd.qtp.auth.BasicLoginHandler;
import com.devexperts.qd.qtp.auth.BasicLoginHandlerFactory;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDAuthRealmFactory;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.auth.QDLoginHandlerFactory;
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.Services;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * QTP Connection protocol implementation.
 * This class is public to ensure that configured user and password are always accessible.
 */
public class MessageAdapterConnectionFactory extends ApplicationConnectionFactory {
    // Note, that the names of properties are legacy (they were initially a part of com.devexperts.connector.proto package)
    private static final TimePeriod DEFAULT_HEARTBEAT_PERIOD = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.connector.proto.heartbeatPeriod", "10s"));
    private static final TimePeriod DEFAULT_HEARTBEAT_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.connector.proto.heartbeatTimeout", "2m"));
    private static final TimePeriod INITIAL_HEARTBEAT_PERIOD = TimePeriod.valueOf(
        SystemProperties.getProperty(MessageAdapterConnection.class, "initialHeartbeatPeriod", ".5s"));

    private MessageAdapter.ConfigurableFactory factory;

    private String user = "";
    private String password = "";

    private QDAuthRealm authRealm;
    private QDLoginHandler loginHandler;

    private TimePeriod heartbeatPeriod = DEFAULT_HEARTBEAT_PERIOD;
    private TimePeriod heartbeatTimeout = DEFAULT_HEARTBEAT_TIMEOUT;
    private TimePeriod initialHeartbeatPeriod = INITIAL_HEARTBEAT_PERIOD;

    @SuppressWarnings("unchecked")
    public <T> T getEndpoint(Class<T> endpointClass) {
        return factory.getEndpoint(endpointClass);
    }

    MessageAdapterConnectionFactory(MessageAdapter.ConfigurableFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        this.factory = factory;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        MessageAdapter.Factory factory = getMessageAdapterFactory();
        MessageAdapter adapter;
        Socket socket = transportConnection.variables().get(MessageConnectors.SOCKET_KEY);
        QDStats stats = transportConnection.variables().get(MessageConnectors.STATS_KEY);
        if (stats == null)
            stats = QDStats.VOID;
        if (socket != null && factory instanceof SocketMessageAdapterFactory)
            adapter = ((SocketMessageAdapterFactory) factory).createAdapterWithSocket(socket, stats);
        else
            adapter = factory.createAdapter(stats);
        adapter.setConnectionVariables(transportConnection.variables());
        adapter.setLoginHandler(loginHandler);
        adapter.setAuthRealm(authRealm);
        transportConnection.variables();
        return new MessageAdapterConnection(adapter, this, transportConnection);
    }

    MessageAdapter.ConfigurableFactory getMessageAdapterFactory() {
        return factory;
    }

    @Override
    public ApplicationConnectionFactory clone() {
        MessageAdapterConnectionFactory clone = (MessageAdapterConnectionFactory) super.clone();
        clone.factory = factory.clone();
        return clone;
    }

    @Override
    public Set<ConfigurationKey<?>> supportedConfiguration() {
        Set<ConfigurationKey<?>> set = new LinkedHashSet<>(super.supportedConfiguration());
        set.addAll(factory.supportedConfiguration());
        return set;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(ConfigurationKey<T> key) {
        if (super.supportedConfiguration().contains(key))
            return super.getConfiguration(key);
        return factory.getConfiguration(key);
    }

    @Override
    public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) {
        if (super.supportedConfiguration().contains(key))
            return super.setConfiguration(key, value);
        return factory.setConfiguration(key, value);
    }

    public String getUser() {
        return user;
    }

    @Configurable(description = "user name for basic authentication")
    public void setUser(String user) {
        if (user == null)
            throw new NullPointerException();
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    @Configurable(description = "password for basic authentication")
    public void setPassword(String password) {
        if (password == null)
            throw new NullPointerException();
        this.password = password;
    }

    @Configurable(description = "Login plugin")
    public void setLogin(String login) {
        for (QDLoginHandlerFactory factory : Services.createServices(QDLoginHandlerFactory.class, null)) {
            this.loginHandler = factory.createLoginHandler(login, this);
            if (this.loginHandler != null)
                return;
        }
        this.loginHandler = BasicLoginHandlerFactory.INSTANCE.createLoginHandler(login, this);
    }

    public QDLoginHandler getLogin() {
        return loginHandler;
    }

    @Configurable(description = "Auth plugin")
    public void setAuth(String auth) {
        for (QDAuthRealmFactory factory : Services.createServices(QDAuthRealmFactory.class, null)) {
            this.authRealm = factory.createAuthRealm(auth, this);
            if (this.authRealm != null)
                return;
        }
        this.authRealm = BasicAuthRealmFactory.INSTANCE.createAuthRealm(auth, this);
    }

    public QDAuthRealm getAuth() {
        return authRealm;
    }

    @Override
    public void reinitConfiguration() {
        if (authRealm != null && loginHandler != null)
            throw new InvalidFormatException("Cannot have both auth and login set");
        if (authRealm == null && loginHandler == null && !user.isEmpty() && !password.isEmpty())
            this.loginHandler = new BasicLoginHandler(user, password);
    }

    /**
     * Returns heartbeat period for this application protocol.
     * @return heartbeat period for this application protocol
     */
    public TimePeriod getHeartbeatPeriod() {
        return heartbeatPeriod;
    }

    @Configurable(description = "Long-term heartbeat period for this connection")
    public void setHeartbeatPeriod(TimePeriod heartbeatPeriod) {
        if (heartbeatPeriod.getTime() <= 0)
            throw new IllegalArgumentException("cannot be negative or zero");
        this.heartbeatPeriod = heartbeatPeriod;
    }

    public TimePeriod getInitialHeartbeatPeriod() {
        return initialHeartbeatPeriod;
    }

    @Configurable(description = "Initial heartbeat period for this connection")
    public void setInitialHeartbeatPeriod(TimePeriod initialHeartbeatPeriod) {
        if (initialHeartbeatPeriod.getTime() <= 0)
            throw new IllegalArgumentException("cannot be negative or zero");
        this.initialHeartbeatPeriod = initialHeartbeatPeriod;
    }

    /**
     * Returns heartbeat timeout for this application protocol.
     * @return heartbeat timeout for this application protocol
     */
    public TimePeriod getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    @Configurable(description = "heartbeat timeout for this connection")
    public void setHeartbeatTimeout(TimePeriod heartbeatTimeout) {
        if (heartbeatTimeout.getTime() <= 0)
            throw new IllegalArgumentException("cannot be negative or zero");
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public String toString() {
        return factory.toString();
    }
}
