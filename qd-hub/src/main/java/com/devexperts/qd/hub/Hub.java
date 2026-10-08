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
package com.devexperts.qd.hub;

import com.devexperts.auth.AuthSession;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.hub.config.ChannelConfig;
import com.devexperts.qd.hub.config.DownlinkConfig;
import com.devexperts.qd.hub.config.HubConfig;
import com.devexperts.qd.hub.config.LinkConfig;
import com.devexperts.qd.hub.config.SpaceConfig;
import com.devexperts.qd.hub.config.UniverseConfig;
import com.devexperts.qd.hub.config.UplinkConfig;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.DynamicChannelShaper;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.tools.module.EventLog;
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.StateReportingSupport;
import com.devexperts.qd.tools.reporting.HtmlReportBuilder;
import com.devexperts.qd.tools.reporting.ReportBuilder;
import com.devexperts.util.TimePeriod;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.concurrent.GuardedBy;

import static com.devexperts.qd.hub.Log.Action.RESOLVE;
import static com.devexperts.qd.hub.Log.Subject.DOWNLINK;
import static com.devexperts.qd.hub.Log.Subject.UPLINK;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.ADD;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.UPDATE;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.WARNING;

/**
 * Space-aware hub main class.
 */
public class Hub implements HubMXBean, Module<HubConfig>, StateReportingSupport {
    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*");
    private final Logging log;

    private static class Link {
        final Log.Subject what;
        final String name;
        final String address;
        final String defaultBindAddr;
        final List<String> spaces;

        volatile String filter;
        volatile List<ChannelConfig> products;

        final QDEndpoint endpoint;
        final List<MessageConnector> connectors = new ArrayList<>();

        Link(LinkConfig linkConfig, String defaultBindAddr, List<String> spaces, List<ChannelConfig> products,
            QDEndpoint endpoint)
        {
            this.what = what(linkConfig);
            this.name = linkConfig.getName();
            this.address = linkConfig.getAddress();
            this.defaultBindAddr = defaultBindAddr;
            this.filter = linkConfig.getFilter();
            this.spaces = spaces;
            this.products = products;
            this.endpoint = endpoint;
        }

        @Override
        public String toString() {
            return "Link{name=" + name +
                ", address=" + address +
                ", defaultBindAddr=" + defaultBindAddr +
                ", filter=" + filter +
                ", spaces=" + spaces +
                ", products=" + products +
                "}";
        }
    }

    private final String name;
    private final ModuleContext context;
    private final Log eventLog;
    private final Universe universe;

    private final Map<String, Link> uplinks = new ConcurrentHashMap<>();
    private final Map<String, Link> downlinks = new ConcurrentHashMap<>();

    private QDEndpoint moduleEndpoint;
    private Management.Registration moduleRegistration;

