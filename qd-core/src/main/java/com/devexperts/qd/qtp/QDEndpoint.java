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
import com.devexperts.connector.proto.EndpointId;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.Service;
import com.devexperts.services.Services;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Named collection of QD collectors and network connections.
 * Use {@link #newBuilder()}.{@link Builder#build() build()} to create an instance of this class.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is  tread-safe.
 */
public class QDEndpoint implements Closeable {
    /**
     * Defines name for an endpoint that is used to distinguish multiple endpoints
     * in the same JVM in logs and in other diagnostic means.
     * Use {@link Builder#withProperty(String, String)} method.
     * This property is also changed by {@link Builder#withName(String)} method.
     */
    public static final String NAME_PROPERTY = "name";

    /**
     * Defines symbol striping strategy for an endpoint.
     * Use {@link Builder#withProperty(String, String)}. This property is also changed by
     * {@link Builder#withStripe(String)} or {@link Builder#withStriper(SymbolStriper)} method.
     * @see SymbolStriper
     */
    public static final String DXFEED_STRIPE_PROPERTY = "dxfeed.stripe";

    /**
     * Creates new {@link Builder} instance.
     * Use {@link Builder#build()} to build an instance of {@link QDEndpoint} when
     * all configuration properties were set.
     *
     * @return the created endpoint builder.
     */
    public static Builder newBuilder() {
        Builder builder = Services.createService(Builder.class, null, null);
        if (builder == null)
            builder = new Builder();
        return builder;
    }

    // ------------------ instance ------------------

    protected final Logging log = Logging.getLogging(getClass());

    // everything is modified only under SYNC(lock)
    private final Object lock = new Lock();

    private final String name;
    private final DataScheme scheme;
    private final QDStats rootStats;
    private final EndpointId endpointId;

    private QDTicker ticker;
    private QDStream stream;
    private QDHistory history;
    private ConnectorInitializer connectorInitializer;

    private String user = ""; // SYNC(lock)
    private String password = ""; // SYNC(lock)

    private final EnumMap<QDContract, QDCollector> collectors = new EnumMap<>(QDContract.class);
    private final Set<QDContract> collectorsKeys = Collections.unmodifiableSet(collectors.keySet());
    private final Collection<QDCollector> collectorsValues = Collections.unmodifiableCollection(collectors.values());
    private final List<MessageConnector> connectors = new CopyOnWriteArrayList<>();
    private final List<MessageConnectorListener> connectorListeners = new ArrayList<>();
    private final List<Plugin> plugins = new ArrayList<>();
    private final boolean withEventTimeSequence;
    private final boolean storeEverything;
    private final SymbolStriper striper;

    private volatile boolean closed;

    /**
     * Creates custom endpoint with a specified name, scheme, and root stats, without management.
     * This constructor is used only if user defines all management for this endpoint and
     * automated management is not necessary.
     *
     * @deprecated  Use {@link Builder#build()} or builder constructor.
     */
    @Deprecated
    protected QDEndpoint(String name, DataScheme scheme, QDStats rootStats,
        List<QDCollector.Factory> collectors, boolean withEventTimeSequence, boolean storeEverything)
    {
        this(new Builder()
            .withName(name)
            .withScheme(scheme)
            .withCollectors(collectors)
            .withEventTimeSequence(withEventTimeSequence)
            .withStoreEverything(storeEverything),
            rootStats);
    }

    protected QDEndpoint(Builder builder, QDStats rootStats) {
        this.name = builder.getOrCreateName();
        this.scheme = builder.getSchemeOrDefault();
        this.rootStats = Objects.requireNonNull(rootStats, "rootStats");
        this.endpointId = EndpointId.newEndpointId(name);
        this.withEventTimeSequence = builder.withEventTimeSequence;
        this.storeEverything = builder.storeEverything;
        this.striper = builder.getStriperOrDefault();

        checkSchemes(scheme, rootStats.getScheme(), "rootStats");
        checkSchemes(scheme, striper.getScheme(), "striper");
        initCollectors(builder.collectors);
    }

    private static void checkSchemes(DataScheme expected, DataScheme scheme, String source) {
        if (scheme != null && scheme != expected) {
            throw new IllegalArgumentException(
                "Different scheme in " + source + ": " + "found " + scheme + ", expected " + expected);
        }
    }

    public Object getLock() {
        return lock;
    }

    private void initCollectors(List<QDCollector.Factory> collectorFactories) {
        if (collectorFactories.isEmpty())
            return; // don't log when there are no collectors
        log.info(name + " with collectors " + collectorFactories);
        QDFactory defaultFactory = QDFactory.getDefaultFactory();
        for (QDCollector.Factory factory : collectorFactories) {
            QDCollector.Builder<?> builder = defaultFactory.collectorBuilder(factory.getContract())
                .withScheme(scheme)
                .withStats(rootStats.create(factory.getStatsType()))
                .withEventTimeSequence(withEventTimeSequence)
                .withStoreEverything(storeEverything)
                .withStriper(striper);
            QDCollector collector = factory.createCollector(defaultFactory, builder);
            if (this.collectors.containsKey(collector.getContract())) {
                throw new IllegalArgumentException("Multiple collectors with the same contract: " +
                    collector.getContract());
            }
            collectors.put(collector.getContract(), collector);
            switch (collector.getContract()) {
                case TICKER:
                    ticker = (QDTicker) collector;
                    break;
                case STREAM:
                    stream = (QDStream) collector;
                    break;
                case HISTORY:
                    history = (QDHistory) collector;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected contract " + collector.getContract());
            }
        }
    }

    /**
     * Connects the endpoint to specified address(es).
     * This method can be used only when sets {@link ConnectorInitializer connectorInitializer}, otherwise it will be
     * {@link IllegalStateException}.
     *
     * @param address address(es) to connect to.
     * @see #hasConnectorInitializer()
     */
    public void connect(String address) {
        initializeConnectorsForAddress(address);
        startConnectors();
    }

    /**
     * Initializes endpoint's connectors to the specified address(es).
     * This method can be used only when sets {@link ConnectorInitializer connectorInitializer}, otherwise it will be
     * {@link IllegalStateException}.
     *
     * @param address address(es) to connect to.
     * @see #hasConnectorInitializer()
     */
    public void initializeConnectorsForAddress(String address) {
        if (!hasConnectorInitializer())
            throw new IllegalStateException("ConnectorsInitializer is not set");
        connectorInitializer.createAndAddConnector(this, address);
        updateUserAndPasswordImpl(connectors);
    }

    /**
     * Sets {@link ConnectorInitializer} for this endpoint, which is used in {@link #connect(String)}
     * @param connectorInitializer {@link ConnectorInitializer}, which is used in {@link #connect(String)}
     */
    public void setConnectorInitializer(ConnectorInitializer connectorInitializer) {
        synchronized (lock) {
            this.connectorInitializer = connectorInitializer;
        }
    }

    /**
     * Returns {@code true} if this endpoint has a {@link ConnectorInitializer}, otherwise returns {@code false}
     * @return {@code true} if this endpoint has a {@link ConnectorInitializer}, otherwise returns {@code false}
     */
    public boolean hasConnectorInitializer() {
        synchronized (lock) {
            return connectorInitializer != null;
        }
    }

    /**
     * Returns {@code true} if this endpoint supports event time sequence
     * or any it's collector supports event time sequence
     *
     * @return {@code true} if this endpoint supports event time sequence
     * or any it's collector supports event time sequence
     */
    public boolean hasEventTimeSequence() {
        boolean hasEventTimeSequence = withEventTimeSequence;
        for (QDCollector c : collectorsValues) {
            hasEventTimeSequence |= c.hasEventTimeSequence();
        }
        return hasEventTimeSequence;
    }

    public boolean isClosed() {
        return closed;
    }

    public final String getName() {
        return name;
    }

    public Map<String, String> getDescriptorProperties() {
        return Collections.emptyMap();
    }

    public final DataScheme getScheme() {
        return scheme;
    }

    public QDStats getRootStats() {
        return rootStats;
    }

    public final QDTicker getTicker() {
        return ticker;
    }

    public final QDStream getStream() {
        return stream;
    }

    public final QDHistory getHistory() {
        return history;
    }

    public QDCollector getCollector(QDContract contract) {
        return collectors.get(contract);
    }

    public Set<QDContract> getContracts() {
        return collectorsKeys;
    }

    public Collection<QDCollector> getCollectors() {
        return collectorsValues;
    }

    // this method must be lock-free, so that DXFeedEndpointImpl.updateState can be generally lock-free
    public List<MessageConnector> getConnectors() {
        return connectors;
    }

    @Deprecated
    public QDEndpoint addCollector(QDCollector collector) {
        synchronized (lock) {
            if (collector.getScheme() != scheme) {
                throw new IllegalArgumentException("Different scheme in endpoint collector. " +
                    "Found " + collector.getScheme() + ", expected " + scheme);
            }
            if (closed)
                return this;
            QDContract contract = collector.getContract();
            switch (contract) {
                case TICKER:
                    if (ticker != null)
                        collectorRedefined(contract);
                    ticker = (QDTicker) collector;
                    break;
                case STREAM:
                    if (stream != null)
                        collectorRedefined(contract);
                    stream = (QDStream) collector;
                    break;
                case HISTORY:
                    if (history != null)
                        collectorRedefined(contract);
                    history = (QDHistory) collector;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected contract " + contract);
            }
            collectors.put(contract, collector);
        }
        return this;
    }

    @Deprecated
    public QDEndpoint addCollectors(QDCollector... collectors) {
        synchronized (lock) {
            for (QDCollector collector : collectors) {
                addCollector(collector);
            }
        }
        return this;
    }

    public final void addPlugin(Plugin plugin) {
        synchronized (lock) {
            plugins.add(plugin);
        }
    }

    public final void removePlugin(Plugin plugin) {
        synchronized (lock) {
            plugins.remove(plugin);
        }
    }

    public QDEndpoint user(String user) {
        if (user == null)
            throw new NullPointerException();
        synchronized (lock) {
            this.user = user;
            updateUserAndPasswordImpl(connectors);
        }
        return this;
    }

    public QDEndpoint password(String password) {
        if (password == null)
            throw new NullPointerException();
        synchronized (lock) {
            this.password = password;
            updateUserAndPasswordImpl(connectors);
        }
        return this;
    }

    public final QDEndpoint addConnectors(Collection<MessageConnector> connectors) {
        synchronized (lock) {
            if (closed)
                return this;
            addConnectorsImpl(connectors);
            onConnectorsChanged();
        }
        return this;
    }

    // extension point, SYNC(lock)
    protected void addConnectorsImpl(Collection<MessageConnector> connectors) {
        updateUserAndPasswordImpl(connectors);
        this.connectors.addAll(connectors);
        for (MessageConnectorListener listener : connectorListeners) {
            for (MessageConnector connector : connectors) {
                connector.addMessageConnectorListener(listener);
                listener.stateChanged(connector);
            }
        }
    }

    public final QDEndpoint removeConnectors(Collection<MessageConnector> connectors) {
        synchronized (lock) {
            if (closed)
                return this;
            removeConnectorsImpl(connectors);
            onConnectorsChanged();
        }
        return this;
    }

    // extension point, SYNC(lock)
    protected void removeConnectorsImpl(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            if (!connector.getUser().isEmpty())
                connector.setUser("");
            if (!connector.getPassword().isEmpty())
                connector.setPassword("");
        }
        this.connectors.removeAll(connectors);
        for (MessageConnectorListener listener : connectorListeners) {
            for (MessageConnector connector : connectors) {
                connector.removeMessageConnectorListener(listener);
            }
        }
    }

    // SYNC(lock)
    private void onConnectorsChanged() {
        for (Plugin plugin : plugins) {
            plugin.connectorsChanged(connectors);
        }
    }

    public QDEndpoint startConnectors() {
        synchronized (lock) {
        connectors:
            for (MessageConnector connector : connectors) {
                for (Plugin plugin : plugins) {
                    if (plugin.skipConnectorOnStart(connector))
                        continue connectors;
                }
                connector.start();
            }
        }
        return this;
    }

    public final void restartActiveConnectors() {
        synchronized (lock) {
            for (MessageConnector connector : connectors) {
                if (connector.isActive())
                    connector.restart();
            }
        }
    }

    public final void reconnectActiveConnectors() {
        synchronized (lock) {
            for (MessageConnector connector : connectors) {
                if (connector.isActive())
                    connector.reconnect();
            }
        }
    }

    public final void awaitProcessed() throws InterruptedException {
        for (MessageConnector connector : connectors)
            connector.awaitProcessed();
    }

    public final void stopConnectors() {
        MessageConnectors.stopMessageConnectors(connectors);
    }

    public final void stopConnectorsAndWait() throws InterruptedException {
        for (MessageConnector connector : connectors)
            connector.stopAndWait();
    }

    public final void stopConnectorsAndWaitUninterruptibly()  {
        for (MessageConnector connector : connectors)
            try {
                connector.stopAndWait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // reassert
            }
    }

    /**
     * Permanently closes all connectors and cleans up their resources.
     */
    public final void cleanupConnectors() {
        synchronized (lock) {
            if (connectors.isEmpty())
                return; // nothing to do
            cleanupConnectorsImpl(connectors);
            connectors.clear(); // clear list of connectors
            onConnectorsChanged();
        }
    }

    // extension point, SYNC(lock)
    protected void cleanupConnectorsImpl(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            connector.close();
        }
    }

    public QDEndpoint addMessageConnectionListener(MessageConnectorListener listener) {
        synchronized (lock) {
            if (closed)
                return this;
            MessageConnectors.addMessageConnectorListener(connectors, listener);
            connectorListeners.add(listener);
            for (MessageConnector connector : connectors)
                listener.stateChanged(connector);
        }
        return this;
    }

    public QDEndpoint removeMessageConnectionListener(MessageConnectorListener listener) {
        synchronized (lock) {
            MessageConnectors.removeMessageConnectorListener(connectors, listener);
            connectorListeners.remove(listener);
        }
        return this;
    }

    /**
     * This method registers monitoring task that is invoked periodically when "qds-monitoring" module
     * is in classpath.
     */
    public void registerMonitoringTask(Runnable task) {}

    /**
     * Closes this endpoint. All connectors are stopped and removed, all other associated resources are freed.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed)
                return;
            closed = true;
        }
        closeImpl();
    }

    // extension point, NON SYNCHRONIZED
    protected void closeImpl() {
        cleanupConnectors();
        connectorListeners.clear();
        collectorsValues.forEach(QDCollector::close);
    }

    private void collectorRedefined(QDContract contract) throws IllegalArgumentException {
        throw new IllegalArgumentException("Only one " + contract + " collector can be used");
    }

    // SYNC(lock)
    private void updateUserAndPasswordImpl(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            if (!user.isEmpty())
                connector.setUser(user);
            if (!password.isEmpty())
                connector.setPassword(password);
        }
    }

    public EndpointId getEndpointId() {
        return endpointId;
    }

    // ------------------ lock ------------------

    // just named class for nice stack-traces
    private static class Lock {}

    // ------------------ plugin ------------------

    public abstract static class Plugin {
        // extension point, SYNC(lock)
        public boolean skipConnectorOnStart(MessageConnector connector) {
            return false;
        }

        // extension point, SYNC(lock)
        public void connectorsChanged(List<MessageConnector> connectors) {
            // does nothing by default
        }
    }

    // ------------------ builder ------------------

    /**
     * Builder that creates instances of {@link QDEndpoint} objects.
     */
    @Service
    public static class Builder implements Cloneable {
        private static final AtomicInteger INSTANCES_NUMERATOR = new AtomicInteger();

        protected DataScheme scheme;
        protected List<QDCollector.Factory> collectors = new ArrayList<>(3);
        protected Properties props = new Properties();
        protected boolean withEventTimeSequence = false;
        protected boolean storeEverything = false;
        protected SymbolStriper striper;

        private String subscribeSupportPrefix;

        /**
         * Creates new builder. This method is for extension only.
         * Don't use it directly. Use {@link QDEndpoint#newBuilder()}.
         */
        protected Builder() {}

        @Override
        public Builder clone() {
            try {
                Builder clone = (Builder) super.clone();
                clone.collectors = new ArrayList<>(collectors);
                clone.props = new Properties();
                clone.props.putAll(props);
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Changes name that is used to distinguish multiple endpoints
         * in the same JVM in logs and in other diagnostic means.
         * This is a shortcut for
         * {@link #withProperty withProperty}({@link #NAME_PROPERTY NAME_PROPERTY},{@code name})
         */
        public final Builder withName(String name) {
            return withProperty(NAME_PROPERTY, name);
        }

        public final Builder withScheme(DataScheme scheme) {
            if (scheme == null)
                throw new NullPointerException();
            this.scheme = scheme;
            return this;
        }

        /**
         * @deprecated Use {@link #withCollectors(Collection)} and it has a more telling name
         *             and wider range of features.
         */
        public final Builder withContracts(EnumSet<QDContract> contracts) {
            return withCollectors(contracts);
        }

        /**
         * Creates specified collectors for this endpoint when it is {@link #build()}.
         */
        public final Builder withCollectors(Collection<? extends QDCollector.Factory> collectors) {
            this.collectors.addAll(collectors);
            return this;
        }

        /**
         * Specifies should endpoint support event time sequence or not.
         * @param withEventTime {@code true} if endpoint should support event time sequence.
         */
        public final Builder withEventTimeSequence(boolean withEventTime) {
            this.withEventTimeSequence = withEventTime;
            return this;
        }

        /**
         * Invoke this method to enable {@code store everything} mode in endpoint's collectors.
         */
        public final Builder withStoreEverything(boolean storeEverything) {
            this.storeEverything = storeEverything;
            return this;
        }

        public final Builder withStripe(String stripe) {
            return withProperty(DXFEED_STRIPE_PROPERTY, stripe);
        }

        public final Builder withStriper(SymbolStriper striper) {
            // Remove previous stripe property, if any
            this.props.remove(QDEndpoint.DXFEED_STRIPE_PROPERTY);
            this.striper = Objects.requireNonNull(striper, "striper");
            return this;
        }

        /**
         * Invoke this method with a property key prefix like "dxfeed.qd.subscribe." or
         * "multiplexor.qd.subscribe." to enable support for permanent subscription.
         * This method should be invoked before {@link #withProperties(Properties)}.
         * This prefix together with a {@link QDContract} name provides a way to define
         * a permanent subscription for a specified contract with the value of "&lt;records&gt; &lt;symbols&gt;".
         */
        public final Builder withSubscribeSupport(String prefix) {
            subscribeSupportPrefix = prefix;
            return this;
        }

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
            // Properties.stringPropertyNames() is properly synchronized to avoid ConcurrentModificationException.
            for (String key : props.stringPropertyNames()) {
                withProperty(key, props.getProperty(key));
            }
            return this;
        }

        /**
         * Returns true if the corresponding property key is supported.
         * @see #withProperty(String, String)
         */
        public boolean supportsProperty(String key) {
            return NAME_PROPERTY.equals(key) || DXFEED_STRIPE_PROPERTY.equals(key) ||
                (subscribeSupportPrefix != null && key.startsWith(subscribeSupportPrefix));
        }

        protected final String getOrCreateName() {
            String name = props.getProperty(NAME_PROPERTY);
            if (name != null)
                return name;
            int number = INSTANCES_NUMERATOR.getAndIncrement();
            return "qd" + (number == 0 ? "" : "-" + number);
        }

        protected final DataScheme getSchemeOrDefault() {
            return scheme == null ? QDFactory.getDefaultScheme() : scheme;
        }

        protected final SymbolStriper getStriperOrDefault() {
            String stripe = props.getProperty(DXFEED_STRIPE_PROPERTY);
            if (stripe != null) {
                // Presence of stripe property means that it was set after possible withStriper() method
                return SymbolStriper.definedValueOf(getSchemeOrDefault(), stripe);
            }
            return striper == null ? MonoStriper.INSTANCE : striper;
        }

        public QDEndpoint build() {
            QDStats rootStats = QDFactory.createStats(QDStats.SType.ANY, getSchemeOrDefault());
            QDEndpoint endpoint = new QDEndpoint(this, rootStats);
            subscribe(endpoint);
            return endpoint;
        }

        protected void subscribe(QDEndpoint endpoint) {
            if (subscribeSupportPrefix == null)
                return;
            for (Object oKey : props.keySet()) {
                String key = (String) oKey;
                if (!key.startsWith(subscribeSupportPrefix))
                    continue;
                String value = props.getProperty(key).trim();
                if (value.isEmpty())
                    continue;
                endpoint.log.info(endpoint.getName() + " with " + key + "=" + value);
                QDContract contract;
                try {
                    contract = QDContract.valueOf(
                        key.substring(subscribeSupportPrefix.length()).toUpperCase(Locale.US));
                } catch (IllegalArgumentException e) {
                    throw new InvalidFormatException("Unsupported contract name in property key '" + key + "'");
                }
                QDCollector collector = endpoint.getCollector(contract);
                if (collector == null)
                    throw new InvalidFormatException("Endpoint does not have " + collector + " collector to subscribe");
                String[] s = value.split("\\s+", 3);
                if (s.length < 2)
                    throw new InvalidFormatException("Property '" + key + "' shall have '<records> <symbols> [<date-time>]' value");

                DataScheme scheme = endpoint.getScheme();
                RecordOnlyFilter records = RecordOnlyFilter.valueOf(s[0], scheme);
                SymbolSetFilter symbols = SymbolSetFilter.valueOf(s[1], scheme);
                if (symbols.getSymbolSet() == null)
                    throw new InvalidFormatException("Symbol filter is not supported: " + s[1]);
                long millis = 0;
                if (s.length == 3) {
                    try {
                        millis = TimeFormat.DEFAULT.parse(s[2]).getTime();
                    } catch (InvalidFormatException e) {
                        throw new InvalidFormatException("Property '" + key + "' has wrong date-time value", e);
                    }
                }
                final long qdTime = (millis / 1000) << 32;
                final RecordBuffer buf = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
                for (int i = 0; i < scheme.getRecordCount(); i++) {
                    final DataRecord record = scheme.getRecord(i);
                    if (!records.acceptRecord(record))
                        continue;
                    symbols.getSymbolSet().examine((cipher, symbol) -> buf.add(record, cipher, symbol).setTime(qdTime));
                }
                QDAgent agent = collector.agentBuilder()
                    .withKeyProperties("agent=qd.subscribe")
                    .build();
                agent.setRecordListener(RecordListener.VOID);
                agent.addSubscription(buf);
                buf.release();
            }
        }
    }

    /**
     * A strategy that defines how to create {@link MessageConnector MessageConnectors},
     * which should be used {@link QDEndpoint} for {@link #connect(String)}.
     */
    public static interface ConnectorInitializer {
        /**
         * Creates and adds {@link MessageConnector} to the {@link QDEndpoint qdEndpoint}.
         * This method combining one of the following methods:
         * <ul>
         *     <li>
         *         {@link MessageConnectors#createMessageConnectors(ApplicationConnectionFactory, String)}
         *     </li>
         *     <li>
         *         {@link MessageConnectors#createMessageConnectors(ApplicationConnectionFactory, String, QDStats)}
         *     </li>
         *     <li>
         *         {@link MessageConnectors#createMessageConnectors(ConfigurableMessageAdapterFactory, String, QDStats)}
         *     </li>
         * </ul>
         * with {@link #addConnectors(Collection) qdEndpoint.addConnectors()}
         * @param qdEndpoint {@link QDEndpoint} for which are created and added {@link MessageConnector}
         * @param address specified address(es) for {@link MessageConnector}
         */
        public void createAndAddConnector(QDEndpoint qdEndpoint, String address);
    }
}
