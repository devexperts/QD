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

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.ConfigurableObject;
import com.devexperts.connector.proto.ConfigurationException;
import com.devexperts.connector.proto.EndpointId;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.fieldreplacer.FieldReplacersCache;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TypedMap;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * The <code>MessageAdapter</code> is a basic adapter of some entity to message API.
 * It implements both {@link MessageConsumer} and {@link MessageProvider} interfaces
 * with default behavior and is recommended for extension by specific adapters
 * (instead of pure implementations of corresponding interfaces). Certain QTP connectors
 * works only with <code>MessageAdapter</code> as a single representative of some entity.
 */
public abstract class MessageAdapter extends MessageConsumerAdapter implements MessageProvider, MessageAdapterMBean {
    private static final boolean SKIP_CORRUPTED_MESSAGES = SystemProperties.getBooleanProperty(MessageAdapter.class, "skipCorruptedMessages", false);
    private static final boolean SKIP_UNKNOWN_MESSAGES = SystemProperties.getBooleanProperty(MessageAdapter.class, "skipUnknownMessages", false);

    public static final String AUTHENTICATION_LOGIN_REQUIRED = "LOGIN ";

    /**
     * The <code>CloseListener</code> is used to notify QTP connector that
     * this message adapter was closed by some reason. Unless QTP connector
     * was closed in turn, it shall attempt to recreate message adapter and
     * reconnect as specified by its contract.
     */
    public interface CloseListener {
        public void adapterClosed(MessageAdapter adapter);
    }

    /**
     * The <code>Factory</code> performs creation of actual message agents
     * on demand from QTP connector. Unlike created agents, the factory itself
     * must be lightweight entity without any cleanup procedures.
     */
    public interface Factory {
        /**
         * Creates message adapter.
         * @param stats stats node for the adapter.
         * @return new message adapter.
         */
        public MessageAdapter createAdapter(QDStats stats);
    }

    /**
     * An abstract message adapter factory with ability to configure it with arbitrary keys and values.
     */
    public abstract static class ConfigurableFactory extends ConfigurableObject
        implements Factory, ConfigurableMessageAdapterFactory
    {
        private final Map<Class<?>, Object> endpoints = new ConcurrentHashMap<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public abstract MessageAdapter createAdapter(QDStats stats);

        /**
         * Creates a copy of this factory with the same configuration.
         */
        @Override
        public ConfigurableFactory clone() {
            return (ConfigurableFactory) super.clone();
        }

        /**
         * This implementation uses {@link #clone()} and spec
         * to set {@link MessageConnectors#FILTER_CONFIGURATION_KEY FILTER_CONFIGURATION_KEY} if spec is not empty.
         */
        @Override
        public final MessageAdapter.Factory createMessageAdapterFactory(String spec) throws AddressSyntaxException {
            if (spec == null || spec.isEmpty())
                return this;
            ConfigurableFactory clone = clone();
            try {
                clone.setConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY, spec);
            } catch (ConfigurationException e) {
                throw new AddressSyntaxException(e.getMessage(), e.getCause());
            }
            return clone;
        }

        /**
         * Returns description of this factory for management and logging purposes.
         */
        @Override
        public String toString() {
            String desc = getClass().getSimpleName();
            String suffix = "Factory";
            if (desc.equals(suffix) && getClass().getEnclosingClass() != null) {
                // unwrap "Factory" inner class to the name of enclosing class
                desc = getClass().getEnclosingClass().getSimpleName();
            } else if (desc.length() > suffix.length() && desc.endsWith(suffix)) {
                // .. or drop "Factory" suffix from the simple class name
                desc = desc.substring(0, desc.length() - suffix.length());
            }
            suffix = "Adapter";
            if (desc.length() > suffix.length() && desc.endsWith(suffix)) {
                // drop "Adapter" suffix if present, too
                desc = desc.substring(0, desc.length() - suffix.length());
            }
            return desc;
        }

        public <T> void setEndpoint(Class<?> endpointClass, T endpointInstance) {
            Object old = endpoints.put(endpointClass, endpointInstance);
            if (old != null)
                throw new IllegalStateException("Endpoint of class " + endpointClass + " was already set");
        }

