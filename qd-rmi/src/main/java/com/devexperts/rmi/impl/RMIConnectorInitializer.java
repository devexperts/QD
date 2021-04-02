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
package com.devexperts.rmi.impl;

import com.devexperts.connector.codec.ssl.SSLConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.ConfigurationException;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.SystemProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RMIConnectorInitializer implements QDEndpoint.ConnectorInitializer {
    public static final int DEFAULT_WEIGHT =
        SystemProperties.getIntProperty(RMIConnectorInitializer.class, "defaultWeight", 10);

    private RMIEndpointImpl rmiEndpoint;

    public RMIConnectorInitializer(RMIEndpointImpl rmiEndpoint) {
        this.rmiEndpoint = rmiEndpoint;
    }

    @Override
    public void createAndAddConnector(QDEndpoint qdEndpoint, String address) {
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
            MessageConnectors.applicationConnectionFactory(new AdapterFactory()), address, qdEndpoint.getRootStats());
        if (rmiEndpoint.trustManager != null) {
            try {
                for (MessageConnector connector : connectors) {
                    SSLConnectionFactory factory = MessageConnectors.getCodecFactory(connector.getFactory(), SSLConnectionFactory.class);
                    if (factory != null)
                        factory.setTrustManager(rmiEndpoint.trustManager);
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Trust store may be specified for client socket connector only", e);
            }
        }
        qdEndpoint.addConnectors(connectors);
        rmiEndpoint.setConnectedAddressSync(address);
    }

    private class AdapterFactory extends MessageAdapter.ConfigurableFactory {

        private ServiceFilter services = ServiceFilter.ANYTHING;
        private AdvertisementFilter advertisementFilter = AdvertisementFilter.ALL;
        private int weight = DEFAULT_WEIGHT;

        private MessageAdapter.ConfigurableFactory attachedMessageAdapterFactory =
            rmiEndpoint.getAttachedMessageAdapterFactory();

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            MessageAdapter attachedAdapter = attachedMessageAdapterFactory == null ?
                null : attachedMessageAdapterFactory.createAdapter(stats);
            return new RMIConnection(rmiEndpoint, stats, attachedAdapter, services, advertisementFilter, weight).messageAdapter;
        }

        @Override
        public MessageAdapter.ConfigurableFactory clone() {
            AdapterFactory clone = (AdapterFactory) super.clone();
            if (attachedMessageAdapterFactory != null)
                clone.attachedMessageAdapterFactory = attachedMessageAdapterFactory.clone();
            return clone;
        }

        @Override
        public Set<ConfigurationKey<?>> supportedConfiguration() {
            if (attachedMessageAdapterFactory != null) {
                Set<ConfigurationKey<?>> set = new LinkedHashSet<>(attachedMessageAdapterFactory.supportedConfiguration());
                set.addAll(super.supportedConfiguration());
                return set;
            }
            return super.supportedConfiguration();
        }

        @Override
        public <T> T getConfiguration(ConfigurationKey<T> key) {
            if (attachedMessageAdapterFactory != null && attachedMessageAdapterFactory.supportedConfiguration().contains(key))
                return attachedMessageAdapterFactory.getConfiguration(key);
            return super.getConfiguration(key);
        }

        @Override
        public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) throws ConfigurationException {
            if (attachedMessageAdapterFactory != null && attachedMessageAdapterFactory.supportedConfiguration().contains(key))
                return attachedMessageAdapterFactory.setConfiguration(key, value);
            return super.setConfiguration(key, value);
        }

        @Configurable(description = "advertised services pattern/filter")
        public void setServices(ServiceFilter services) {
            this.services = services;
        }

        public ServiceFilter getServices() {
            return services;
        }

        @SuppressWarnings("unused") // Used by ConfigurableObject framework
        @Configurable(description = "services advertisement mode on this connection")
        public void setAdvertise(AdvertisementFilter filter) {
            advertisementFilter = filter;
        }

        @SuppressWarnings("unused") // Used by ConfigurableObject framework
        public AdvertisementFilter getAdvertise() {
            return advertisementFilter;
        }

        @Configurable(description = "connection weight")
        public void setWeight(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public <T> void setEndpoint(Class<?> endpointClass, T endpointInstance) {
            if (attachedMessageAdapterFactory == null)
                super.setEndpoint(endpointClass, endpointInstance);
            else
                attachedMessageAdapterFactory.setEndpoint(endpointClass, endpointInstance);
        }

        @Override
        public <T> T getEndpoint(Class<T> endpointClass) {
            if (attachedMessageAdapterFactory == null)
                return super.getEndpoint(endpointClass);
            else
                return attachedMessageAdapterFactory.getEndpoint(endpointClass);
        }

        @Override
        public String toString() {
            return attachedMessageAdapterFactory == null ? "RMI" : attachedMessageAdapterFactory.toString();
        }
    }
}
