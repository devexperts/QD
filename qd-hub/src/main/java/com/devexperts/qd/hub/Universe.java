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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.hub.config.ChannelConfig;
import com.devexperts.qd.hub.config.SpaceConfig;
import com.devexperts.qd.hub.config.UniverseConfig;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.qtp.DynamicChannelShaper;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

import static com.devexperts.qd.hub.Log.Action.PASSIVATE;
import static com.devexperts.qd.hub.Log.Subject.CHANNEL;
import static com.devexperts.qd.hub.Log.Subject.PRODUCT;
import static com.devexperts.qd.hub.Log.Subject.SPACE;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.UPDATE;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.WARNING;

/**
 * Universe maintains a set of coordinated {@link QDEndpoint QD endpoints} representing spaces and products inside a Hub,
 * primarily configured by a {@link UniverseConfig} instance.
 * It supports on-the-fly reconfiguration and provides methods for configuring attached connectors.
 */
@SuppressWarnings("WeakerAccess")
class Universe {

    static class Space {
        final String name;
        final SpaceConfig spaceConfig;
        final QDEndpoint endpoint;

        // TODO: SpaceConfig already provides a name
        Space(String name, SpaceConfig spaceConfig, QDEndpoint endpoint) {
            this.name = name;
            this.spaceConfig = spaceConfig;
            this.endpoint = endpoint;
        }

        public boolean isActive() {
            return spaceConfig.isActive();
        }

        @Override
        public String toString() {
            return spaceConfig.toString();
        }
    }

    static class Channel {
        private static final EnumSet<QDContract> ALL_CONTRACTS = EnumSet.allOf(QDContract.class);

        final Space space;
        final QDFilter filter;
        final EnumSet<QDContract> contracts;
        final long aggregationPeriod;
        final boolean allowWildcards;

        Channel(Space space, QDFilter filter, EnumSet<QDContract> contracts, long aggregationPeriod,
            boolean allowWildcards)
        {
            this.space = space;
            this.filter = filter;
            this.contracts = contracts;
            this.aggregationPeriod = aggregationPeriod;
            this.allowWildcards = allowWildcards;
        }

        @Override
        public String toString() {
            return "Channel{space=" + space.name +
                ", filter=" + filter +
                ", contracts=" + (contracts.equals(ALL_CONTRACTS) ? "[*]" : contracts) +
                ", ap=" + aggregationPeriod +
                ", awc=" + allowWildcards +
                "}";
        }
    }

    private static final List<Channel> UNDER_CONSTRUCTION = Collections.unmodifiableList(new ArrayList<>());
    static final List<Channel> INVALID = Collections.unmodifiableList(new ArrayList<>());

    private final String moduleName;
    private final DataScheme scheme;
    private final Log log;
    private final Properties endpointProps;

    private final Map<String, QDFilter.Updated> filters = new ConcurrentHashMap<>();
    private final Map<String, Space> spaces = new ConcurrentHashMap<>();
    private final Map<String, List<ChannelConfig>> products = new ConcurrentHashMap<>();
    private final Map<String, List<Channel>> channels = new ConcurrentHashMap<>();

    private final QDFilter nwcFilter;

    public Universe(String moduleName, DataScheme scheme, Log log, Properties endpointProps) {
        this.moduleName = moduleName;
        this.scheme = scheme;
        this.log = log;
        this.endpointProps = endpointProps;

        nwcFilter = getFilter("nwc");
    }

    public QDFilter getFilter(String filter) {
        QDFilter.Updated updated = filters.get(filter);
        if (updated == null) {
            // TODO try-catch FilterSyntaxException, also make filter to include spec into exception
            // Create filter outside of map synchronization in case it takes long time for I/O.
            QDFilter qdFilter = CompositeFilters.valueOf(filter, scheme);
            // Atomic update just in case as we do not expect any real concurrency here.
            updated = filters.computeIfAbsent(filter, s -> qdFilter.getUpdated());
        }
        return updated.getFilter();
    }

    public DataScheme getScheme() {
        return scheme;
    }

