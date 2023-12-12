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
package com.devexperts.qd;

import com.devexperts.logging.Logging;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.Services;
import com.devexperts.util.SystemProperties;

/**
 * The <code>QDFactory</code> creates implementations for core QD components.
 */
public abstract class QDFactory {

    private static final Logging log = Logging.getLogging(QDFactory.class);

    /**
     * Creates builder for an collector that has specified contract.
     * @param contract the contract of the collector.
     */
    public abstract QDCollector.Builder<?> collectorBuilder(QDContract contract);

    @SuppressWarnings("unchecked")
    public final QDCollector.Builder<QDTicker> tickerBuilder() {
        return (QDCollector.Builder<QDTicker>) collectorBuilder(QDContract.TICKER);
    }

    @SuppressWarnings("unchecked")
    public final QDCollector.Builder<QDStream> streamBuilder() {
        return (QDCollector.Builder<QDStream>) collectorBuilder(QDContract.STREAM);
    }

    @SuppressWarnings("unchecked")
    public final QDCollector.Builder<QDHistory> historyBuilder() {
        return (QDCollector.Builder<QDHistory>) collectorBuilder(QDContract.HISTORY);
    }

    /**
     * Creates implementation of ticker-view for specified data scheme
     * with default statistics gathering delegate.
     * @deprecated use {@link #tickerBuilder()} instead.
     */
    public final QDTicker createTicker(DataScheme scheme) {
        return tickerBuilder().withScheme(scheme).build();
    }

    /**
     * Creates implementation of stream-view for specified data scheme
     * with default statistics gathering delegate.
     * @deprecated use {@link #streamBuilder()} instead.
     */
    public final QDStream createStream(DataScheme scheme) {
        return streamBuilder().withScheme(scheme).build();
    }

    /**
     * Creates implementation of history-view for specified data scheme
     * with default statistics gathering delegate.
     * @deprecated use {@link #historyBuilder()} instead.
     */
    public final QDHistory createHistory(DataScheme scheme) {
        return historyBuilder().withScheme(scheme).build();
    }

    /**
     * Creates implementation of ticker-view for specified data scheme
     * and a specified statistics gathering delegate.
     * @deprecated use {@link #tickerBuilder()} instead.
     */
    public final QDTicker createTicker(DataScheme scheme, QDStats stats) {
        return tickerBuilder().withScheme(scheme).withStats(stats).build();
    }

    /**
     * Creates implementation of stream-view for specified data scheme
     * and a specified statistics gathering delegate.
     * @deprecated use {@link #streamBuilder()} instead.
     */
    public final QDStream createStream(DataScheme scheme, QDStats stats) {
        return streamBuilder().withScheme(scheme).withStats(stats).build();
    }

    /**
     * Creates implementation of history-view for specified data scheme
     * and a specified statistics gathering delegate, using default filter.
     * @deprecated use {@link #historyBuilder()} instead.
     */
    public final QDHistory createHistory(DataScheme scheme, QDStats stats) {
        return historyBuilder().withScheme(scheme).withStats(stats).build();
    }

    /**
     * Creates implementation of history-view for specified data scheme,
     * a specified statistics gathering delegate, and a filter.
     * @deprecated use {@link #historyBuilder()} instead.
     */
    public final QDHistory createHistory(DataScheme scheme, QDStats stats, HistorySubscriptionFilter historyFilter) {
        return historyBuilder().withScheme(scheme).withStats(stats).withHistoryFilter(historyFilter).build();
    }

    /**
     * Creates builder for an agent that is not tied to any particular collector, has no data,
     * and only keeps track of added and remove subscription.
     * @param contract the contract of the agent.
     * @param scheme the data scheme.
     */
    public QDAgent.Builder createVoidAgentBuilder(QDContract contract, DataScheme scheme) {
        throw new UnsupportedOperationException();
    }

    // ========== Defaults ==========

    private static String version;
    private static boolean versionShown;
    private static volatile QDFactory defaultFactory;
    private static volatile DataScheme defaultScheme;

    /**
     * Shows QDS version in the log (at most once).
     */
    public static synchronized void showVersion() {
        if (!versionShown) {
            log.info("Using " + getVersion() + ", (C) Devexperts");
            versionShown = true;
        }
    }

    private static synchronized QDFactory createDefaultFactory() {
        if (defaultFactory != null)
            return defaultFactory; // double check under synchronization
        showVersion();
        defaultFactory = Services.createService(QDFactory.class, null, null);
        if (defaultFactory == null)
            defaultFactory = new MatrixFactory();
        return defaultFactory;
    }

