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
package com.dxfeed.api.impl;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorListener;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.DxTimer;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.impl.RMISupportingDXEndpoint;
import com.devexperts.services.ServiceProvider;
import com.devexperts.services.Services;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.EventType;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import javax.annotation.concurrent.GuardedBy;

public class DXEndpointImpl extends ExtensibleDXEndpoint implements MessageConnectorListener, RMISupportingDXEndpoint {
    private static final boolean TRACE_LOG = DXEndpointImpl.class.desiredAssertionStatus();

    private static final Logging log = Logging.getLogging(DXEndpointImpl.class);

    private static final ExecutorProvider DEFAULT_EXECUTOR_PROVIDER =
        new ExecutorProvider("DXEndpoint-DXExecutorThread", log);

    private final Role role;
    private final QDEndpoint qdEndpoint;
    private final Properties props;
    private final DataScheme scheme;
    private final SymbolCodec codec;
    private final IndexedSet<Class<?>, EventDelegateSet<?, ?>> delegateSetsByEventType = IndexedSet.create((IndexerFunction<Class<?>, EventDelegateSet<?, ?>>) value -> value.eventType());
    private final EnumMap<QDContract, IndexedSet<DataRecord, List<EventDelegate<?>>>> delegateListsByContractAndRecord = new EnumMap<>(QDContract.class);
    private Set<Class<? extends EventType<?>>> eventTypes = new IndexedSet<Class<? extends EventType<?>>, Class<? extends EventType<?>>>(); // unmodifiable after construction

    private final List<PropertyChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();

    // this lock is taken from qdEndpoint and protects overall state
    private final Object lock;

    // separate lock to protect writes to state only. "updateState" method uses only this lock
    private final StateHolder stateHolder = new StateHolder();

    private RMIEndpointImpl rmiEndpoint;

    private volatile DXFeedImpl feed; // SYNC(lock) on create
    private volatile DXPublisherImpl publisher; // SYNC(lock) on create

    private String address; // SYNC(lock), != null when started (connect was called)

    public static MessageAdapter.AbstractFactory getMessageAdapterFactory(QDEndpoint qdEndpoint, Role role) {
        switch (role) {
        case FEED:
        case ON_DEMAND_FEED:
        case STREAM_FEED:
            return new DistributorAdapter.Factory(qdEndpoint, null);
        case PUBLISHER:
            return new AgentAdapter.Factory(qdEndpoint, null);
        case STREAM_PUBLISHER:
            return new AgentAdapter.Factory(qdEndpoint, null) {
                @Override
                public MessageAdapter createAdapter(QDStats stats) {
                    // TODO here we lost capability to configure channels via connector spec, if it ever worked here
                    return new AgentAdapter(endpoint, ticker, stream, history, getFilter(), getStripe(), stats) {
                        @Override
                        protected QDAgent createAgent(
                            QDCollector collector, SubscriptionFilter filter, String keyProperties)
                        {
                            QDAgent agent = super.createAgent(collector, filter, keyProperties);
                            agent.setBufferOverflowStrategy(QDAgent.BufferOverflowStrategy.BLOCK);
                            return agent;
                        }
                    };
                }
            };
        default:
            throw new UnsupportedOperationException("Connection is not supported in " + role + " role");
        }
    }

    private final ExecutorProvider executorProvider;
    private final ExecutorProvider.Reference executorReference;