    public Properties getEndpointProps() {
        return endpointProps;
    }

    public synchronized void configure(UniverseConfig universeConfig) {
        // FIXME: current implementation validates and applies configuration gradually, 
        //   configuration errors/inconsistencies and unsupported changes reported and passed-by.
        //   Although some configuration aspects are verified up-front in Hub.validate, some others aren't.
        //   As a result we might end with a runtime configuration state mismatching any initial configuration. 
        universeConfig.applyOverrides();
        configureSpaces(universeConfig);
        configureProducts(universeConfig);
    }

    public synchronized SpaceConfig getSpaceConfig(String name) {
        Space space = spaces.get(name);
        return space == null ? null : space.spaceConfig;
    }

    public synchronized QDEndpoint getSpaceEndpoint(String name) {
        Space space = spaces.get(name);
        return space == null ? null : space.endpoint;
    }

    public synchronized boolean isActiveSpace(String name) {
        Space space = spaces.get(name);
        return space != null && space.isActive();
    }

    public synchronized List<DynamicChannelShaper> createChannelShapers(List<ChannelConfig> products,
        String filter, long aggregationPeriod, Executor subscriptionExecutor)
    {
        QDFilter commonFilter = getFilter(filter);
        List<Channel> baseChannels = createProductChannels(products);
        QDFilter[] chainFilters = new QDFilter[QDContract.values().length];
        Arrays.fill(chainFilters, QDFilter.ANYTHING);
        List<DynamicChannelShaper> result = new ArrayList<>();
        for (Channel channel : baseChannels) {
            for (QDContract contract : channel.contracts) {
                int idx = contract.ordinal();
                QDFilter subscriptionFilter = CompositeFilters.makeAnd(chainFilters[idx], channel.filter);
                chainFilters[idx] =
                    CompositeFilters.makeAnd(chainFilters[idx], CompositeFilters.makeNot(channel.filter));

                subscriptionFilter = CompositeFilters.makeAnd(subscriptionFilter, commonFilter);
                if (!channel.allowWildcards)
                    subscriptionFilter = CompositeFilters.makeAnd(subscriptionFilter, nwcFilter);
                if (subscriptionFilter == QDFilter.NOTHING)
                    continue;

                DynamicChannelShaper shaper =
                    new DynamicChannelShaper(contract, subscriptionExecutor, subscriptionFilter);
                shaper.setCollector(channel.space.endpoint.getCollector(contract));
                shaper.setAggregationPeriod(Math.max(channel.aggregationPeriod, aggregationPeriod));
                result.add(shaper);
            }
        }
        return result;
    }

    public synchronized List<List<String>> getState() {
        List<List<String>> result = new ArrayList<>();
        result.addAll(Log.convert(SPACE, spaces));
        result.addAll(Log.convert(PRODUCT, products));
        result.addAll(Log.convert(CHANNEL, channels));
        return result;
    }


    // ========== Universe private implementation ==========

    List<Channel> getChannels(String product) {
        return channels.get(product);
    }

