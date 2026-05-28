/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.dxlink.websocket.transport.DxLinkLoginHandlerFactory;
import com.devexperts.qd.dxlink.websocket.transport.TokenDxLinkLoginHandlerFactory;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.Services;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DxLink Connection protocol implementation.
 *
 * <p>JMX attribute name and system property are spelled {@code requestedAggregationPeriod}.
 * The legacy {@code acceptAggregationPeriod} JMX attribute and system property are kept
 * as deprecated aliases. The DxLink JSON wire field is still {@code acceptAggregationPeriod}
 * pending coordinated rename with the DxLink protocol team.
 */
public class DxLinkWebSocketApplicationConnectionFactory extends ApplicationConnectionFactory {
    private static final Logging log = Logging.getLogging(DxLinkWebSocketApplicationConnectionFactory.class);

    private static final Pattern ACCEPT_EVENT_FIELDS_VALIDATOR =
        Pattern.compile("^\\((\\w+\\[\\w+(,\\w+)*])(,\\w+\\[\\w+(,\\w+)*])*\\)$");
    private static final Pattern ACCEPT_EVENT_FIELDS_PARSER = Pattern.compile("(\\w+)\\[([^]]+)]");
    private static final String APPLICATION_VERSION =
        SystemProperties.getProperty("com.devexperts.qd.dxlink.applicationVersion", null);
    private static final String ACCEPT_EVENT_FIELDS =
        SystemProperties.getProperty("com.devexperts.qd.dxlink.websocket.acceptEventFields", "");
    private static final TimePeriod DEFAULT_HEARTBEAT_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.dxlink.websocket.heartbeatTimeout", "60s"));

    private static final String REQUESTED_AGGREGATION_PERIOD_PROPERTY =
        "com.devexperts.qd.dxlink.feedService.requestedAggregationPeriod";
    private static final String LEGACY_ACCEPT_AGGREGATION_PERIOD_PROPERTY =
        "com.devexperts.qd.dxlink.feedService.acceptAggregationPeriod";
    private static final TimePeriod REQUESTED_AGGREGATION_PERIOD = readRequestedAggregationPeriodProperty();

    private TimePeriod heartbeatTimeout = DEFAULT_HEARTBEAT_TIMEOUT;
    private String applicationVersion = APPLICATION_VERSION;
    private String acceptEventFields = ACCEPT_EVENT_FIELDS;
    private Map<String, List<String>> acceptEventFieldsByType = Collections.emptyMap();
    private MessageAdapter.ConfigurableFactory factory;
    private QDLoginHandler loginHandler;

    public DxLinkWebSocketApplicationConnectionFactory(MessageAdapter.ConfigurableFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        this.factory = factory;
        if (REQUESTED_AGGREGATION_PERIOD != null) {
            factory.setConfiguration(MessageConnectors.REQUESTED_AGGREGATION_PERIOD_CONFIGURATION_KEY,
                REQUESTED_AGGREGATION_PERIOD);
        }
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) {
        QDStats stats = transportConnection.variables().get(MessageConnectors.STATS_KEY);
        if (stats == null)
            stats = QDStats.VOID;

        MessageAdapter adapter = this.factory.createAdapter(stats);
        adapter.setConnectionVariables(transportConnection.variables());
        adapter.setLoginHandler(loginHandler);
        adapter.useDescribeProtocol();

        HeartbeatProcessor heartbeatProcessor = new HeartbeatProcessor(getHeartbeatTimeout().getTime());

        Delegates delegates = new Delegates(adapter.getScheme());

        return new DxLinkWebSocketApplicationConnection(
            adapter,
            this,
            transportConnection,
            new DxLinkWebSocketQTPParser(
                adapter.getScheme(),
                adapter.supportsMixedSubscription(),
                adapter.getFieldReplacer(),
                heartbeatProcessor,
                adapter,
                receiver -> new DxLinkJsonMessageParser(receiver, delegates)
            ),
            new DxLinkWebSocketQTPComposer(
                adapter.getScheme(),
                delegates,
                new DxLinkJsonMessageFactory(),
                heartbeatProcessor,
                this,
                adapter
            ),
            heartbeatProcessor
        );
    }

