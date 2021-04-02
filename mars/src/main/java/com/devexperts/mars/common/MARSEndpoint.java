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
package com.devexperts.mars.common;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.net.MARSConnector;
import com.devexperts.services.Services;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A container for MARS root and its connectors.
 * Use {@link #newBuilder()}.{@link Builder#build() build()} to create an instance of this class.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is  tread-safe.
 */
public class MARSEndpoint {
    // use MARS class for logging to be nicer...
    private static final Logging log = Logging.getLogging(MARS.class);

    // captures relevant system properties in static section
    private static final Builder SYSTEM_BUILDER = new Builder();

    static {
        // capture relevant system props
        SYSTEM_BUILDER.withProperty(MARSNode.MARS_ROOT_PROPERTY, SystemProperties.getProperty(MARSNode.MARS_ROOT_PROPERTY, ""));
        SYSTEM_BUILDER.withProperty(MARSNode.MARS_ADDRESS_PROPERTY, SystemProperties.getProperty(MARSNode.MARS_ADDRESS_PROPERTY, ""));
    }

    private static class InstanceHolder {
        // initializes and starts on first use!!!
        static final MARSEndpoint INSTANCE = SYSTEM_BUILDER.acquire(); // build explicitly
    }

    /**
     * Returns a default instance of MARS endpoint that is automatically
     * configured from system properties {@link MARSNode#MARS_ROOT_PROPERTY MARS_ROOT_PROPERTY} and
     * {@link MARSNode#MARS_ADDRESS_PROPERTY MARS_ADDRESS_PROPERTY}.
     * It is created and acquired on first use. It is shared by all manually created MARSEndpoint instances that do not
     * explicitly specify any MARS configuration properties.
     */
    public static MARSEndpoint getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Creates new {@link Builder} instance.
     * Use {@link Builder#build()} to build an instance of {@link MARSEndpoint} when
     * all configuration properties were set.
     *
     * @return the created endpoint builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ---------------------- static data ----------------------

    private static final Map<Builder, MARSEndpoint> INSTANCES = new HashMap<>();

    // ---------------- instance ----------------

    private final Builder builder;
    private final String marsRootName;
    private final MARS mars;
    private MARSNode root; // non-final for a deprecated MARSNode.setRoot(...) method
    private final String address;
    private final List<MARSConnector> connectors = new ArrayList<>();
    private final List<MARSPlugin> plugins = new ArrayList<>();
    private int refCounter;

    MARSEndpoint(Builder builder, String marsRootName, MARS mars, MARSNode root, String address) {
        this.builder = builder;
        this.marsRootName = marsRootName;
        this.mars = mars;
        this.root = root;
        this.address = address;
    }

    /**
     * Returns original user-specified value of {@link MARSNode#MARS_ROOT_PROPERTY}.
     * May be empty if it was not specified.
     */
    public String getMarsRootName() {
        return marsRootName;
    }

    // SYNC(INSTANCES)
    void acquire() {
        if (refCounter++ > 0)
            return;
        // create connector
        if (address.length() > 0) {
            MARSConnector connector = new MARSConnector(mars, false, true);
            connector.setAddress(address);
            connector.start();
            connectors.add(connector);
            log.info("MARS root is " + root + ", address is " + LogUtil.hideCredentials(address));
        }
        // create all plugins
        for (MARSPlugin.Factory pluginFactory : Services.createServices(MARSPlugin.Factory.class, null)) {
            MARSPlugin plugin = pluginFactory.createPlugin(this);
            plugin.start();
            plugins.add(plugin);
            log.info("Started " + plugin);
        }
    }

    public void release() {
        synchronized (INSTANCES) {
            if (--refCounter > 0)
                return;
            INSTANCES.remove(builder);
            for (MARSConnector connector : connectors) {
                log.info("Stopping " + connector);
                try {
                    connector.stop();
                } catch (Exception e) {
                    log.info("Failed to stop connector", e);
                }
            }
            for (MARSPlugin plugin : plugins) {
                log.info("Stopping " + plugin);
                try {
                    plugin.stop();
                } catch (Exception e) {
                    log.error("Failed to stop plugin", e);
                }
            }
            connectors.clear();
            plugins.clear();
        }
    }

    public MARSNode getRoot() {
        return root; // root is effectively final in non-deprecated code, so no synchronization needed
    }

    // for a deprecated MARSNode.setRoot(...) method only
    synchronized void setRoot(MARSNode root) {
        this.root = root;
        log.warn("[DEPRECATED METHOD MARSNode.setRoot: Use -D" + MARSNode.MARS_ROOT_PROPERTY + "=<root-name>] MARS root is set to " + root);
    }

    /**
     * Builder that creates instances of {@link MARSEndpoint} objects.
     */
    public static class Builder {
        private static final Set<String> SUPPORTED_PROPERTIES = new HashSet<>(Arrays.<String>asList(
            MARSNode.MARS_ROOT_PROPERTY,
            MARSNode.MARS_ADDRESS_PROPERTY
        ));

        protected final Properties props = new Properties();

        Builder() {}

        /**
         * Sets the specified property. Unsupported properties are ignored.
         *
         * @see #supportsProperty(String)
         */
        public final Builder withProperty(String key, String value) {
            if (key == null || value == null)
                throw new NullPointerException();
            if (supportsProperty(key))
                props.setProperty(key, value);
            return this;
        }

        /**
         * Sets all supported properties from the provided properties object.
         */
        public final Builder withProperties(Properties props) {
            for (Map.Entry<Object, Object> entry : props.entrySet())
                withProperty((String) entry.getKey(), (String) entry.getValue());
            return this;
        }

        /**
         * Returns true if the corresponding property key is supported.
         *
         * @see #withProperty(String, String)
         */
        public boolean supportsProperty(String key) {
            return SUPPORTED_PROPERTIES.contains(key);
        }

        public MARSEndpoint acquire() {
            // takes property defaults from system properties
            String name = props.getProperty(MARSNode.MARS_ROOT_PROPERTY,
                SYSTEM_BUILDER.props.getProperty(MARSNode.MARS_ROOT_PROPERTY, ""));
            String address = props.getProperty(MARSNode.MARS_ADDRESS_PROPERTY,
                SYSTEM_BUILDER.props.getProperty(MARSNode.MARS_ADDRESS_PROPERTY, ""));
            // set resolves properties
            props.setProperty(MARSNode.MARS_ROOT_PROPERTY, name);
            props.setProperty(MARSNode.MARS_ADDRESS_PROPERTY, address);
            // now get existing instance of build
            synchronized (INSTANCES) {
                MARSEndpoint endpoint = INSTANCES.get(this);
                if (endpoint == null)
                    INSTANCES.put(this, endpoint = build());
                endpoint.acquire();
                return endpoint;
            }
        }

        // SYNC(INSTANCES)
        private MARSEndpoint build() {
            // create all
            String name = props.getProperty(MARSNode.MARS_ROOT_PROPERTY);
            String address = props.getProperty(MARSNode.MARS_ADDRESS_PROPERTY);
            MARS mars = new MARS();
            MARSNode root = new MARSNode(mars, name).subNode(MARSNode.generateUniqueName());
            return new MARSEndpoint(this, name, mars, root, address);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Builder && props.equals(((Builder) o).props);
        }

        @Override
        public int hashCode() {
            return props.hashCode();
        }
    }
}