    private void configureSpaces(UniverseConfig universeConfig) {
        for (String name : new TreeSet<>(universeConfig.getSpaces().keySet())) {
            SpaceConfig spaceConfig = universeConfig.getSpaces().get(name);
            if (!spaceConfig.isActive())
                continue;
            boolean hasSameProduct = products.get(name) != null && universeConfig.getProducts().get(name) != null;
            Space space = spaces.get(name);
            if (space == null) {
                log.log(Log.Action.ADD, SPACE, name, spaceConfig);
                QDEndpoint endpoint = QDEndpoint.newBuilder()
                    .withName(moduleName + "-" + name)
                    .withScheme(scheme)
                    .withCollectors(spaceConfig.getContracts())
                    .withEventTimeSequence(spaceConfig.isEventTimeSequence())
                    .withProperties(endpointProps)
                    .build();
                // FIXME: support stripe and sticky-subscription parameters
                space = new Space(name, spaceConfig, endpoint);
                spaces.put(name, space);
                // check newly added space against an already existing product
                if (hasSameProduct && spaceConfig.isActive())
                    log.log(WARNING, PRODUCT, name, "collides with active space with the same name");
            } else if (!space.spaceConfig.equals(spaceConfig)) {
                log.log(UPDATE, SPACE, name, spaceConfig);
                if (!space.spaceConfig.getContracts().equals(spaceConfig.getContracts())) {
                    log.log(WARNING, SPACE, name, "unable to change contracts from " +
                        space.spaceConfig.getContracts() + " to " + spaceConfig.getContracts());
                }
                if (space.spaceConfig.isEventTimeSequence() != spaceConfig.isEventTimeSequence()) {
                    log.log(WARNING, SPACE, name, "unable to change eventTimeSequence from " +
                        space.spaceConfig.isEventTimeSequence() + " to " + spaceConfig.isEventTimeSequence());
                }
                // check newly activated space against an already existing product
                if (hasSameProduct && spaceConfig.isActive() && !space.isActive())
                    log.log(WARNING, PRODUCT, name, "collides with active space with same name");
                space.spaceConfig.setFilter(spaceConfig.getFilter());
                space.spaceConfig.setActive(spaceConfig.isActive());
                space.spaceConfig.setEnableWildcards(spaceConfig.isEnableWildcards());
                space.spaceConfig.setStoreEverything(spaceConfig.isStoreEverything());
                space.spaceConfig.setStoreEverythingFilter(spaceConfig.getStoreEverythingFilter());
            }

            if (space.endpoint.getStream() != null)
                space.endpoint.getStream().setEnableWildcards(spaceConfig.isEnableWildcards());
            QDFilter storeEverythingFilter = getFilter(spaceConfig.getStoreEverythingFilter());
            for (QDCollector collector : space.endpoint.getCollectors()) {
                collector.setStoreEverything(spaceConfig.isStoreEverything());
                // below updates dynamic filter as a side effect; storeEverythingFilter does not track dynamic itself
                collector.setStoreEverythingFilter(storeEverythingFilter);
            }
        }
        // Close stale spaces
        for (String name : new TreeSet<>(spaces.keySet())) {
            Space space = spaces.get(name);
            SpaceConfig spaceConfig = universeConfig.getSpaces().get(name);
            if (space.isActive() && (spaceConfig == null || !spaceConfig.isActive())) {
                // FIXME: close/remove inactive space?
                space.spaceConfig.setActive(false);
                log.log(PASSIVATE, SPACE, name, "no longer exists " + space.spaceConfig);
            }
        }
    }

    private void configureProducts(UniverseConfig universeConfig) {
        // Add or update products from new configuration.
        Map<String, List<ChannelConfig>> productsConfig = universeConfig.getProducts();
        for (String name : new TreeSet<>(productsConfig.keySet())) {
            List<ChannelConfig> newProduct = productsConfig.get(name);
            List<ChannelConfig> oldProduct = products.get(name);
            if (oldProduct == null) {
                log.log(Log.Action.ADD, PRODUCT, name, newProduct);
                products.put(name, newProduct);
                // check newly added product against already existing space
                if (isActiveSpace(name))
                    log.log(WARNING, PRODUCT, name, "collides with active space with the same name");
            } else if (!oldProduct.equals(newProduct)) {
                log.log(UPDATE, PRODUCT, name, newProduct);
                products.put(name, newProduct);
            }
        }
        // Remove old products which are missing in the new configuration.
        for (String name : new TreeSet<>(products.keySet())) {
            if (productsConfig.get(name) == null) {
                log.log(Log.Action.REMOVE, PRODUCT, name, products.get(name));
                products.remove(name);
            }
        }

        // Keep copy of resolved channels for proper reporting later.
        Map<String, String> oldChannels = new HashMap<>();
        channels.forEach((k, v) -> oldChannels.put(k, v.toString()));
        channels.clear();
        // Create (resolve) channels for all spaces and products. Note that collisions are resolved via products.
        // FIXME: visit only active spaces.
        for (String name : new TreeSet<>(spaces.keySet())) {
            getOrCreateChannels(name);
        }
        for (String name : new TreeSet<>(products.keySet())) {
            getOrCreateChannels(name);
        }
        // Report newly resolved channels.
        for (String name : new TreeSet<>(channels.keySet())) {
            String s = channels.get(name).toString();
            String old = oldChannels.get(name);
            if (!s.equals(old))
                log.log(Log.Action.RESOLVE, CHANNEL, name, s);
        }
        // Report removed channels.
        for (String name : new TreeSet<>(oldChannels.keySet())) {
            if (channels.get(name) == null)
                log.log(Log.Action.REMOVE, CHANNEL, name, oldChannels.get(name));
        }
    }