    @Override
    public DxLinkWebSocketApplicationConnectionFactory clone() {
        DxLinkWebSocketApplicationConnectionFactory clone = (DxLinkWebSocketApplicationConnectionFactory) super.clone();
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

    @Configurable(description = "Login plugin")
    public void setLogin(String login) {
        for (DxLinkLoginHandlerFactory factory :
            Services.createServices(DxLinkLoginHandlerFactory.class, null))
        {
            this.loginHandler = factory.createLoginHandler(login, this);
            if (this.loginHandler != null)
                return;
        }
        this.loginHandler = TokenDxLinkLoginHandlerFactory.INSTANCE.createLoginHandler(login, this);
    }

    public QDLoginHandler getLogin() { return loginHandler; }

    /**
     * Returns the aggregation period this client requests from the remote side.
     * @return the requested aggregation period
     */
    public TimePeriod getRequestedAggregationPeriod() {
        return factory.getConfiguration(MessageConnectors.REQUESTED_AGGREGATION_PERIOD_CONFIGURATION_KEY);
    }

    @Configurable(description = "aggregation period requested from the remote side")
    public void setRequestedAggregationPeriod(TimePeriod requestedAggregationPeriod) {
        factory.setConfiguration(MessageConnectors.REQUESTED_AGGREGATION_PERIOD_CONFIGURATION_KEY,
            requestedAggregationPeriod);
    }

    /**
     * @deprecated use {@link #getRequestedAggregationPeriod()}.
     */
    @Deprecated
    public TimePeriod getAcceptAggregationPeriod() {
        return getRequestedAggregationPeriod();
    }

    /**
     * @deprecated use {@link #setRequestedAggregationPeriod(TimePeriod)} and the
     *     {@code requestedAggregationPeriod} address-string / JMX property.
     */
    @Deprecated
    @Configurable(description = "deprecated alias for requestedAggregationPeriod")
    public void setAcceptAggregationPeriod(TimePeriod acceptAggregationPeriod) {
        factory.setConfiguration(MessageConnectors.REQUESTED_AGGREGATION_PERIOD_CONFIGURATION_KEY,
            acceptAggregationPeriod);
    }

    private static TimePeriod readRequestedAggregationPeriodProperty() {
        String requested = SystemProperties.getProperty(REQUESTED_AGGREGATION_PERIOD_PROPERTY, null);
        if (requested != null)
            return TimePeriod.valueOf(requested);
        String legacy = SystemProperties.getProperty(LEGACY_ACCEPT_AGGREGATION_PERIOD_PROPERTY, null);
        if (legacy != null) {
            log.warn("System property '" + LEGACY_ACCEPT_AGGREGATION_PERIOD_PROPERTY +
                "' is deprecated; use '" + REQUESTED_AGGREGATION_PERIOD_PROPERTY + "' instead");
            return TimePeriod.valueOf(legacy);
        }
        return null;
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

    public String getAcceptEventFields() {
        return acceptEventFields;
    }

    @Configurable(description = "accept event fields")
    public void setAcceptEventFields(String acceptEventFields) {
        this.acceptEventFieldsByType = parseAcceptEventFields(acceptEventFields);
        this.acceptEventFields = acceptEventFields;
    }

    public List<String> getAcceptedEventFieldsByType(String eventType) {
        return acceptEventFieldsByType.getOrDefault(eventType, Collections.emptyList());
    }

    static Map<String, List<String>> parseAcceptEventFields(String acceptEventFields) {
        if (!ACCEPT_EVENT_FIELDS_VALIDATOR.matcher(acceptEventFields).matches())
            throw new IllegalArgumentException("Invalid acceptEventFields format");
        Map<String, List<String>> result = new HashMap<>();
        Matcher matcher = ACCEPT_EVENT_FIELDS_PARSER.matcher(acceptEventFields);
        while (matcher.find()) {
            String eventType = matcher.group(1);
            String[] fields = matcher.group(2).split(",");
            result.put(eventType, Arrays.asList(fields));
        }
        return result;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    @Configurable(description = "client application version")
    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    public Map<String, String> getAgentInfo() {
        Map<String, String> agent = new TreeMap<>();
        agent.put("version", QDFactory.getVersion());
        if (getApplicationVersion() != null)
            agent.put("application", getApplicationVersion());
        agent.put("platform", SystemProperties.getProperty("os.name", null) + " " +
            SystemProperties.getProperty("os.version", null));
        String javaVersion = SystemProperties.getProperty("java.version", null);
        if (javaVersion != null)
            agent.put("java", javaVersion);
        return agent;
    }

    public String toString() {
        return factory.toString();
    }
}