    private static synchronized DataScheme createDefaultScheme() {
        if (defaultScheme != null)
            return defaultScheme; // double check under synchronization
        showVersion();
        defaultScheme = createDefaultScheme(null);
        log.info("Using scheme " + defaultScheme.getClass().getName() + " " + defaultScheme.getDigest());
        return defaultScheme;
    }

    /**
     * Creates default data scheme for a specified class loader.
     * <code>loader</code> may be <code>null</code> to specify default class loader.
     * In this case "scheme" system property is checked for an implementation class name or jar file first.
     * If "scheme" system property value starts with "ext:" prefix, try to load {@link DataSchemeFactory}
     * via {@link Services#createServices(Class, ClassLoader)}.
     * The scheme is loaded via
     * {@link Services#createService(Class, ClassLoader, String) Services.createService(DataScheme.class, loader, scheme)},
     * where scheme is the value of "scheme" system property or null.
     * @throws IllegalArgumentException if default scheme is not found.
     */
    public static DataScheme createDefaultScheme(ClassLoader loader) {
        String schemeProp = SystemProperties.getProperty("scheme", null);

        if (schemeProp != null) {
            for (DataSchemeFactory dsf : Services.createServices(DataSchemeFactory.class, loader)) {
                // Could throw IllegalArgumentException!
                DataScheme scheme = dsf.createDataScheme(schemeProp);
                if (scheme != null)
                    return scheme;
            }
        }

        DataScheme scheme = Services.createService(DataScheme.class, loader, loader != null ? null : schemeProp);
        if (scheme == null)
            throw new IllegalArgumentException("Default scheme is not found");
        return scheme;
    }

    /**
     * Creates default implementation of {@link QDStats} with the specified type
     * and scheme. Scheme may be <code>null</code> if per-record information
     * is not required.
     */
    public static QDStats createStats(QDStats.SType type, DataScheme scheme) {
        QDStats stats = Services.createService(QDStats.class, null, null);
        if (stats == null)
            return QDStats.VOID;
        stats.initRoot(type, scheme);
        return stats;
    }

    /**
     * Returns string description of this QDS version.
     */
    public static String getVersion() {
        String version = QDFactory.version;
        if (version == null) {
            String mainVersion = getImplementationVersion(Package.getPackage("com.devexperts.qd"));
            StringBuilder sb = new StringBuilder("QDS-");
            sb.append(mainVersion);
            checkOtherPackageVersion(sb, mainVersion, "dxlib", "com.devexperts.services");
            checkOtherPackageVersion(sb, mainVersion, "file", "com.devexperts.qd.qds.file");
            checkOtherPackageVersion(sb, mainVersion, "mars", "com.devexperts.mars.common");
            checkOtherPackageVersion(sb, mainVersion, "monitoring", "com.devexperts.qd.monitoring");
            checkOtherPackageVersion(sb, mainVersion, "dxfeed", "com.dxfeed.api");
            checkOtherPackageVersion(sb, mainVersion, "tools", "com.devexperts.qd.tools");
            version = sb.toString();
            QDFactory.version = version;
        }
        return version;
    }

    private static void checkOtherPackageVersion(StringBuilder sb, String mainVersion, String shortName, String fqName) {
        Package otherPackage = Package.getPackage(fqName);
        if (otherPackage != null) {
            String otherVersion = getImplementationVersion(otherPackage);
            if (!otherVersion.equals(mainVersion))
                sb.append('+').append(shortName).append('-').append(otherVersion);
        }
    }

    /**
     * Overwrites string description of this QDS version <b>for testing purposes only</b>.
     * @param version version string to set. Use null to restore to default value.
     */
    public static void setVersion(String version) {
        QDFactory.version = version;
    }

    private static String getImplementationVersion(Package p) {
        String version = p == null ? null : p.getImplementationVersion();
        return version == null ? "UNKNOWN" : version;
    }

    /**
     * Returns default QD factory.
     */
    public static QDFactory getDefaultFactory() {
        return defaultFactory == null ? createDefaultFactory() : defaultFactory;
    }

    /**
     * Returns default data scheme for a system class loader.
     * @see #createDefaultScheme(ClassLoader)
     */
    public static DataScheme getDefaultScheme() {
        return defaultScheme == null ? createDefaultScheme() : defaultScheme;
    }
}
