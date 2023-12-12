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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class JMXEndpoint {
    /**
     * Defines TCP/IP port for JMX HTML management console.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String JMX_HTML_PORT_PROPERTY = "jmx.html.port";

    /**
     * Defines IP bind address for JMX HTML management console.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String JMX_HTML_BIND_PROPERTY = "jmx.html.bind";

    /**
     * Turns on SSL for JMX HTML management console.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String JMX_HTML_SSL_PROPERTY = "jmx.html.ssl";

    /**
     * Turns on authorization for JMX HTML management console.
     * Format is "user1:password1,user2:password2,...".
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String JMX_HTML_AUTH_PROPERTY = "jmx.html.auth";

    /**
     * Defines TCP/IP port for JMX RMI management.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String JMX_RMI_PORT_PROPERTY = "jmx.rmi.port";

    private static final Logging log = Logging.getLogging(JMXEndpoint.class);

    public static Builder newBuilder() {
        return new Builder();
    }

    // ---------------------- static data ----------------------

    private static final Map<Builder, JMXEndpoint> INSTANCES = new HashMap<>();

    // ---------------------- instance ----------------------

    private final Builder builder;
    private final List<JmxConnector> jmxConnectors;
    private int refCounter;

    public JMXEndpoint(Builder builder, List<JmxConnector> jmxConnectors) {
        this.builder = builder;
        this.jmxConnectors = jmxConnectors;
    }

    // SYNC(INSTANCES)
    void acquire() {
        refCounter++;
    }

    public void release() {
        synchronized (INSTANCES) {
            if (--refCounter > 0)
                return;
            INSTANCES.remove(builder);
            for (JmxConnector jmxConnector : jmxConnectors) {
                log.info("Stopping JMX Connector " + jmxConnector);
                try {
                    jmxConnector.stop();
                } catch (Exception e) {
                    log.error("Failed to stop JMX Connector " + jmxConnector, e);
                }
            }
        }
    }

    public static class Builder {
        private static final Set<String> SUPPORTED_PROPERTIES = new LinkedHashSet<>(Arrays.asList(
            JMX_HTML_PORT_PROPERTY,
            JMX_HTML_BIND_PROPERTY,
            JMX_HTML_SSL_PROPERTY,
            JMX_HTML_AUTH_PROPERTY,
            JMX_RMI_PORT_PROPERTY
        ));

        private final Properties props = new Properties();

        Builder() {}

        /**
         * Sets the specified property. Unsupported properties are ignored.
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
         * @see #withProperty(String, String)
         */
        public boolean supportsProperty(String key) {
            return SUPPORTED_PROPERTIES.contains(key);
        }

        public JMXEndpoint acquire() {
            synchronized (INSTANCES) {
                JMXEndpoint endpoint = INSTANCES.get(this);
                if (endpoint == null)
                    INSTANCES.put(this, endpoint = build());
                endpoint.acquire();
                return endpoint;
            }
        }

        // SYNC(INSTANCES)
        private JMXEndpoint build() {
            // log properties
            for (String key : SUPPORTED_PROPERTIES) {
                if (props.containsKey(key)) {
                    String v = props.getProperty(key);
                    log.info("JMXEndpoint with " + key + "=" + (JMX_HTML_AUTH_PROPERTY.equals(key) ? "****" : v));
                }
            }
            // create connectors
            List<JmxConnector> jmxConnectors = new ArrayList<>();
            if (props.containsKey(JMX_HTML_PORT_PROPERTY)) {
                try {
                    JmxConnector connector = JmxHtml.init(props);
                    if (connector != null)
                        jmxConnectors.add(connector);
                } catch (Throwable e) {
                    log.error("Failed to initialize JMX HTML Adaptor", e);
                }
            }
            if (props.containsKey(JMX_RMI_PORT_PROPERTY)) {
                try {
                    JmxConnector connector = JmxRmi.init(props);
                    if (connector != null)
                        jmxConnectors.add(connector);
                } catch (Throwable e) {
                    log.error("Failed to initialize JMX RMI Connector", e);
                }
            }
            JMXEndpoint endpoint = new JMXEndpoint(this, jmxConnectors);
            return endpoint;
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
