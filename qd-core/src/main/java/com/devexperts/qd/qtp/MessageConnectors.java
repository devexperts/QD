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

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.codec.CodecFactory;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.ConfigurationException;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Factory and utility classes to work with message connectors.
 *
 * @see MessageConnector
 */
public class MessageConnectors {

    public static final ConfigurationKey<String> NAME_CONFIGURATION_KEY =
        ConfigurationKey.create("name", String.class, "name of this connection");
    public static final ConfigurationKey<String> FILTER_CONFIGURATION_KEY =
        ConfigurationKey.create("filter", String.class, "filter for this connection");
    public static final ConfigurationKey<String> STRIPE_FILTER_CONFIGURATION_KEY =
        ConfigurationKey.create("stripeFilter", String.class, "stripe filter for this connection");
    public static final ConfigurationKey<String> USER_CONFIGURATION_KEY =
        ConfigurationKey.create("user", String.class, "user name for this connection");
    public static final ConfigurationKey<String> PASSWORD_CONFIGURATION_KEY =
        ConfigurationKey.create("password", String.class, "password for this connection");
    public static final ConfigurationKey<String> FIELD_REPLACER_CONFIGURATION_KEY =
        ConfigurationKey.create("fieldReplacer", String.class, "input field replacers for this connection");
    public static final ConfigurationKey<Integer> MAX_CONNECTIONS_CONFIGURATION_KEY =
        ConfigurationKey.create("maxConnections", Integer.class, "max number of allowed connections");

    public static final TypedKey<Socket> SOCKET_KEY = new TypedKey<>();
    public static final TypedKey<QDStats> STATS_KEY = new TypedKey<>();
    public static final TypedKey<QDFilter> LOCAL_STRIPE_KEY = new TypedKey<>();

    private static final String HTTP_PREFIX = "http:";
    private static final String HTTPS_PREFIX = "https:";
    private static final String SERVER_SOCKET_PREFIX = "server:";
    private static final String CLIENT_SOCKET_PREFIX = "socket:";
    private static final String CONFIG_SUFFIX = ".config";

    /**
     * Wraps any {@link MessageAdapter.Factory} into {@link ConfigurableMessageAdapterFactory} if
     * necessary, so that it can be used with {@link #createMessageConnectors} method, but does not
     * accept any additional specification for factory.
     * @param factory {@link MessageAdapter.Factory} to wrap
     * @return {@link ConfigurableMessageAdapterFactory}
     * @deprecated This class has a deprecated return type. Use {@link #configurableFactory(MessageAdapter.Factory)}.
     */
    public static ConfigurableMessageAdapterFactory configurable(MessageAdapter.Factory factory) {
        return configurableFactory(factory);
    }

    /**
     * Wraps any {@link MessageAdapter.Factory} into {@link ConfigurableMessageAdapterFactory} if
     * necessary, so that it can be used with {@link #createMessageConnectors} method, but does not
     * accept any additional specification for factory.
     * This method return null when the argument is null.
     * @param factory {@link MessageAdapter.Factory} to wrap
     * @return {@link ConfigurableMessageAdapterFactory}
     */
    public static MessageAdapter.ConfigurableFactory configurableFactory(MessageAdapter.Factory factory) {
        return factory == null ? null : factory instanceof MessageAdapter.ConfigurableFactory ?
            (MessageAdapter.ConfigurableFactory) factory :
            new LegacyNonConfigurableFactory(factory);
    }

    public static ApplicationConnectionFactory applicationConnectionFactory(MessageAdapter.Factory factory) {
        return new MessageAdapterConnectionFactory(configurableFactory(factory));
    }

    public static ApplicationConnectionFactory applicationConnectionFactory(ConfigurableMessageAdapterFactory factory) {
        return new MessageAdapterConnectionFactory(configurableFactory(factory));
    }

    public static MessageAdapter.Factory retrieveMessageAdapterFactory(ApplicationConnectionFactory pFactory) {
        try {
            MessageAdapterConnectionFactory mapFactory = (MessageAdapterConnectionFactory) pFactory;
            return mapFactory.getMessageAdapterFactory();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Unsupported application connection class: " + pFactory.getClass().getName(), e);
        }
    }

