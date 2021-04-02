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
package com.devexperts.qd.monitoring;

import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSScheduler;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.JMXStats;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.JMXNameBuilder;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * JMX management utilities for QD.
 * @deprecated Don't use this class. It initializes monitoring from system properties on first use. This approach
 * is deprecated. Use {@link QDEndpoint.Builder#withProperty(String, String)} to specify application-specific
 * monitoring properties.
 */
public class QDMonitoring {
    /** @deprecated Use {@link JMXEndpoint#JMX_HTML_PORT_PROPERTY} */
    public static final String JMX_HTML_PORT_PROPERTY = JMXEndpoint.JMX_HTML_PORT_PROPERTY;
    /** @deprecated Use {@link JMXEndpoint#JMX_HTML_BIND_PROPERTY} */
    public static final String JMX_HTML_BIND_PROPERTY = JMXEndpoint.JMX_HTML_BIND_PROPERTY;
    /** @deprecated Use {@link JMXEndpoint#JMX_HTML_SSL_PROPERTY} */
    public static final String JMX_HTML_SSL_PROPERTY = JMXEndpoint.JMX_HTML_SSL_PROPERTY;
    /** @deprecated Use {@link JMXEndpoint#JMX_HTML_AUTH_PROPERTY} */
    public static final String JMX_HTML_AUTH_PROPERTY = JMXEndpoint.JMX_HTML_AUTH_PROPERTY;

    /** @deprecated  Use {@link JMXEndpoint#JMX_RMI_PORT_PROPERTY} */
    public static final String JMX_RMI_PORT_PROPERTY = JMXEndpoint.JMX_RMI_PORT_PROPERTY;

    static {
        // init HTML management if needed
        try {
            if (Integer.getInteger(JMX_HTML_PORT_PROPERTY) != null)
                initHtmlJMX(System.getProperties());
        } catch (SecurityException e) {
            // ignored -- failed to read property
        }
        // init RMI management if needed
        try {
            if (Integer.getInteger(JMX_RMI_PORT_PROPERTY) != null)
                initRmiJMX(System.getProperties());
        } catch (SecurityException e) {
            // ignored -- failed to read property
        }
    }

    private QDMonitoring() {} // do not create

    /**
     * Initializes all JMX monitoring tools.
     * @deprecated No replacement.
     */
    public static void init() {
        // actually does nothing. everything happens in static initialization section.
    }

    /**
     * Convenient method to register JMX MBean.
     * This is a shortcut for {@link Management#registerMBean(Object, Class, String) registerMBean(mbean, null, name)}.
     * @param mbean the MBean to be registered.
     * @param name the object name of MBean.
     * @return true if MBean with this name was already registered.
     * @deprecated Use {@link Management#registerMBean(Object, Class, String)} which also provides means to unregister
     *             the bean later if needed.
     */
    public static boolean registerMBean(Object mbean, String name) {
        return Management.registerMBean(mbean, null, name).hasExisted();
    }

    /**
     * @deprecated Use {@link QDEndpoint.Builder#withProperty(String, String)} to specify monitoring properties
     */
    public static void initHtmlJMX(Properties props) {
        try {
            JmxHtml.init(props);
        } catch (Throwable t) {
            QDLog.log.error("Failed to initialize JMX HTML Adaptor", t);
        }
    }

    private static void initRmiJMX(Properties props) {
        try {
            JmxRmi.init(props);
        } catch (Throwable t) {
            QDLog.log.error("Failed to initialize JMX RMI Connector", t);
        }
    }

    /**
     * @deprecated Use {@link QDEndpoint.Builder#withName(String)} to specify monitoring name or
     * {@link JMXStats#createRoot(String, DataScheme)} which also provides means to unregister all MBeans from JMX later.
     */
    public static QDStats createRootStats(String name, DataScheme scheme) {
        return JMXStats.createRoot(name, scheme).getRootStats();
    }

    /**
     * Registers a list of {@link MessageConnector} instances as MBeans for monitoring and management.
     * @deprecated Use {@link QDEndpoint#addConnector} to monitor connections.
     */
    public static void registerConnectors(String domain, List<MessageConnector> connectors) {
        int id = 1;
        for (MessageConnector con : connectors)
            Management.registerMBean(con, null,
                domain + ":id=" + JMXNameBuilder.quoteKeyPropertyValue((id++) + "-" + con.toString()));
    }

    /**
     * Registers periodic task in a dedicated "MonitoringScheduler" thread.
     * It is designed for stats logging and other periodic management activities that
     * run forever inside JVM.
     * @deprecated Use {@link MARSScheduler#schedule(Runnable, long, TimeUnit)}
     */
    public static void registerPeriodicTask(long period, Runnable task) {
        MARSScheduler.schedule(task, period, TimeUnit.MILLISECONDS);
    }
}