    public Hub(ModuleContext context) {
        this.context = context;
        name = context.getName();
        DataScheme scheme = QDFactory.getDefaultScheme();
        eventLog = new Log(context.getEventLog());
        log = context.getEventLog().getLogging();
        universe = new Universe(name, scheme, eventLog, context.getEndpointConfig().getProperties());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized void start(HubConfig config) {
        boolean initialized = false;
        try {
            moduleEndpoint = QDEndpoint.newBuilder()
                .withName(name)
                .withScheme(universe.getScheme())
                .withProperties(universe.getEndpointProps())
                .build();
            moduleRegistration =
                Management.registerMBean(this, HubMXBean.class, "com.devexperts.mars:type=Hub,name=" + name);
            doConfigure(config);
            initialized = true;
        } finally {
            // do not leave any "active" leftovers
            if (!initialized)
                doClose();
        }
    }

    @Override
    public synchronized void reconfigure(HubConfig hubConfig) {
        doConfigure(hubConfig);
    }

    // FIXME: Launcher validation API probably needs to be reconsidered
    @Override
    public Object validate(HubConfig config) {

        String error = null;

        // Check universe config for consistency
        //   - active products should refer only active components
        //   - space + products definitions should form a DAG
        UniverseConfig universeConfig = config.getUniverseConfig();
        universeConfig.applyOverrides();

        Map<String, SpaceConfig> spaces = universeConfig.getSpaces();
        Map<String, List<ChannelConfig>> products = universeConfig.getProducts();

        for (String space : spaces.keySet()) {
            if (!PRODUCT_NAME_PATTERN.matcher(space).matches())
                return "Invalid space name '" + space + "'";
        }

        HashSet<String> validProducts = new HashSet<>();
        ArrayDeque<String> constructing = new ArrayDeque<>();
        for (String product : products.keySet()) {
            // FIXME: maybe invalid products not referenced by downlinks may be ignored ?
            error = validateProduct(product, spaces, products, validProducts, constructing);
            if (error != null)
                return error;
        }

        // validate links config for consistency
        // - uplink should reference a single active space
        for (UplinkConfig uplink : config.getUplinks().values()) {
            List<String> spaceList = uplink.getSpaces();
            String prefix = "Uplink " + uplink.getName();
            if (spaceList.isEmpty())
                return prefix + " should reference a space";
            if (spaceList.size() != 1)
                return prefix + " references multiple spaces. Only single-space uplinks are supported.";
            String space = spaceList.get(0);
            SpaceConfig spaceConfig = spaces.get(space);
            if (spaceConfig == null)
                return prefix + " references unknown space " + space;
            if (!spaceConfig.isActive())
                return prefix + " references disabled space " + space;
        }

        // - downlink should either reference a single active space or a nonempty list of active products
        for (DownlinkConfig downlink : config.getDownlinks().values()) {
            List<String> spaceList = downlink.getSpaces();
            String prefix = "Downlink " + downlink.getName();
            if (!spaceList.isEmpty()) {
                List<ChannelConfig> downlinkProducts = downlink.getProducts();
                if (!downlinkProducts.isEmpty())
                    return prefix + " configuration shall define either a space or a product list";
                if (spaceList.size() != 1)
                    return prefix + " references multiple spaces. Only single-space downlinks are supported.";
                String space = spaceList.get(0);
                SpaceConfig spaceConfig = spaces.get(space);
                if (spaceConfig == null)
                    return prefix + " references unknown space " + space;
                if (!spaceConfig.isActive())
                    return prefix + " references disabled space " + space;
            } else {
                List<ChannelConfig> downlinkProducts = downlink.getProducts();
                if (downlinkProducts.isEmpty())
                    return prefix + " configuration shall define either a space or a product list";
                for (ChannelConfig downlinkProduct : downlinkProducts) {
                    String base = downlinkProduct.getBase(); // base references either product or space
                    if (products.containsKey(base))
                        continue; // assume all products are valid for now
                    SpaceConfig spaceConfig = spaces.get(base);
                    if (spaceConfig == null)
                        return prefix + " references unknown space " + base;
                    if (!spaceConfig.isActive())
                        return prefix + " references disabled space " + base;
                }
            }
        }

        return Module.super.validate(config);
    }

    private String validateProduct(String product, Map<String, SpaceConfig> spaces,
        Map<String, List<ChannelConfig>> products, Set<String> valid, ArrayDeque<String> constructing)
    {
        String error = null;
        if (valid.contains(product))
            return null;
        if (!PRODUCT_NAME_PATTERN.matcher(product).matches())
            return "Invalid product name '" + product + "'";
        if (constructing.contains(product)) // deep hierarchy is not expected
            return "Products definition cycle detected: " + String.join(">", constructing);
        List<ChannelConfig> channels = products.get(product);
        if (channels != null) {
            // FIXME: is empty product makes sense ?
            // if (channels.isEmpty())
            //     return "Product " + product + " is empty";
            constructing.addLast(product);
            for (ChannelConfig channel : channels) {
                error = validateProduct(channel.getBase(), spaces, products, valid, constructing);
                if (error != null)
                    return error;
            }
            constructing.removeLast();
        } else {
            SpaceConfig space = spaces.get(product);
            if (space == null)
                return "Unknown product/space " + product + " is referenced by product " + constructing.peekLast();
            if (!space.isActive())
                return "Inactive space " + product + " is referenced by product " + constructing.peekLast();
        }
        valid.add(product);
        return null;
    }

    @Override
    public boolean isActive() {
        return uplinks.values().stream().flatMap(l -> l.connectors.stream()).anyMatch(MessageConnector::isActive) ||
            downlinks.values().stream().flatMap(l -> l.connectors.stream()).anyMatch(MessageConnector::isActive);
    }

    @Override
    public synchronized void close() {
        doClose();
    }

    @Override
    public String reportCurrentState(String regex) {
        try {
            HtmlReportBuilder reportBuilder = new HtmlReportBuilder();
            reportCurrentState(reportBuilder);
            return reportBuilder.buildFilteredHtmlReport(name, regex);
        } catch (RuntimeException e) {
            log.error("Unexpected error", e);
            throw e;
        }
    }

    @Override
    public void reportCurrentState(ReportBuilder reportBuilder) {
        reportBuilder.addHeaderRow("What", "Name", "Message");
        List<List<String>> result = universe.getState();
        // FIXME: reconsider how formatting methods shall be organized
        result.addAll(Log.convert(UPLINK, uplinks));
        result.addAll(Log.convert(DOWNLINK, downlinks));
        result.forEach(reportBuilder::addRow);
    }

    @Override
    public String reportEventLog(String regex) {
        EventLog defaultEventLog = context.getEventLog();
        try {
            HtmlReportBuilder reportBuilder = new HtmlReportBuilder();
            defaultEventLog.reportEventLog(reportBuilder);
            return reportBuilder.buildFilteredHtmlReport(context.getName(), regex);
        } catch (RuntimeException e) {
            log.error("Unexpected error", e);
            throw e;
        }
    }

    // ========== Hub private implementation ==========

    @GuardedBy("this")
    private void doConfigure(HubConfig hubConfig) {
        // the current implementation does initial configuration and on-the-fly reconfiguration the same way
        universe.configure(hubConfig.getUniverseConfig());
        resolveBindAddr(hubConfig.getDefaultUplinkBindAddr(), hubConfig::setDefaultUplinkBindAddr);
        resolveBindAddr(hubConfig.getDefaultDownlinkBindAddr(), hubConfig::setDefaultDownlinkBindAddr);
        configureUplinks(hubConfig.getUplinks(), hubConfig.getDefaultUplinkBindAddr());
        configureDownlinks(hubConfig.getDownlinks(), hubConfig.getDefaultDownlinkBindAddr());
    }

    @GuardedBy("this")
    private void doClose() {
        // Cleanup all related resources. Be ready to a partially initialized state.
        uplinks.values().forEach(l -> closeLink(l, l));
        downlinks.values().forEach(l -> closeLink(l, l));
        uplinks.clear();
        downlinks.clear();

        if (moduleRegistration != null)
            moduleRegistration.unregister();
        if (moduleEndpoint != null)
            moduleEndpoint.close();
    }

    private void resolveBindAddr(String bindAddr, Consumer<String> setter) {
        if (!bindAddr.equals(MessageConnector.Bindable.ANY_BIND_ADDRESS)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                InetAddress.getByName(bindAddr);
            } catch (UnknownHostException e) {
                eventLog.log(WARNING, Log.Subject.MODULE, name,
                    "failed to resolve default bindAddr " + bindAddr + " with " + e.getMessage() + ", switching to ANY bindAddr");
                setter.accept(MessageConnector.Bindable.ANY_BIND_ADDRESS);
            }
        }
    }