    // Must call initConnectivity after this constructor
    protected DXEndpointImpl(Role role, QDEndpoint qdEndpoint, Properties props) {
        this.role = role;
        this.qdEndpoint = qdEndpoint;
        this.lock = qdEndpoint.getLock();
        this.props = props;
        this.scheme = qdEndpoint.getScheme();
        this.codec = scheme.getCodec();
        // Configures executor provide (either common built-in or custom)
        if (hasProperty(DXFEED_THREAD_POOL_SIZE_PROPERTY)) {
            executorProvider = new ExecutorProvider(Integer.decode(getProperty(DXFEED_THREAD_POOL_SIZE_PROPERTY)),
                "DXEndpoint-" + qdEndpoint.getName() +  "-DXExecutorThread", log);
        } else
            executorProvider = DEFAULT_EXECUTOR_PROVIDER;
        executorReference = executorProvider.newReference();
        // turns on stream wildcard support if enabled in properties
        if (Boolean.parseBoolean(props.getProperty(DXFEED_WILDCARD_ENABLE_PROPERTY, "false")))
            qdEndpoint.getStream().setEnableWildcards(true);
        // create delegates
        for (QDCollector collector : qdEndpoint.getCollectors()) {
            QDContract contract = collector.getContract();
            delegateListsByContractAndRecord.put(contract, IndexedSet.create((IndexerFunction<DataRecord, List<EventDelegate<?>>>) value -> value.get(0).getRecord()));
        }
        for (EventDelegateFactory factory : Services.createServices(EventDelegateFactory.class, null))
            for (int i = 0; i < scheme.getRecordCount(); i++)
                createDelegates(factory, scheme.getRecord(i));
        for (EventDelegateSet<?, ?> delegateSet : delegateSetsByEventType) {
            delegateSet.completeConstruction();
            eventTypes.add(delegateSet.eventType());
        }
        eventTypes = Collections.unmodifiableSet(eventTypes);
        // ---- the very last stuff (when everything is already constructed) ---
        // will cause notifications and updateState
        qdEndpoint.addMessageConnectionListener(this);
    }

    /**
     * @deprecated Use {@link #DXEndpointImpl(Role, QDEndpoint, Properties)} or {@link Builder} to
     * {@link Builder#build() build()} endpoints with custom properties.
     */
    public DXEndpointImpl(Role role, QDCollector... collectors) {
        this(role, QDEndpoint.newBuilder()
            .withScheme(collectors[0].getScheme())
            .build()
            .addCollectors(collectors),
            new Properties());
        initConnectivity();
    }