    /**
     * Returns the list of channels for the specified space or product name.
     * <p>
     * If the name corresponds to a product, it resolves the product's channel configuration,
     * which may involve recursively calling this method for base channels.
     * If the name corresponds to a space, it returns the channels defined by that space.
     * Product definitions take precedence over space definitions if both exist with the same name.
     * <p>
     * This method detects recursion or self-references during product resolution and
     * returns an {@link #INVALID} empty list if such a cycle is detected.
     *
     * @param productName the name of the space or product to resolve
     * @return the list of resolved channels; {@link #INVALID} if the name is unknown or a recursive reference is detected
     */
    @Nonnull
    private List<Channel> getOrCreateChannels(String productName) {
        List<Channel> result = channels.get(productName);
        if (result == UNDER_CONSTRUCTION) {
            log.log(WARNING, PRODUCT, productName, "recursive or self-reference");
            return INVALID;
        }
        if (result != null)
            return result;
        if (products.get(productName) != null) { // product definitions have precedence
            channels.put(productName, UNDER_CONSTRUCTION);
            result = createProductChannels(products.get(productName));
            if (result == INVALID) {
                log.log(WARNING, PRODUCT, productName, "internal failure while building product");
            }
            channels.put(productName, result);
        } else { // check spaces if didn't match with a product
            Space space = spaces.get(productName);
            if (space != null && space.isActive()) {
                result = createSpaceChannels(space);
                channels.put(productName, result);
            } else {
                log.log(WARNING, PRODUCT, productName, "unknown space or product");
                result = INVALID;
            }
        }
        return result;
    }

    private List<Channel> createProductChannels(List<ChannelConfig> productConfig) {
        List<Channel> result = new ArrayList<>();
        for (ChannelConfig channelConfig : productConfig) {
            if (channelConfig.getContracts().isEmpty())
                continue;
            List<Channel> baseChannels = getOrCreateChannels(channelConfig.getBase());
            if (baseChannels == INVALID) {
                log.log(WARNING, PRODUCT, channelConfig.getBase(), "invalid/unknown product or space");
                return INVALID;
            }
            QDFilter filter = getFilter(channelConfig.getFilter());
            EnumSet<QDContract> contracts = EnumSet.copyOf(channelConfig.getContracts());
            long aggregationPeriod = channelConfig.getAggregationPeriod().getTime();
            boolean allowWildcards = channelConfig.isAllowWildcards();
            for (Channel base : baseChannels) {
                EnumSet<QDContract> newContracts = EnumSet.copyOf(contracts);
                newContracts.retainAll(base.contracts);
                if (newContracts.isEmpty())
                    continue;
                result.add(new Channel(base.space,
                    CompositeFilters.makeAnd(base.filter, filter),
                    newContracts,
                    Math.max(base.aggregationPeriod, aggregationPeriod),
                    base.allowWildcards || allowWildcards));
            }
        }
        return result;
    }

    private List<Channel> createSpaceChannels(Space space) {
        List<Channel> result = new ArrayList<>();
        if (space.isActive() && !space.endpoint.getContracts().isEmpty()) {
            QDFilter filter = getFilter(space.spaceConfig.getFilter());
            result.add(new Channel(space, filter, EnumSet.copyOf(space.endpoint.getContracts()), 0, false));
        }
        return result;
    }
}