    /**
     * Creates new connectors for specified addresses using specified
     * configurable adapter factory. The addresses string can contain one address or
     * multiple addresses to configure multiple connectors:
     * <ul>
     * <li><b>&lt;address&gt;</b>
     * <li><b>(&lt;address&gt;)(&lt;address&gt;)...</b> - first way to specify multiple addresses.
     * <li><b>&lt;address&gt;/&lt;address&gt;/...</b> - second way to specify multiple addresses.
     * </ul>
     * The individual address format is: <b>[&lt;spec&gt;@]&lt;connector&gt;[(&lt;name&gt;=&lt;value&gt;)[(...)]]</b>.
     * Spec (optional) is processed by the configurable message adapter factory.
     * Name-value pairs (optional) specify additional bean properties for connectors (see corresponding class definitions).
     * The individual connector format is one of:
     * <ul>
     * <li><b>[tls+][&lt;host&gt;[:&lt;port&gt;],[...,]]&lt;host&gt;:&lt;port&gt;</b> - for client TCP/IP socket.
     * See {@link ClientSocketConnector}.
     * <li><b>[tls+]:&lt;port&gt;</b> - for server TCP/IP socket.
     * See {@link ServerSocketConnector}.
     * </ul>
     * Note that last port number is mandatory, and it will be used for all listed hosts
     * unless overridden individually. Optional TLS (Transport Layer Security) specifier
     * turns on SSL/TLS for the corresponding connector.
     *
     * <p>Example of the addresses string is
     * <br><code>(equities@quote01:5001)(options@quote02:5001)(messages@m1,m2:5002)(:5003)</code>
     *
     * @param cFactory     Configurable message adapter factory.
     * @param addresses    The addresses string.
     * @param parentStats  Parent {@code QDStats} for all created connectors.
     * @return A list of messages connectors implementing {@link MessageConnector} interface
     * @throws NullPointerException   if any parameter is <code>null</code>
     * @throws AddressSyntaxException if address format is incorrect
     */
    // todo: revise documentation
    public static List<MessageConnector> createMessageConnectors(ConfigurableMessageAdapterFactory cFactory, String addresses, QDStats parentStats) throws AddressSyntaxException {
        return createConnectorsInternal(applicationConnectionFactory(cFactory), addresses, parentStats, getCurrentDirURL());
    }

    public static List<MessageConnector> createMessageConnectors(ApplicationConnectionFactory acFactory, String addresses, QDStats stats) throws AddressSyntaxException {
        return createConnectorsInternal(acFactory, addresses, stats, getCurrentDirURL());
    }

    public static List<MessageConnector> createMessageConnectors(ApplicationConnectionFactory acFactory, String addresses) throws AddressSyntaxException {
        return createConnectorsInternal(acFactory, addresses, QDStats.VOID, getCurrentDirURL());
    }

    private static URL getCurrentDirURL() {
        URL base;
        try {
            base = new File(".").toURL();
        } catch (MalformedURLException e) {
            throw new AssertionError();
        }
        return base;
    }

    private static List<MessageConnector> createConnectorsInternal(ApplicationConnectionFactory acFactory, String addresses, QDStats parentStats, URL base) throws AddressSyntaxException {
        if (acFactory == null)
            throw new NullPointerException("acFactory");
        if (addresses == null)
            throw new NullPointerException("addresses");
        if (parentStats == null)
            throw new NullPointerException("parentStats");
        addresses = addresses.trim();
        List<MessageConnector> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        if (addresses.startsWith("(")) {
            for (String s : QDConfig.splitParenthesisSeparatedString(addresses))
                result.addAll(createConfiguredConnector(s, acFactory, parentStats, base, usedNames));
        } else {
            // old style or single connector
            ParsedAddress pa = parseAddress(addresses);
            if (!isLegacyAddress(addresses) || pa.address.startsWith(HTTP_PREFIX) || pa.address.startsWith(HTTPS_PREFIX)) {
                // single connector
                result.addAll(createConfiguredConnector(addresses, acFactory, parentStats, base, usedNames));
            } else {
                // Legacy -- split into multiple connectors with '/'
                for (StringTokenizer st = new StringTokenizer(addresses, "/"); st.hasMoreTokens();)
                    result.addAll(createConfiguredConnector(st.nextToken(), acFactory, parentStats, base, usedNames));
            }
        }
        return result;
    }