    private void closeRemovedLinks(Map<String, Link> links, Set<String> activeNames) {
        for (Iterator<Link> it = links.values().iterator(); it.hasNext();) {
            Link link = it.next();
            if (!activeNames.contains(link.name)) {
                closeLink(link, "no longer exists " + link);
                it.remove();
            }
        }
    }

    private void configureUplinks(Map<String, UplinkConfig> uplinkConfigs, String defaultBindAddr) {
        closeRemovedLinks(uplinks, uplinkConfigs.keySet());
        for (String name : new TreeSet<>(uplinkConfigs.keySet())) {
            UplinkConfig uc = uplinkConfigs.get(name);
            configureSpaceLink(uplinks, uc, uc.getSpaces(), DistributorAdapter.Factory::new, defaultBindAddr);
        }
    }

    private void configureDownlinks(Map<String, DownlinkConfig> downlinkConfigs, String defaultBindAddr) {
        closeRemovedLinks(downlinks, downlinkConfigs.keySet());
        for (String name : new TreeSet<>(downlinkConfigs.keySet())) {
            DownlinkConfig dc = downlinkConfigs.get(name);
            if (!dc.getSpaces().isEmpty() && !dc.getProducts().isEmpty()) {
                // Safety net: should not happen with a validated config
                eventLog.log(WARNING, DOWNLINK, name, "has both spaces and products config " + dc);
                continue;
            }
            if (dc.getSpaces().isEmpty() && dc.getProducts().isEmpty()) {
                // Safety net: should not happen with a validated config
                eventLog.log(WARNING, DOWNLINK, name, "has no spaces or products config " + dc);
                continue;
            }
            if (!dc.getSpaces().isEmpty()) {
                configureSpaceLink(downlinks, dc, dc.getSpaces(), AgentAdapter.Factory::new, defaultBindAddr);
            } else if (!dc.getProducts().isEmpty()) {
                configureProductDownlink(dc, defaultBindAddr);
            }
        }
    }