    private void initConnectivity() {
        // install RMI or default connection initializer
        if (role == Role.FEED) {
            // BuilderRMImpl provides its own RMIEndpoint before invocation
            if (rmiEndpoint == null)
                rmiEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, qdEndpoint,
                    getMessageAdapterFactory(qdEndpoint, Role.FEED), this);
        } else if (!qdEndpoint.hasConnectorInitializer())
            qdEndpoint.setConnectorInitializer(new DXConnectorInitializer(this));
        // connect if needed
        switch (role) {
        case FEED:
        case ON_DEMAND_FEED:
            if (hasProperty(DXFEED_USER_PROPERTY))
                qdEndpoint.user(getProperty(DXFEED_USER_PROPERTY));
            if (hasProperty(DXFEED_PASSWORD_PROPERTY))
                qdEndpoint.password(getProperty(DXFEED_PASSWORD_PROPERTY));
            if (hasProperty(DXFEED_ADDRESS_PROPERTY))
                connectImpl(getProperty(DXFEED_ADDRESS_PROPERTY), role == Role.FEED);
            break;
        case PUBLISHER:
            if (hasProperty(DXPUBLISHER_ADDRESS_PROPERTY))
                connectImpl(getProperty(DXPUBLISHER_ADDRESS_PROPERTY), true);
            break;
        }
    }

    public static EnumSet<QDContract> getRoleContracts(Role role) {
        return role == Role.STREAM_FEED || role == Role.STREAM_PUBLISHER ?
            EnumSet.of(QDContract.STREAM) :
            EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY);
    }

    @Override
    public Object getLock() {
        return lock;
    }

    @Override
    public QDEndpoint getQDEndpoint() {
        return qdEndpoint;
    }

    @Override
    public Role getRole() {
        return role;
    }

    public boolean isClosed() {
        return stateHolder.state == State.CLOSED;
    }

    @Override
    public State getState() {
        return stateHolder.state;
    }

    @Override
    public void addStateChangeListener(PropertyChangeListener listener) {
        stateChangeListeners.add(listener);
    }

    @Override
    public void removeStateChangeListener(PropertyChangeListener listener) {
        stateChangeListeners.remove(listener);
    }

    @Override
    public Set<Class<? extends EventType<?>>> getEventTypes() {
        return eventTypes;
    }

    @Override
    public DXFeed getFeed() {
        DXFeedImpl feed = this.feed;
        return feed == null ? createFeedInternal() : feed;
    }

    private DXFeed createFeedInternal() {
        synchronized (lock) {
            if (feed == null)
                feed = new DXFeedImpl(this);
            return feed;
        }
    }

    @Override
    public DXPublisher getPublisher() {
        DXPublisherImpl publisher = this.publisher;
        return publisher == null ? createPublisherInternal() : publisher;
    }

    private DXPublisher createPublisherInternal() {
        synchronized (lock) {
            if (publisher == null)
                publisher = new DXPublisherImpl(this);
            return publisher;
        }
    }

    public Executor getOrCreateExecutor() {
        return executorReference.getOrCreateExecutor();
    }

    public DxTimer getOrCreateTimer() {
        return DxTimer.getInstance();
    }

    @Override
    public ExecutorProvider.Reference getExecutorReference() {
        return executorReference;
    }

    @Override
    public DXEndpoint executor(Executor executor) {
        executorReference.setExecutor(executor);
        return this;
    }

    @Override
    public DXEndpoint user(String user) {
        qdEndpoint.user(user);
        return this;
    }

    @Override
    public DXEndpoint password(String password) {
        qdEndpoint.password(password);
        return this;
    }

    @Override
    public DXEndpoint connect(String address) {
        connectImpl(address, true);
        return this;
    }

    @Override
    public void reconnect() {
        synchronized (lock) {
            if (isClosed() || qdEndpoint.isClosed())
                return;
            qdEndpoint.reconnectActiveConnectors();
        }
    }

    // Note "start == false" is used internally to initializeConnectorsForAddress (only) in ON_DEMAND_FEED role
    private void connectImpl(String address, boolean start) {
        if (address == null)
            throw new NullPointerException();
        synchronized (lock) {
            if (stateHolder.state == State.CLOSED || address.equals(this.address))
                return;
            disconnect();
            qdEndpoint.initializeConnectorsForAddress(address);
            if (start) {
                qdEndpoint.startConnectors();
                setConnectedAddressSync(address);
                if (rmiEndpoint != null)
                    rmiEndpoint.setConnectedAddressSync(address);
            }
        }
    }

    // GuardedBy(lock)
    @Override
    public void setConnectedAddressSync(String address) {
        this.address = address;
        stateHolder.updateNow();
    }

    @Override
    public void disconnect() {
        synchronized (lock) {
            if (address == null)
                return;
            address = null;
            qdEndpoint.cleanupConnectors();
            if (rmiEndpoint != null)
                rmiEndpoint.disconnect();
            stateHolder.updateNow();
        }
    }

    @Override
    public void disconnectAndClear() {
        synchronized (lock) {
            // stop everything first
            qdEndpoint.stopConnectorsAndWaitUninterruptibly();
            // cleanup storage
            clearImpl();
            // disconnect to properly update address, change state, etc.
            disconnect();
        }
    }

    /**
     * This method expects connectors being stopped (disconnected) and also properly guarded by lock.
     * Although it will work even with running connectors and without locks, but will asynchronously
     * intersect with live data updates.
     */
    public void clearImpl() {
        clearCollector(qdEndpoint.getCollector(QDContract.TICKER), false);
        clearCollector(qdEndpoint.getCollector(QDContract.HISTORY), true);
    }

    private void clearCollector(QDCollector collector, boolean keepTime) {
        if (collector == null)
            return;
        RecordBuffer buf = RecordBuffer.getInstance();
        collector.examineData(buf);
        DXFeedImpl.clearDataInBuffer(buf, keepTime);
        buf.rewind();
        QDDistributor distributor = collector.distributorBuilder().build();
        try {
            distributor.process(buf);
        } finally {
            distributor.close();
        }
        buf.release();
    }

    @Override
    public void awaitNotConnected() throws InterruptedException {
        stateHolder.awaitNotConnected();
    }

    @Override
    public void awaitProcessed() throws InterruptedException {
        qdEndpoint.awaitProcessed();
    }

    @Override
    public void close() {
        if (prepareToClose()) {
            disconnect();
            if (feed != null)
                feed.closeImpl();
            closeRest();
        }
    }

    @Override
    public void closeAndAwaitTermination() throws InterruptedException {
        if (prepareToClose()) {
            synchronized (lock) {
                qdEndpoint.stopConnectorsAndWait();
                disconnect();
            }
            if (feed != null)
                feed.awaitTerminationAndCloseImpl();
            stateHolder.awaitClosed();
            closeRest();
        }
    }

    private boolean prepareToClose() {
        State oldState = makeClosed();
        if (oldState == State.CLOSED)
            return false;
        return true;
    }

    private void closeRest() {
        if (publisher != null)
            publisher.closeImpl();
        qdEndpoint.close();
        // Close executorReference as the last step. We should not need executor anymore
        executorReference.close();
    }

    private void fireStateChangeEvent(State oldState, State newState) {
        if (stateChangeListeners.isEmpty())
            return;
        PropertyChangeEvent event = new PropertyChangeEvent(this, "state", oldState, newState);
        for (PropertyChangeListener listener : stateChangeListeners)
            try {
                listener.propertyChange(event);
            } catch (Throwable t) {
                log.error("Exception in DXEndpoint state change listener", t);
            }
    }

    private State makeClosed() {
        synchronized (lock) {
            return stateHolder.makeClosed();
        }
    }

    public boolean hasProperty(String key) {
        return props.getProperty(key) != null;
    }

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    public Set<QDContract> getContracts() {
        return qdEndpoint.getContracts();
    }

    public QDCollector getCollector(QDContract contract) {
        return qdEndpoint.getCollector(contract);
    }

    public int encode(String symbol) {
        return codec.encode(symbol);
    }

    public String decode(int cipher, String symbol) {
        return codec.decode(cipher, symbol);
    }

    public EventDelegateSet<?, ?> getDelegateSetByEventType(Class<?> eventType) {
        return delegateSetsByEventType.getByKey(eventType);
    }

    public List<EventDelegate<?>> getDelegateListByContractAndRecord(QDContract contract, DataRecord record) {
        return delegateListsByContractAndRecord.get(contract).getByKey(record);
    }

    @Override
    public String toString() {
        return "DXEndpoint{" +
            "role=" + role +
            ", scheme=" + scheme.getClass().getSimpleName() +
            ", address=" + LogUtil.hideCredentials(address) +
            (isClosed() ? ", closed" : "") +
            '}';
    }

    public RMIEndpoint getRMIEndpoint() {
        return rmiEndpoint;
    }

    private void createDelegates(EventDelegateFactory factory, DataRecord record) {
        Collection<EventDelegate<?>> delegates = role == Role.STREAM_FEED || role == Role.STREAM_PUBLISHER ?
            factory.createStreamOnlyDelegates(record) : factory.createDelegates(record);
        if (delegates == null)
            return;
        delegates.forEach(this::registerDelegate);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void registerDelegate(EventDelegate<?> delegate) {
        QDContract contract = delegate.getContract();
        if (qdEndpoint.getCollector(contract) == null)
            return;
        EventDelegateSet delegateSet = delegateSetsByEventType.getByKey(delegate.getEventType());
        if (delegateSet == null)
            delegateSetsByEventType.add(delegateSet = delegate.createDelegateSet());
        try {
            delegateSet.add(delegate);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Cannot mix events of incompatible types", e);
        }
        IndexedSet<DataRecord, List<EventDelegate<?>>> lists = delegateListsByContractAndRecord.get(contract);
        List<EventDelegate<?>> delegateList = lists.getByKey(delegate.getRecord());
        if (delegateList == null) {
            delegateList = new ArrayList<>(1);
            delegateList.add(delegate);
            lists.add(delegateList);
        } else
            delegateList.add(delegate);
    }

    @Override
    public void stateChanged(MessageConnector connector) {
        // This method is not synchronized to avoid deadlocks and will recompute state in different thread
        stateHolder.scheduleUpdate();
    }

    private static final Set<String> SUPPORTED_PROPERTIES = new LinkedHashSet<>(Arrays.asList(
        DXFEED_PROPERTIES_PROPERTY,
        DXFEED_THREAD_POOL_SIZE_PROPERTY,
        DXFEED_AGGREGATION_PERIOD_PROPERTY,
        DXFEED_ADDRESS_PROPERTY,
        DXFEED_USER_PROPERTY,
        DXFEED_PASSWORD_PROPERTY,
        DXFEED_WILDCARD_ENABLE_PROPERTY,
        DXFEED_STRIPE_PROPERTY,
        DXENDPOINT_EVENT_TIME_PROPERTY,
        DXENDPOINT_STORE_EVERYTHING_PROPERTY,
        DXSCHEME_NANO_TIME_PROPERTY,
        DXPUBLISHER_PROPERTIES_PROPERTY,
        DXPUBLISHER_ADDRESS_PROPERTY,
        DXPUBLISHER_THREAD_POOL_SIZE_PROPERTY
    ));

    private static final Set<String> MASKED_PROPERTIES = new HashSet<>(Arrays.asList(
        DXFEED_USER_PROPERTY,
        DXFEED_PASSWORD_PROPERTY
    ));

    private static boolean supportsProperty(String key) {
        return SUPPORTED_PROPERTIES.contains(key)
            || key.startsWith(DXSCHEME_ENABLED_PROPERTY_PREFIX);
    }

    @ServiceProvider
    public static class BuilderImpl extends Builder {
        private final Properties props = new Properties();
        private QDEndpoint.Builder qdEndpointBuilder = QDEndpoint.newBuilder();

        public BuilderImpl() {
            updateSubscribeSupport();
        }

        private void updateSubscribeSupport() {
            // support permanent subscription for FEED role
            qdEndpointBuilder.withSubscribeSupport(role == Role.FEED ? "dxfeed.qd.subscribe." : null);
        }

        @Override
        public Builder withRole(Role role) {
            super.withRole(role);
            updateSubscribeSupport();
            return this;
        }

        @Override
        public Builder withProperty(String key, String value) {
            if (key == null || value == null)
                throw new NullPointerException();
            if (supportsProperty(key)) {
                // store property locally... pass to qdEndpointBuilder in build() method
                props.setProperty(key, value);
            }
            return this;
        }

        @Override
        public boolean supportsProperty(String key) {
            return DXEndpointImpl.supportsProperty(key) ||
                qdEndpointBuilder.supportsProperty(key);
        }

        private void loadPropertiesDefaults(Properties defaultProps, boolean ignoreName) {
            // Properties.stringPropertyNames() is properly synchronized to avoid ConcurrentModificationException.
            for (String key : defaultProps.stringPropertyNames()) {
                if (ignoreName && key.equals(NAME_PROPERTY))
                    continue;
                String value = defaultProps.getProperty(key);
                if (value != null && !props.containsKey(key))
                    withProperty(key, value);
            }
        }

        private void loadPropertiesDefaultsFromStream(InputStream in, String name, String propFileKey) {
            if (in == null)
                return;
            log.info("DXEndpoint is loading properties from " + name);
            Properties props = new Properties();
            try {
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                failedToLoadFrom(name, propFileKey, e);
                return;
            }
            loadPropertiesDefaults(props, false);
        }

        private void loadPropertiesDefaultsFromFile(String fileName, String propFileKey) {
            try {
                loadPropertiesDefaultsFromStream(new URLInputStream(fileName), fileName, propFileKey);
            } catch (IOException e) {
                failedToLoadFrom("file '" + fileName + "'", propFileKey, e);
            }
        }

        private void failedToLoadFrom(String name, String propFileKey, IOException e) {
            log.error("Failed to load " + propFileKey + " from " + LogUtil.hideCredentials(name), e);
        }

        @Override
        public DXEndpoint build() {
            loadProperties();
            // Create scheme
            DataScheme scheme = QDFactory.getDefaultScheme();
            if (scheme == DXFeedScheme.getInstance()) {
                scheme = DXFeedScheme.withProperties(new SchemeProperties(props));
            } else if (scheme instanceof ConfigurableDataScheme) {
                scheme = ((ConfigurableDataScheme) scheme).withProperties(props);
            }
            // Resolve properties
            SymbolStriper striper = SymbolStriper.definedValueOf(scheme,
                props.getProperty(DXFEED_STRIPE_PROPERTY, MonoStriper.MONO_STRIPER_NAME));
            boolean isEventTime = Boolean.parseBoolean(
                props.getProperty(DXENDPOINT_EVENT_TIME_PROPERTY, "false"));
            boolean isStoreEverything = Boolean.parseBoolean(
                props.getProperty(DXENDPOINT_STORE_EVERYTHING_PROPERTY, "false"));

            // create QD endpoint
            QDEndpoint qdEndpoint = qdEndpointBuilder
                .withProperties(props)
                .withCollectors(getRoleContracts(role))
                .withScheme(scheme)
                .withEventTimeSequence(isEventTime)
                .withStoreEverything(isStoreEverything)
                .withStriper(striper)
                .build();
            
            // log DXEndpoint properties
            for (Map.Entry<Object, Object> entry : new TreeMap<>(props).entrySet()) {
                String key = (String) entry.getKey();
                if (!qdEndpointBuilder.supportsProperty(key) && supportsProperty(key)) {
                    log.info(qdEndpoint.getName() + " DXEndpoint with " + key + "=" +
                        (MASKED_PROPERTIES.contains(key) ? "****" : props.getProperty(key)));
                }
            }
            // create DXEndpoint
            DXEndpointImpl dxEndpoint = new DXEndpointImpl(role, qdEndpoint, props);
            dxEndpoint.initConnectivity();
            return dxEndpoint;
        }

        private void loadProperties() {
            String propFileKey;
            switch (role) {
            case FEED:
            case ON_DEMAND_FEED:
                propFileKey = DXFEED_PROPERTIES_PROPERTY;
                break;
            case PUBLISHER:
                propFileKey = DXPUBLISHER_PROPERTIES_PROPERTY;
                break;
            default:
                return; // properties are not supported
            }
            // resolve prop file name
            String fileName = props.getProperty(propFileKey);
            if (fileName == null)
                fileName = SystemProperties.getProperty(propFileKey, null);

            // load from file
            if (fileName != null)
                loadPropertiesDefaultsFromFile(fileName, propFileKey);

            // fill in last-resort defaults from system properties (but name)
            try {
                loadPropertiesDefaults(System.getProperties(), true);
            } catch (SecurityException e) {
                // just ignore exception if we cannot access system properties due to security manager
            }

            // and load very last defaults from resource if file was not explicitly specified
            if (fileName == null) {
                String resourceName = "/" + propFileKey;
                loadPropertiesDefaultsFromStream(DXEndpointImpl.class.getResourceAsStream(resourceName),
                    "resource '" + resourceName + "'", propFileKey);
            }
        }
    }

    @ServiceProvider
    public static class BuilderRMIImpl extends RMIEndpointImpl.Builder {

        private QDEndpoint.Builder qdEndpointBuilder = QDEndpoint.newBuilder();

        @Override
        public RMIEndpoint build() {
            DXEndpointImpl dxEndpoint = null;
            qdEndpointBuilder.withProperties(props);
            if (scheme == null) {
                scheme = QDFactory.getDefaultScheme();
                if (scheme == DXFeedScheme.getInstance())
                    scheme = DXFeedScheme.withProperties(new SchemeProperties(props));
                else if (scheme instanceof ConfigurableDataScheme)
                    scheme = ((ConfigurableDataScheme)scheme).withProperties(props);
            }
            qdEndpointBuilder.withScheme(scheme);
            if (dxRole != null)
                qdEndpointBuilder.withCollectors(getRoleContracts(dxRole));
            qdEndpointBuilder.withName(getOrCreateName());
            qdEndpointBuilder.withEventTimeSequence(
                Boolean.parseBoolean(props.getProperty(DXENDPOINT_EVENT_TIME_PROPERTY, "false")));
            QDEndpoint qdEndpoint = qdEndpointBuilder.build();
            if (dxRole != null) // create dxEndpoint if dxRole is set
                dxEndpoint = new DXEndpointImpl(dxRole, qdEndpoint, props);
            if (dxRole == Role.FEED) // feed needs RMI client
                side = side.withClient();
            RMIEndpointImpl rmiEndpoint = new RMIEndpointImpl(side, qdEndpoint,
                (dxRole != null) ? getMessageAdapterFactory(qdEndpoint, dxRole) : null, dxEndpoint);
            if (dxEndpoint != null) {
                dxEndpoint.rmiEndpoint = rmiEndpoint; // store rmiEndpoint's reference
                dxEndpoint.initConnectivity();
            }
            return rmiEndpoint;
        }

        @Override
        public boolean supportsProperty(String key) {
            return super.supportsProperty(key) ||
                DXEndpointImpl.supportsProperty(key) ||
                qdEndpointBuilder.supportsProperty(key);
        }
    }

    private class StateHolder implements Runnable {
        // SYNC(lock+this) on write to State.CLOSE; SYNC(this) on any other writes
        volatile State state = State.NOT_CONNECTED;
        private State lastFiredState = state;

        // at most one task is scheduled at any time
        private int scheduled; // > 0 when run() is scheduled to run or is running, increment on any change
        private volatile Thread processingThread; // current thread that processes notifications

        // SYNC(lock), for connect & disconnect method to immediately recompute state
        synchronized void updateNow() {
            if (state != State.CLOSED) {
                State computedState = computeState();
                if (state != computedState) {
                    state = computedState;
                    scheduleImpl();
                }
            }
        }

        synchronized void scheduleUpdate() {
            if (state != State.CLOSED)
                scheduleImpl();
        }

        @GuardedBy("this")
        private void scheduleImpl() {
            if (TRACE_LOG)
                log.trace("Schedule state update to " + state);
            if (scheduled++ > 0) {
                notifyAll(); // wakeup awaitInner
                return;
            }
            getOrCreateExecutor().execute(this);
        }

        @Override
        public void run() {
            int lastScheduled = 0;
            while (true) { // loop while we need to fire state change events
                State oldState;
                State newState;
                synchronized (this) {
                    state = computeState();
                    oldState = lastFiredState;
                    newState = state;
                    if (newState == oldState && scheduled == lastScheduled) {
                        // no change in state -- leave event processing loop
                        processingThread = null;
                        scheduled = 0;
                        notifyAll(); // wakeup awaitOuter
                        return;
                    }
                    lastScheduled = scheduled;
                    lastFiredState = newState;
                    // keep reference to processing thread to catch inner wait
                    processingThread = Thread.currentThread();
                }
                if (oldState != newState)
                    fireStateChangeEvent(oldState, newState);
            }
        }

        @GuardedBy("this")
        private State computeState() {
            if (state == State.CLOSED)
                return State.CLOSED;
            boolean hasConnecting = false;
            for (MessageConnector connector : qdEndpoint.getConnectors()) {
                switch (connector.getState()) {
                case CONNECTING:
                    hasConnecting = true;
                    break;
                case CONNECTED:
                    return State.CONNECTED;
                }
            }
            return hasConnecting ? State.CONNECTING : State.NOT_CONNECTED;
        }

        // SYNC(lock) (then syncs on this)
        synchronized State makeClosed() {
            State oldState = state;
            if (oldState != State.CLOSED) {
                state = State.CLOSED;
                scheduleImpl();
            }
            return oldState;
        }

        void awaitClosed() throws InterruptedException {
            await(State.CLOSED);
        }

        void awaitNotConnected() throws InterruptedException {
            await(State.NOT_CONNECTED);
        }

        private void await(State condition) throws InterruptedException {
            if (processingThread == Thread.currentThread())
                awaitInner(condition);
            else
                awaitOuter(condition);
        }

        private synchronized void awaitInner(State condition) throws InterruptedException {
            // reenter from inside of run() event processing loop - run inner processing loop and wait
            while (true) {
                state = computeState();
                if (isCondition(condition))
                    return;
                wait(); // wait until anything changes (see notifyAll in scheduleImpl)
            }
        }

        private synchronized void awaitOuter(State condition) throws InterruptedException {
            // just wait normally from another thread
            // wait until processed state is in expected condition and no further changes scheduled
            while (!isCondition(condition) || scheduled != 0)
                wait();
        }

        @GuardedBy("this")
        // true when computed state is as expected or closed
        private boolean isCondition(State condition) {
            return state == State.CLOSED || state == condition;
        }
    }
}