    // true when it is a '/' separated list of address, each containing at least one ':' in address
    private static boolean isLegacyAddress(String addresses) {
        if (addresses.startsWith("/") || addresses.endsWith("/"))
            return false; // legacy addresses cannot start or end with "/"
        for (StringTokenizer st = new StringTokenizer(addresses, "/"); st.hasMoreTokens();) {
            String addr = st.nextToken();
            ParsedAddress pa = parseAddress(addr);
            if (pa.address.indexOf(':') < 0)
                return false;
        }
        return true;
    }

    /**
     * @deprecated use {@link LogUtil#hideCredentials} instead
     */
    @Deprecated
    public static String maskAuthorizationData(String address) {
        return LogUtil.hideCredentials(address);
    }

    // Property key-value whose namespace is shared between Connector and ApplicationConnectionFactory
    private static class SharedProp {
        final String kv; // "key=value" string
        boolean used; // set to 'true' when it was used by ApplicationConnectionFactory

        private SharedProp(String kv) {
            this.kv = kv;
        }
    }

    private static class ParsedAddress {
        final String spec;
        final List<List<String>> codecs; // one List<String> per codec of format: [<codec-name>, <codec-property-1>, ..., <codec-property-N>]
        final String address;
        final List<SharedProp> props;

        ParsedAddress(String spec, List<List<String>> codecs, String address, List<SharedProp> props) {
            this.spec = spec;
            this.codecs = codecs;
            this.address = address;
            this.props = props;
        }
    }

    private static ParsedAddress parseAddress(String address) throws AddressSyntaxException {
        // Parse configurable message adapter factory specification in address
        String[] specSplit = QDConfig.splitParenthesisedStringAt(address, '@');
        String spec = specSplit.length == 1 ? "" : specSplit[0];
        address = specSplit.length == 1 ? address : specSplit[1];

        // Parse additional properties at the end of address
        List<String> propStrings = new ArrayList<>();
        try {
            address = QDConfig.parseProperties(address, propStrings);
        } catch (InvalidFormatException e) {
            throw new AddressSyntaxException(e.getMessage(), e);
        }

        // Parse codecs
        ArrayList<List<String>> codecs = new ArrayList<>();
        while (true) {
            String[] codecSplit = QDConfig.splitParenthesisedStringAt(address, '+');
            if (codecSplit.length == 1)
                break;
            String codecStr = codecSplit[0];
            address = codecSplit[1];
            ArrayList<String> codecInfo = new ArrayList<>();
            codecInfo.add(null); // reserving place for codec name
            codecStr = QDConfig.parseProperties(codecStr, codecInfo);
            codecInfo.set(0, codecStr);
            codecs.add(codecInfo);
        }
        Collections.reverse(codecs);

        List<SharedProp> props = new ArrayList<>();
        for (String s : propStrings)
            props.add(new SharedProp(s));
        return new ParsedAddress(spec, codecs, address, props);
    }

    @SuppressWarnings({"unchecked"})
    public static List<Class<? extends MessageConnector>> listMessageConnectors(ClassLoader loader) {
        ArrayList<Class<? extends MessageConnector>> result = new ArrayList<>();
        result.add(ClientSocketConnector.class);
        result.add(ServerSocketConnector.class);
        for (MessageConnectorFactory mcf : Services.createServices(MessageConnectorFactory.class, loader))
            result.add(mcf.getResultingClass());
        return result;
    }