    private void configureSpaceLink(Map<String, Link> links, LinkConfig linkConfig, List<String> spaces,
        BiFunction<QDEndpoint, SubscriptionFilter, MessageAdapter.AbstractFactory> fFactory, String defaultBindAddr)
    {
        Log.Subject what = what(linkConfig);
        String name = linkConfig.getName();
        Link link = links.get(name);
        if (spaces.isEmpty()) { // Safety net: should not happen with a validated config
            eventLog.log(WARNING, what, name, "does not refer any space " + linkConfig);
            stopLink(link, "does not refer any space " + linkConfig);
            return;
        }
        if (spaces.size() > 1) { // Safety net: should not happen with a validated config
            eventLog.log(WARNING, what, name, "refers to multiple spaces " + spaces);
        }
        // Use the first space from the list
        String spaceName = spaces.get(0);
        QDEndpoint endpoint = universe.getSpaceEndpoint(spaceName);
        if (endpoint == null) { // Safety net: should not happen with a validated config
            eventLog.log(WARNING, what, name, "refers to unknown space " + linkConfig);
            stopLink(link, "refers to unknown space " + linkConfig);
            return;
        }

        if (link == null || !link.filter.equals(linkConfig.getFilter()) || !link.spaces.equals(spaces) ||
            !link.address.equals(linkConfig.getAddress()) || !link.defaultBindAddr.equals(defaultBindAddr))
        {
            if (link != null)
                closeLink(link, "incompatible old config " + link + " with new config " + linkConfig);

            link = new Link(linkConfig, defaultBindAddr, spaces, Collections.emptyList(), endpoint);
            links.put(name, link);
            eventLog.log(ADD, what, name, linkConfig);

            QDFilter filter = universe.getFilter(linkConfig.getFilter());
            MessageAdapter.AbstractFactory maFactory = fFactory.apply(endpoint, filter);

            createConnectors(link, maFactory);
        }

        if (universe.isActiveSpace(spaceName)) {
            startLink(link, linkConfig);
        } else {
            // TODO: is it possible with a validated config? Should go away with a proper space closing procedure
            stopLink(link, linkConfig + " due to inactive space " + universe.getSpaceConfig(spaceName));
        }
    }

    private void configureProductDownlink(DownlinkConfig downlinkConfig, String defaultBindAddr) {
        String name = downlinkConfig.getName();
        Link link = downlinks.get(name);
        if (link == null || !link.spaces.isEmpty() ||
            !link.address.equals(downlinkConfig.getAddress()) || !link.defaultBindAddr.equals(defaultBindAddr))
        {
            if (link != null)
                closeLink(link, "incompatible old config " + link + " with new config " + downlinkConfig);

            link = new Link(downlinkConfig, defaultBindAddr, downlinkConfig.getSpaces(), downlinkConfig.getProducts(),
                moduleEndpoint);
            downlinks.put(name, link);
            eventLog.log(ADD, DOWNLINK, name, downlinkConfig);
            trackChannels(downlinkConfig);

            final Link linkCapture = link;
            QDFilter filter = universe.getFilter(downlinkConfig.getFilter());
            AgentAdapter.Factory maFactory = new AgentAdapter.Factory(moduleEndpoint, filter) {
                @Override
                protected ChannelShaper[] createChannelShapers(AgentAdapter agentAdapter, AuthSession session) {
                    // FIXME: check aggregationPeriod logic
                    TimePeriod aggregationPeriod = getAggregationPeriod();
                    return universe.createChannelShapers(
                        linkCapture.products,
                        linkCapture.filter,
                        aggregationPeriod == null ? 0 : aggregationPeriod.getTime(),
                        getOrCreateSubscriptionExecutor()).toArray(new ChannelShaper[0]);
                }
            };

            createConnectors(link, maFactory);
            startLink(link, downlinkConfig);
        } else {
            if (!link.filter.equals(downlinkConfig.getFilter()) || !link.products.equals(downlinkConfig.getProducts())) {
                eventLog.log(UPDATE, DOWNLINK, name, "on-the-fly update of filter and products " + downlinkConfig);
                trackChannels(downlinkConfig);
            }
            // Configuration updates will be captured during the creating of new agents.
            // Existing connections will work as configured when a connection was established.
            // FIXME: should update of existing connections be forced somehow?
            link.filter = downlinkConfig.getFilter();
            link.products = downlinkConfig.getProducts();
        }
    }