        @SuppressWarnings("unchecked")
        public <T> T getEndpoint(Class<T> endpointClass) {
            return (T) endpoints.get(endpointClass);
        }
    }

    /**
     * An abstract message adapter factory for {@link QDEndpoint} or a bunch of {@link QDCollector} instances.
     * It has built in support of a {@link MessageConnectors#FILTER_CONFIGURATION_KEY FILTER_CONFIGURATION_KEY}
     * that is aligned with a legacy {@link ConfigurableMessageAdapterFactory} interface.
     */
    public abstract static class AbstractFactory extends ConfigurableFactory {
        protected final QDEndpoint endpoint;
        protected final DataScheme scheme;
        protected final QDTicker ticker;
        protected final QDStream stream;
        protected final QDHistory history;

        // Result filter === rawFilter & initialFilter & stripe
        @Nonnull
        @GuardedBy("this")
        protected QDFilter filter;

        @Nonnull
        @GuardedBy("this")
        protected QDFilter stripe;

        @Nonnull
        private final QDFilter initialFilter;

        // Filter provided via setFilter
        private QDFilter rawFilter;

        /**
         * Creates new factory. Accepts <code>null</code> parameters.
         * @param ticker QDTicker to use.
         * @param stream QDStream to use.
         * @param history QDHistory to use.
         * @param filter SubscriptionFilter to use.
         */
        protected AbstractFactory(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter) {
            this.endpoint = null;
            this.scheme = getCommonScheme(ticker, stream, history);
            this.ticker = ticker;
            this.stream = stream;
            this.history = history;
            this.initialFilter = QDFilter.fromFilter(filter, getCommonScheme(ticker, stream, history));
            this.stripe = QDFilter.ANYTHING;
            this.filter = calculateResultFilter();
        }

        protected AbstractFactory(QDEndpoint endpoint, SubscriptionFilter filter) {
            this.endpoint = endpoint;
            this.scheme = endpoint.getScheme();
            this.ticker = endpoint.getTicker();
            this.stream = endpoint.getStream();
            this.history = endpoint.getHistory();
            this.initialFilter = QDFilter.fromFilter(filter, scheme);
            this.stripe = QDFilter.ANYTHING;
            this.filter = calculateResultFilter();
            setEndpoint(QDEndpoint.class, endpoint);
        }

        public DataScheme getScheme() {
            return scheme;
        }

        public QDCollector[] getCollectors() {
            return new QDCollector[] { ticker, stream, history };
        }

        /**
         * Returns current filter of this factory. It is a result of combining initial filter and stripe
         * with the filter set by {@link #setFilter(String)} method.
         * @return current filter of this factory.
         */
        public synchronized QDFilter getFilter() {
            filter = filter.getUpdatedFilter(); // switch to the most recent filter if it was updated
            return filter;
        }

        /**
         * Sets additional filter for this factory.
         * This filter is parsed and added using "and" operation to the initial filter that was specified in constructor.
         * @param filterString filter for this message adapter.
         * @throws FilterSyntaxException if filter string is invalid.
         */
        @Configurable(description = "default filter for all channels")
        public synchronized void setFilter(String filterString) throws FilterSyntaxException {
            rawFilter = (filterString == null || filterString.isEmpty()) ?
                null : CompositeFilters.valueOf(filterString, scheme);
            filter = calculateResultFilter();
        }

        @Nonnull
        public synchronized QDFilter getStripe() {
            return stripe;
        }

        @Nonnull
        public synchronized String getStripeFilter() {
            return stripe.toString();
        }

        /**
         * Sets stripe (represented as QDFilter) for this factory.
         * @param stripeString stripe for this message adapter.
         * @throws FilterSyntaxException if stripe filter specification is invalid.
         */
        @SuppressWarnings("unused")
        @Configurable(description = "default stripe for all channels")
        public synchronized void setStripeFilter(String stripeString) {
            stripe = CompositeFilters.valueOf(stripeString, scheme);
            filter = calculateResultFilter();
        }

        private QDFilter calculateResultFilter() {
            // Method makeAnd efficiently handles nulls and QDFilter.ANYTHING
            return CompositeFilters.makeAnd(CompositeFilters.makeAnd(rawFilter, initialFilter), stripe);
        }

        @Override
        public String toString() {
            String s = super.toString();
            String filterString;
            synchronized (this) {
                if (filter == QDFilter.ANYTHING)
                    return s;
                filterString = filter.toString();
            }
            // Replace dots in filter name with underscores, so that it can be safely used to define log categories
            return s + "[" + filterString.replace('.', '_') + "]";
        }
    }

    /**
     * Returns common data scheme or throws IllegalArgumentException.
     * Accepts {@code null} parameters.
     * @param c1 QDCollector
     * @param c2 QDCollector
     * @param c3 QDCollector
     * @return common data scheme.
     * @throws IllegalArgumentException if collectors have different schemes or all are {@code null}.
     */
    public static DataScheme getCommonScheme(QDCollector c1, QDCollector c2, QDCollector c3) {
        DataScheme s1 = c1 != null ? c1.getScheme() : null;
        DataScheme s2 = c2 != null ? c2.getScheme() : null;
        DataScheme s3 = c3 != null ? c3.getScheme() : null;
        DataScheme s = s1 != null ? s1 : (s2 != null ? s2 : s3);
        if (s == null)
            throw new IllegalArgumentException("All schemes are null.");
        if (s != s2 && s2 != null || s != s3 && s3 != null)
            throw new IllegalArgumentException("The schemes are different.");
        return s;
    }

    private enum State {
        NEW, STARTED, CLOSED
    }

    private static final Logging log = Logging.getLogging(MessageAdapter.class);

    // ------------------------- instance fields -------------------------

    private final QDEndpoint endpoint;

    private volatile State state = State.NEW;
    private boolean markedForImmediateRestart;
    private final AtomicLong mask = new AtomicLong();

    private final QDStats stats;

    protected boolean useDescribeProtocol;
    protected boolean doNotCloseOnErrors; // it is used by dump tool to keep MA open on errors
    protected volatile CloseListener closeListener;
    protected volatile MessageListener messageListener;

    @Nullable private TypedMap connectionVariables;
    protected EndpointId remoteEndpointId;

    private ProtocolOption.Set remoteOptSet = ProtocolOption.EMPTY_SET; // options supported by remote side

    private MasterMessageAdapter master;
    private AuthManager authManager;
    private LoginManager loginManager;

    // ------------------------- constructors -------------------------

    protected MessageAdapter(QDEndpoint endpoint, QDStats stats) {
        this.endpoint = endpoint;
        this.stats = stats;
    }

    protected MessageAdapter(QDStats stats) {
        this(null, stats);
    }

    // ------------------------- instance methods -------------------------

    public abstract DataScheme getScheme();

    /**
     * Returns endpoint of this message adapter.
     */
    public QDEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Field Replacers specification.
     */
    public FieldReplacersCache getFieldReplacer() {
        return null;
    }

    /**
     * Returns description of this <code>MessageAdapter</code> for management and logging purposes.
     */
    @Override
    public String toString() {
        String desc = getClass().getSimpleName();
        String suffix = "Adapter";
        return desc.endsWith(suffix) ? desc.substring(0, desc.length() - suffix.length()) : desc;
    }

    protected void notifyListener() {
        MessageListener listener = messageListener; // Atomic read.
        if (listener != null)
            listener.messagesAvailable(this);
    }

    public void setCloseListener(CloseListener listener) {
        this.closeListener = listener;
    }

    /**
     * Returns next time when {@link #retrieveMessages(MessageVisitor)} shall be called.
     * This method is overridden when a message has to be generated at a certain time in the future.
     * For immediate message delivery use {@link #addMask(long)}, which
     * immediately calls {@link #notifyListener()} as needed.
     *
     * <p> This implementation returns {@link Long#MAX_VALUE}.
     *
     * @param currentTime the current value of {@link System#currentTimeMillis()}
     * @return next time when {@link #retrieveMessages(MessageVisitor)} shall be called
     *         even if the mask of messages for immediate delivery is empty.
     */
    public long nextRetrieveTime(long currentTime) {
        return authManager != null ? authManager.getAuthDisconnectTime() : Long.MAX_VALUE;
    }

    public boolean supportsMixedSubscription() {
        return false;
    }

    // ========== Authentication support ==========

    boolean hasAuthRealm() {
        return authManager != null;
    }

    public void setAuthRealm(QDAuthRealm authRealm) {
        if (authRealm == null)
            return;
        if (loginManager != null || authManager != null)
            throw new IllegalStateException(); // at most one must be set at most once
        this.authManager = new AuthManager(this, authRealm);
    }

    public void setLoginHandler(QDLoginHandler loginHandler) {
        if (loginHandler == null)
            return;
        if (loginManager != null || authManager != null)
            throw new IllegalStateException(); // at most one must be set at most once
        this.loginManager = new LoginManager(loginHandler, this, (endpoint != null) ? endpoint.getName() : null);
    }

    // ========== Connection variables support ==========

    /**
     * Returns per-connection variables for this message adapter.
     */
    @Nullable
    public TypedMap getConnectionVariables() {
        return connectionVariables;
    }

    /**
     * Sets per-connection variables for this message adapter.
     * This method may be invoked only once during a life-time of MessageAdapter.
     */
    public synchronized void setConnectionVariables(@Nonnull TypedMap connectionVariables) {
        if (this.connectionVariables != null)
            throw new IllegalStateException("Connection variables were already set");
        this.connectionVariables = connectionVariables;
    }

    protected String getRemoteHostAddress() {
        return connectionVariables == null ? null : connectionVariables.get(TransportConnection.REMOTE_HOST_ADDRESS_KEY);
    }

    public EndpointId getRemoteEndpointId() {
        return remoteEndpointId;
    }

    // ========== Describe protocol support ==========

    /**
     * Invocation of this method causes this {@code MessageAdapter} to send
     * {@link MessageType#DESCRIBE_PROTOCOL DESCRIBE_PROTOCOL} message if it is capable of doing so.
     * It should be invoked once before calling {@link #start()}.
     * The code that calls this method shall also consult {@link #nextRetrieveTime(long)}.
     */
    public void useDescribeProtocol() {
        if (isAlive())
            throw new IllegalStateException("Must be invoked before start");
        useDescribeProtocol = true;
    }

    protected long retrieveDescribeProtocolMessage(MessageVisitor visitor, long mask) {
        if (!hasMessageMask(mask, MessageType.DESCRIBE_PROTOCOL))
            return mask;
        ProtocolDescriptor desc = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        prepareCommonProtocolDescriptor(desc);
        if (endpoint != null)
            desc.setEndpointId(endpoint.getEndpointId());
        updateManagerState(true);
        if (authManager == null || !authManager.authenticatePreparing())
            prepareProtocolDescriptor(desc);
        else
            prepareAuthenticateProtocolDescriptor(desc);
        if (master != null)
            master.augmentProtocolDescriptor(desc);
        visitor.visitDescribeProtocol(desc);
        mask = clearMessageMask(mask, MessageType.DESCRIBE_PROTOCOL);
        updateManagerState(false);
        return mask;
    }

    /**
     * Updates the adapter configuration.
     * @param session the unique session.
     */
    public void reinitConfiguration(AuthSession session) {
    }

    /**
     * Prepares outgoing protocol descriptor.
     * Implementers should call {@code super.prepareProtocolDescriptor(desc)}.
     * @param desc outgoing protocol descriptor
     */
    public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        if (loginManager != null)
            loginManager.prepareProtocolDescriptor(desc);
        if (authManager != null && authManager.firstAuthProtocolWasSent())
            desc.setProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY, "");
    }

    protected void prepareAuthenticateProtocolDescriptor(ProtocolDescriptor desc) {
        desc.setProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY, authManager.getReason());
    }

    private void prepareCommonProtocolDescriptor(ProtocolDescriptor desc) {
        if (endpoint != null) {
            Map<String, String> descriptorProperties = endpoint.getDescriptorProperties();
            for (Map.Entry<String, String> entry : descriptorProperties.entrySet()) {
                String key = entry.getKey();
                if (desc.getProperty(key) == null) // don't override any properties just in case...
                    desc.setProperty(key, entry.getValue());
            }
        }
        desc.addSend(desc.newMessageDescriptor(MessageType.PART));
        desc.addReceive(desc.newMessageDescriptor(MessageType.PART));
    }

    /**
     * Process incoming protocol descriptor.
     * Implementers should call {@code super.processDescribeProtocol(desc)}.
     * @param desc incoming protocol descriptor
     * @param logDescriptor when protocol description shall be logged.
     */
    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
        remoteOptSet = ProtocolOption.parseProtocolOptions(desc.getProperty(ProtocolDescriptor.OPT_PROPERTY));
        if (logDescriptor) {
            String authProp = desc.getProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY);
            if (authProp != null && !authProp.isEmpty()) {
                logAuthRequestDescriptor(desc);
            } else {
                logIncomingProtocolDescriptor(desc);
            }
        }
        remoteEndpointId = desc.getEndpointId();
        String auth = desc.getProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY);
        if (auth != null) {
            if (authManager != null) {
                authManager.authenticate(AuthToken.valueOf(auth), connectionVariables);
            } else {
                if (connectionVariables != null)
                    connectionVariables.set(TransportConnection.SUBJECT_KEY, AuthToken.valueOf(auth));
            }
        }
        String authInfo = desc.getProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY);
        if (loginManager != null && authInfo != null) {
            if (authInfo.isEmpty())
                loginManager.completeLogin();
            else
                loginManager.login(authInfo);
        }
    }

    /**
     * Deprecated. Left to force compilation failure for overriding classes.
     * @deprecated See {@link #processDescribeProtocol(ProtocolDescriptor, boolean)}
     */
    public final void processDescribeProtocol(ProtocolDescriptor desc) {
        throw new UnsupportedOperationException();
    }

    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        return true;
    }

    protected ProtocolOption.Set getRemoteOptSet() {
        return remoteOptSet;
    }

    protected void setRemoteOptSet(ProtocolOption.Set optSet) {
        remoteOptSet = Objects.requireNonNull(optSet);
    }

    private void updateManagerState(boolean beforePreparing) {
        if (loginManager != null)
            loginManager.updateState(beforePreparing);
        else if (authManager != null)
            authManager.updateState(beforePreparing);
    }

    private void logAuthRequestDescriptor(ProtocolDescriptor desc) {
        StringBuilder sb = new StringBuilder(toString());
        sb.append(" received authentication request ").append(desc);
        String host = LogUtil.hideCredentials(getRemoteHostAddress());
        if (host != null)
            sb.append(" from ").append(host);
        log.info(sb.toString());
    }

    private void logIncomingProtocolDescriptor(ProtocolDescriptor desc) {
        StringBuilder sb = new StringBuilder();
        sb.append(this).append(" received protocol descriptor ").append(desc);
        String host = LogUtil.hideCredentials(getRemoteHostAddress());
        if (host != null)
            sb.append(" from ").append(host);
        // For backwards-compatibility purposes a protocol descriptor that does not advertise any "send messages"
        // is always considered "compatible" (e.g. we don't know what is being sent and assume we are Ok),
        // so no warning is given in this case. See QD-545 for details and rationale
        if (desc.getSendMessages().isEmpty() || isProtocolDescriptorCompatible(desc)) {
            log.info(sb.toString());
        } else {
            sb.append("\n!!! IT IS NOT A COMPATIBLE PROTOCOL !!!");
            sb.append(" Maybe connection was established to the wrong host or port?");
            log.warn(sb.toString());
        }
    }

    // ========== QDStats support ==========

    public QDStats getStats() {
        return stats;
    }

    // ========== Error Handling ==========

    @Override
    public void handleCorruptedStream() {
        super.handleCorruptedStream();
        if (!doNotCloseOnErrors)
            close();
    }

    @Override
    public void handleCorruptedMessage(int messageTypeId) {
        super.handleCorruptedMessage(messageTypeId);
        if (!doNotCloseOnErrors && !SKIP_CORRUPTED_MESSAGES)
            close();
    }

    @Override
    public void handleUnknownMessage(int messageTypeId) {
        super.handleUnknownMessage(messageTypeId);
        if (!doNotCloseOnErrors && !SKIP_UNKNOWN_MESSAGES)
            close();
    }

    // ========== MessageProvider Implementation ==========

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        if (authManager != null && System.currentTimeMillis() > authManager.getAuthDisconnectTime())
            close();
        return false;
    }

    /**
     * Note: this method SHOULD be called before calling .start()
     * - it does not give immediate notification regarding the pending messages
     */
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    // ========== Session API ==========

    /**
     * Returns {@code true} when this message adapter is alive
     * (was {@link #start() started} and was not {@link #close() closed} yet).
     */
    @Override
    public final boolean isAlive() {
        // Note: this method much be lock-free (it is used under AgentChannel lock)
        return state == State.STARTED;
    }

    public final boolean isClosed() {
        return state == State.CLOSED;
    }

    public final String getStatus() {
        return state.toString();
    }

    /**
     * Starts this adapter.
     * Each adapter can be started only once.
     * Closed adapter can not be started again.
     */
    public final void start() {
        reinitConfiguration(null);
        start(null);
    }

    /**
     * Same as start, but when slave is true it starts in slave mode. In slave mode there is a master message adapter
     * that prepares and sends protocol descriptor using {@link #prepareProtocolDescriptor(ProtocolDescriptor) prepareProtocolDescriptor}
     * method of this adapter.
     */
    public final synchronized void start(MasterMessageAdapter master) {
        if (state != State.NEW)
            throw new IllegalStateException(state == State.STARTED ?
                "Adapter is already started." : "Adapter is already closed.");
        state = State.STARTED;
        startImpl(master);
        if (mask.get() != 0)
            notifyListener();
    }

    /**
     * Closes this adapter. Once closed, adapter can not be started again and is useless.
     */
    @Override
    public final void close() {
        synchronized (this) {
            if (state == State.CLOSED)
                return;
            state = State.CLOSED;
            closeImpl();
        }
        CloseListener listener = closeListener; // Atomic read.
        if (listener != null)
            listener.adapterClosed(this);
        if (loginManager != null)
            loginManager.close();
        if (authManager != null)
            authManager.close();

    }

    /**
     * Marks this message adapter for immediate restart by underlying connection when it is closed.
     */
    public void markForImmediateRestart() {
        markedForImmediateRestart = true;
    }

    /**
     * Returns {@code true} if this message adapter was marked for immediate restart by underlying
     * connection when it is closed.
     */
    public boolean isMarkedForImmediateRestart() {
        return markedForImmediateRestart;
    }

    // assert Thread.holdsLock(this)
    protected void startImpl(MasterMessageAdapter master) {
        this.master = master;
        if (useDescribeProtocol)
            addMask(getMessageMask(MessageType.DESCRIBE_PROTOCOL));
    }

    // assert Thread.holdsLock(this)
    protected void closeImpl() {
    }

    private long reportedIgnoredMessages;

    protected void reportIgnoredMessage(String reason, MessageType message) {
        if ((reportedIgnoredMessages & (1L << message.ordinal())) != 0)
            return;
        reportedIgnoredMessages |= 1L << message.ordinal();
        log.warn("WARNING: " + reason + " -- ignoring " + message + " message");
    }

    // ========== Mask API ==========

    protected final long retrieveMask() {
        if (!isAlive())
            return 0;
        return mask.getAndSet(0);
    }

    /**
     * Adds bytes from argument mask to internal mask and notifies
     * listeners if internal mask changed.
     * @param mask changed bytes mask.
     * @return true, if internal mask changed and adapter {@link #isAlive is alive}.
     */
    protected final boolean addMask(long mask) {
        if (mask == 0)
            return false; // we know that we have absolutely nothing to do in this case
        while (true) {
            long oldMask = this.mask.get();
            long newMask = oldMask | mask;
            if (newMask == oldMask)
                return false; // we know that we have absolutely nothing to do in this case
            if (this.mask.compareAndSet(oldMask, newMask))
                break;
        }
        if (isAlive()) {
            notifyListener();
            return true;
        } else
            return false;
    }

    protected static long getMessageMask(MessageType message) {
        return 1L << message.getId();
    }

    protected static boolean hasMessageMask(long mask, MessageType message) {
        return (mask & getMessageMask(message)) != 0;
    }

    protected static long clearMessageMask(long mask, MessageType message) {
        return mask & ~getMessageMask(message);
    }

    protected static QDFilter intersectStripes(QDFilter stripe1, QDFilter stripe2) {
        if (stripe1 == null && stripe2 == null) {
            return QDFilter.ANYTHING;
        } else if (stripe1 == null || stripe1 == QDFilter.ANYTHING) {
            return stripe2;
        } else if (stripe2 == null || stripe2 == QDFilter.ANYTHING) {
            return stripe1;
        } else if (stripe1.toString().equals(stripe2.toString())) {
            return stripe1;
        }
        return QDFilter.ANYTHING;
    }
}