    /**
     * Finds MessageConnector class by its short name (case-insensitive).
     *
     * @param name   MessageConnector class name.
     * @param loader Class loader to use.
     * @return corresponding Class&lt;? extends MessageConnector&gt; or null if no such
     *         MessageConnector was found.
     */
    public static Class<? extends MessageConnector> findMessageConnector(String name, ClassLoader loader) {
        for (Class<? extends MessageConnector> connector : listMessageConnectors(loader))
            if (connector.getSimpleName().equalsIgnoreCase(name))
                return connector;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends CodecConnectionFactory> T getCodecFactory(ApplicationConnectionFactory factory, Class<T> codecFactoryClass) {
        Objects.nonNull(factory);
        while (factory instanceof CodecConnectionFactory && !codecFactoryClass.isInstance(factory))
            factory = ((CodecConnectionFactory) factory).getDelegate();
        return codecFactoryClass.isInstance(factory) ? (T) factory : null;
    }

    private static List<MessageConnector> createConfiguredConnector(String fullAddress,
        ApplicationConnectionFactory originalFactory, QDStats parentStats, URL base, Set<String> usedNames)
        throws AddressSyntaxException
    {
        ParsedAddress pa = parseAddress(fullAddress);
        // name property is always set first to make sure all configuration logging uses this name
        String name = removeProperty(pa, NAME_CONFIGURATION_KEY.getName(), null);
        String filter = removeProperty(pa, FILTER_CONFIGURATION_KEY.getName(), "");
        if (pa.spec.length() > 0) {
            if (filter.length() > 0 && !filter.equals(pa.spec))
                throw new AddressSyntaxException("Filters specified before @ and with property \"filter\" conflict: \"" + pa.spec + "\" vs \"" + filter + "\"");
            filter = pa.spec;
        }
        // crate non-configured connectors with original ApplicationConnectionFactory that we'll fully configure later
        ApplicationConnectionFactory factoryWithFilter = originalFactory.clone();
        configureFactoryFilter(factoryWithFilter, filter); // configure filter from parser address only
        List<ConnectorWithConfig> cs = createConnectorOnly(pa, factoryWithFilter, parentStats, base);
        // now configure ApplicationConnectionFactory for each connector separately (each potentially has its own name)
        List<MessageConnector> result = new ArrayList<>();
        for (ConnectorWithConfig c : cs) {
            if (name != null) // keep explicitly configured name
                c.connector.setName(name);
            else {
                // make sure that default name is unique and adjust it if needed
                name = c.connector.getName();
                if (usedNames.contains(name)) {
                    for (int i = 1; usedNames.contains(name); i++)
                        name = c.connector.getName() + "-" + i;
                    c.connector.setName(name);
                }
                usedNames.add(name);
            }
            // set stats if needed (after name)
            if (c.type != null)
                configureConnectorStats(c.connector, parentStats, c.type);
            // configure this connector's factory and set it back.
            ApplicationConnectionFactory configuredFactory = configureConnectionFactory(c.connector.getFactory(), pa);
            c.connector.setFactory(configuredFactory);
            // and configure connector itself
            try {
                List<String> props = new ArrayList<>();
                // collect properties that were not use yet and clear their "used" flag
                for (SharedProp prop : c.props) {
                    if (prop.used)
                        prop.used = false;
                    else
                        props.add(prop.kv);
                }
                QDConfig.setProperties(c.connector, props);
            } catch (InvalidFormatException e) {
                throw new AddressSyntaxException(e.getMessage(), e);
            }
            result.add(c.connector);
        }
        return result;
    }

    private static String removeProperty(ParsedAddress pa, String propName, String def) {
        String propNamePrefix = propName + "=";
        for (Iterator<SharedProp> it = pa.props.iterator(); it.hasNext(); ) {
            String s = it.next().kv;
            if (s.startsWith(propNamePrefix)) {
                it.remove();
                return s.substring(propNamePrefix.length());
            }
        }
        return def;
    }

    private static class ConnectorWithConfig {
        final MessageConnector connector;
        final List<SharedProp> props;
        final QDStats.SType type;

        ConnectorWithConfig(MessageConnector connector, List<SharedProp> props, QDStats.SType type) {
            this.connector = connector;
            this.props = props;
            this.type = type;
        }
    }

    private static List<ConnectorWithConfig> createConnectorOnly(ParsedAddress pa,
        ApplicationConnectionFactory acFactory, QDStats parentStats, URL base)
        throws AddressSyntaxException
    {
        String address = pa.address.trim();
        // let's check if this address is a configuration URL/file
        try {
            URL addressUrl = new URL(base, address);
            if (addressUrl.getRef() != null && addressUrl.getPath().endsWith(CONFIG_SUFFIX)) {
                return loadConfigurationFile(addressUrl, acFactory, parentStats, pa.props);
            }
        } catch (MalformedURLException e) {
            // no luck -- continue looking
        }

        for (MessageConnectorFactory mcf : Services.createServices(MessageConnectorFactory.class, null)) {
            MessageConnector connector = mcf.createMessageConnector(acFactory, address);
            if (connector != null) {
                String name = connector.getClass().getName();
                int idx = name.lastIndexOf('.');
                if (idx >= 0)
                    name = name.substring(idx + 1);
                return Collections.singletonList(new ConnectorWithConfig(connector, pa.props, new QDStats.SType(name, QDStats.FLAG_IO)));
            }
        }

        if (address.startsWith("nio:"))
            throw new AddressSyntaxException("Address starting with \"nio:\" is considered ambiguous: use " +
                "\"nio::<port>\" to create NIO server socket connector or use \"" + CLIENT_SOCKET_PREFIX +
                "nio:<port>\" to create client socket connector to the host named \"nio\".");

        if (address.startsWith(SERVER_SOCKET_PREFIX)) {
            address = address.substring(SERVER_SOCKET_PREFIX.length() - 1); // including ':'
        } else if (address.startsWith(CLIENT_SOCKET_PREFIX)) {
            address = address.substring(CLIENT_SOCKET_PREFIX.length()); // excluding ':'
        }

        // Parse port in address
        int portSep = address.lastIndexOf(':');
        if (portSep < 0)
            throw new AddressSyntaxException("Port number is missing in \"" + address + "\"");
        int port;
        try {
            port = Integer.decode(address.substring(portSep + 1));
        } catch (NumberFormatException e) {
            throw new AddressSyntaxException("Port number format error in \"" + address + "\"");
        }

        // Create message connector
        if (portSep > 0) {
            ClientSocketConnector connector = new ClientSocketConnector(acFactory, address);
            return Collections.singletonList(
                new ConnectorWithConfig(connector, pa.props, QDStats.SType.CLIENT_SOCKET_CONNECTOR));
        } else {
            ServerSocketConnector connector = new ServerSocketConnector(acFactory, port);
            return Collections.singletonList(
                new ConnectorWithConfig(connector, pa.props, QDStats.SType.SERVER_SOCKET_CONNECTOR));
        }
    }

    private static void configureConnectorStats(MessageConnector connector, QDStats parentStats, QDStats.SType type) {
        connector.setStats(parentStats.create(type, "connector=" + JMXNameBuilder.quoteKeyPropertyValue(connector.getName())));
    }

    private static ApplicationConnectionFactory configureConnectionFactory(ApplicationConnectionFactory originalFactory, ParsedAddress parsedAddress) {
        ApplicationConnectionFactory factory = originalFactory;
        try {
            Iterable<CodecFactory> codecs = Services.createServices(CodecFactory.class, null);
            for (List<String> codecInfo : parsedAddress.codecs) {
                String codecName = codecInfo.get(0);
                ApplicationConnectionFactory newFactory = factory;
                boolean matched = false;
                for (CodecFactory codecFactory : codecs) {
                    newFactory = codecFactory.createCodec(codecName, newFactory);
                    if (newFactory != factory) {
                        matched = true;
                        Iterator<String> it = codecInfo.iterator();
                        it.next(); // skip codec name
                        while (it.hasNext()) {
                            String kv = it.next();
                            int i = kv.indexOf('=');
                            String key = i < 0 ? kv : kv.substring(0, i).trim();
                            String value = i < 0 ? "" : kv.substring(i + 1).trim();
                            if (!newFactory.setConfiguration(ConfigurationKey.create(key, String.class), value))
                                throw new AddressSyntaxException("Unknown property \"" + key + "\" for \"" + codecName + "\" codec");
                        }
                    }
                }
                if (!matched)
                    throw new AddressSyntaxException("Unsupported codec \"" + codecName + "\"");
                factory = newFactory;
            }
            for (SharedProp sharedProp : parsedAddress.props) {
                String kv = sharedProp.kv;
                int i = kv.indexOf('=');
                String key = i < 0 ? kv : kv.substring(0, i).trim();
                String value = i < 0 ? "" : kv.substring(i + 1).trim();
                if (originalFactory.setConfiguration(ConfigurationKey.create(key, String.class), value))
                    sharedProp.used = true;
            }
            // finish configuring original factory
            originalFactory.reinitConfiguration();
        } catch (ConfigurationException e) {
            throw new AddressSyntaxException("Invalid connection configuration for key \"" + e.getKey().getName() + "\": " + e.getMessage(), e);
        }
        return factory;
    }

    private static void configureFactoryFilter(ApplicationConnectionFactory factory, String spec) throws AddressSyntaxException {
        try {
            if (!factory.setConfiguration(FILTER_CONFIGURATION_KEY, spec) && spec.length() > 0)
                throw new AddressSyntaxException("Connection does not support filter \"" + spec + "\"");
        } catch (ConfigurationException e) {
            throw new AddressSyntaxException("Invalid filter: " + e.getMessage(), e);
        }
    }

    private static List<ConnectorWithConfig> loadConfigurationFile(URL configUrl, ApplicationConnectionFactory acFactory,
        QDStats parentStats, List<SharedProp> baseProps)
    {
        SortedMap<String, String> config = readProperties(configUrl);
        String prefix = configUrl.getRef();
        String addresses = config.get(prefix);
        if (addresses == null)
            throw new AddressSyntaxException("Property value is not found in \"" + configUrl + "\"");
        List<MessageConnector> connectors = createConnectorsInternal(acFactory, addresses, parentStats, configUrl);
        Set<Map.Entry<String, String>> entries = config.subMap(prefix + '.', prefix + (char) ('.' + 1)).entrySet();
        List<SharedProp> props = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries)
            props.add(new SharedProp(entry.getKey().substring(prefix.length() + 1) + "=" + entry.getValue()));
        props.addAll(baseProps);
        List<ConnectorWithConfig> result = new ArrayList<>();
        for (MessageConnector connector : connectors)
            result.add(new ConnectorWithConfig(connector, props, null));
        return result;
    }