    /**
     * Reports a list of channels corresponding to the provided downlink configuration to the log
     * @param dc downlink configuration
     */
    private void trackChannels(DownlinkConfig dc) {
        // FIXME: create temporary shapers just to report configuration?
        List<DynamicChannelShaper> shapers = universe.createChannelShapers(dc.getProducts(), dc.getFilter(), 0, null);
        // FIXME: standard shaper implementation doesn't report an associated collector, so log won't reflect
        //   assigned spaces
        eventLog.log(RESOLVE, DOWNLINK, dc.getName(), shapers);
        shapers.forEach(DynamicChannelShaper::close);
    }

    private void createConnectors(Link link, MessageAdapter.AbstractFactory maFactory) {
        Log.Subject what = link.what;
        String name = link.name;
        ApplicationConnectionFactory acFactory = MessageConnectors.applicationConnectionFactory(maFactory);
        acFactory.setName(name);
        link.connectors.addAll(MessageConnectors.createMessageConnectors(acFactory, link.address, link.endpoint.getRootStats()));
        for (MessageConnector mc : link.connectors) {
            if (!name.equals(mc.getName())) {
                eventLog.log(WARNING, what, name, "uses a different name in address (" + mc.getName() + "), replaced.");
                mc.setName(name);
            }
            // TODO filter mismatch due to parenthesis - either normalize both or use getName()
            // TODO a&(b&c) -> a&b&c != a&(b&c)
            // TODO think about moving all uplinks to retail-uplinks, retail-space-downlinks, retail-product-downlinks
            String mcFilter = mc.getFactory().getConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY);
            if (!link.filter.equals(mcFilter)) {
                eventLog.log(WARNING, what, name, "uses a different filter in address (" + mcFilter + "), replaced.");
                mc.getFactory().setConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY, link.filter);
            }
            if (!link.defaultBindAddr.equals(MessageConnector.Bindable.ANY_BIND_ADDRESS) &&
                mc instanceof MessageConnector.Bindable)
            {
                MessageConnector.Bindable bindable = (MessageConnector.Bindable) mc;
                if (bindable.getBindAddr().equals(MessageConnector.Bindable.ANY_BIND_ADDRESS)) {
                    try {
                        bindable.setBindAddr(link.defaultBindAddr);
                    } catch (UnknownHostException e) {
                        eventLog.log(WARNING, what, name,
                            "failed to set default bindAddr " + link.defaultBindAddr + " with " + e.getMessage());
                    }
                }
            }
        }
        link.endpoint.addConnectors(link.connectors);
    }

    private static Log.Subject what(LinkConfig linkConfig) {
        return linkConfig instanceof UplinkConfig ? UPLINK : DOWNLINK;
    }

    private void startLink(Link link, Object message) {
        if (link == null || link.connectors.stream().allMatch(MessageConnector::isActive))
            return;
        link.connectors.forEach(MessageConnector::start);
        eventLog.log(Log.Action.START, link.what, link.name, message);
    }

    private void stopLink(Link link, Object message) {
        if (link == null || link.connectors.stream().noneMatch(MessageConnector::isActive))
            return;
        link.connectors.forEach(MessageConnector::stop);
        eventLog.log(Log.Action.STOP, link.what, link.name, message);
    }

    private void closeLink(Link link, Object message) {
        if (link == null)
            return;
        stopLink(link, message);
        link.endpoint.removeConnectors(link.connectors);
        link.connectors.forEach(MessageConnector::close);
        link.connectors.clear();
        eventLog.log(Log.Action.REMOVE, link.what, link.name, message);
    }
}