    private static SortedMap<String, String> readProperties(URL url) {
        Properties props = new Properties();
        try (InputStream in = url.openStream()) {
            props.load(in);
        } catch (IOException e) {
            throw new AddressSyntaxException("Cannot read configuration file \"" + url + "\"", e);
        }
        //noinspection unchecked
        return new TreeMap<>((Map) props);
    }

    public static void startMessageConnectors(Collection<? extends MessageConnector> connectors) {
        connectors.forEach(MessageConnectorMBean::start);
    }

    public static void stopMessageConnectors(Collection<? extends MessageConnector> connectors) {
        connectors.forEach(MessageConnectorMBean::stop);
    }

    public static EndpointStats getEndpointStats(Collection<? extends MessageConnector> connectors) {
        EndpointStats stats = new EndpointStats();
        for (MessageConnector connector : connectors)
            stats.addEndpointStats(connector.retrieveCompleteEndpointStats());
        return stats;
    }

    public static void addMessageConnectorListener(Collection<? extends MessageConnector> connectors, MessageConnectorListener listener) {
        for (MessageConnector connector : connectors)
            connector.addMessageConnectorListener(listener);
    }

    public static void removeMessageConnectorListener(Collection<? extends MessageConnector> connectors, MessageConnectorListener listener) {
        for (MessageConnector connector : connectors)
            connector.removeMessageConnectorListener(listener);
    }

    public static void setThreadPriority(Collection<? extends MessageConnector> connectors, int priority) {
        for (MessageConnector connector : connectors)
            connector.setThreadPriority(priority);
    }

    private static class LegacyNonConfigurableFactory extends MessageAdapter.ConfigurableFactory {
        private MessageAdapter.Factory factory;

        LegacyNonConfigurableFactory(MessageAdapter.Factory factory) {
            this.factory = factory;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return factory.createAdapter(stats);
        }

        @Override
        public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) throws ConfigurationException {
            if (key.equals(FILTER_CONFIGURATION_KEY) && factory instanceof ConfigurableMessageAdapterFactory) {
                factory = ((ConfigurableMessageAdapterFactory) factory).createMessageAdapterFactory((String) value);
                return true;
            }
            return super.setConfiguration(key, value);
        }

        @Override
        public String toString() {
            return factory.toString();
        }
    }
}
