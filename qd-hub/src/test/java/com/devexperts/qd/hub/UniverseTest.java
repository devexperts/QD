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

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.hub.config.ChannelConfig;
import com.devexperts.qd.hub.config.SpaceConfig;
import com.devexperts.qd.hub.config.UniverseConfig;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.TimePeriod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UniverseTest {

    private static final Set<QDContract> ALL_CONTRACTS = Collections.unmodifiableSet(EnumSet.allOf(QDContract.class));

    private static final String MODULE_NAME = "test-hub";
    Logging log = Logging.getLogging(UniverseTest.class);
    private TestModuleContext context;
    private Properties endpointProps;
    private DataScheme scheme;
    private Log eventLog;
    private Universe universe;

    @Before
    public void setUp() {
        endpointProps = new Properties();
        endpointProps.put("mars.root", "test-hub");
        // optional: activate monitoring
        endpointProps.put("monitoring.stat", "10s");
        context = new TestModuleContext(MODULE_NAME, endpointProps);
        scheme = QDFactory.getDefaultScheme();
        eventLog = new Log(context.getEventLog());
        universe = new Universe(MODULE_NAME, scheme, eventLog, context.getEndpointConfig().getProperties());
    }

    @After
    public void tearDown() {
        // FIXME: universe.close()
    }

    @Test
    public void testBaseFunctions() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("A"))
            .withSpace(new SpaceConfig("B"))
            .withProduct("mixAB",
                new ChannelConfig("A").withFilter("A*"),
                new ChannelConfig("B").withFilter("B*"));

        universe.configure(config);

        checkSpaceConfigMatch(config.getSpaces().get("A"), universe.getSpaceEndpoint("A"));
        checkSpaceConfigMatch(config.getSpaces().get("B"), universe.getSpaceEndpoint("B"));

        List<Universe.Channel> channelsA = universe.getChannels("A");
        assertEquals(1, channelsA.size());
        checkChannel(channelsA.get(0), "A", "*", ALL_CONTRACTS, 0, false);

        List<Universe.Channel> channelsB = universe.getChannels("B");
        assertEquals(1, channelsB.size());
        checkChannel(channelsB.get(0), "B", "*", ALL_CONTRACTS, 0, false);

        List<Universe.Channel> mixAB = universe.getChannels("mixAB");
        assertEquals(2, mixAB.size());
        checkChannel(mixAB.get(0), "A", "A*", ALL_CONTRACTS, 0, false);
        checkChannel(mixAB.get(1), "B", "B*", ALL_CONTRACTS, 0, false);
    }

    @Test
    public void testSpaceProperties() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("A")
                .withFilter("A*")
                .withContracts(EnumSet.of(QDContract.STREAM))
                .withEventTimeSequence(true)
                .withEnableWildcards(false) // FIXME: default == true?
                .withStoreEverything(true)
                .withStoreEverythingFilter("AA*")
            );

        universe.configure(config);

        checkSpaceConfigMatch(config.getSpaces().get("A"), universe.getSpaceEndpoint("A"));
    }

    @Test
    public void testProductProperties() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("S"))
            .withProduct("P",
                new ChannelConfig("S")
                    .withFilter("A*")
                    .withContracts(EnumSet.of(QDContract.STREAM, QDContract.HISTORY))
                    .withAllowWildcards(true) // FIXME: default == false?
                    .withAggregationPeriod(TimePeriod.valueOf(500))
            );

        universe.configure(config);

        List<Universe.Channel> channels = universe.getChannels("P");
        assertEquals(1, channels.size());
        checkChannel(channels.get(0), "S", "A*", EnumSet.of(QDContract.STREAM, QDContract.HISTORY), 500, true);
    }

    @Test
    public void testRecursiveProduct() {
        UniverseConfig config = new UniverseConfig()
            .withProduct("A", new ChannelConfig("B"))
            .withProduct("B", new ChannelConfig("A"));

        universe.configure(config);
        assertSame(Universe.INVALID, universe.getChannels("A"));
        assertSame(Universe.INVALID, universe.getChannels("B"));
    }

    @Test
    public void testSelfReferencingProduct() {
        UniverseConfig config = new UniverseConfig()
            .withProduct("A", new ChannelConfig("A"));
        universe.configure(config);
        assertSame(Universe.INVALID, universe.getChannels("A"));
    }

    @Test
    public void testProductSelfReferenceWithSpaceCollision() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("S").withFilter("SF"))
            .withProduct("S", new ChannelConfig("S").withFilter("PF"));

        universe.configure(config);

        // Product should take precedence, but it references itself (base="S")
        // When resolving product "S", it sets "S" to UNDER_CONSTRUCTION.
        // Then it tries to resolve base "S". getOrCreateChannels("S") sees UNDER_CONSTRUCTION and returns INVALID.
        assertSame(Universe.INVALID, universe.getChannels("S"));
    }

    @Test
    public void testProductSpaceCollisionPrecedence() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("Common"))
            .withSpace(new SpaceConfig("Other"))
            .withProduct("Common", new ChannelConfig("Other"));

        universe.configure(config);

        List<Universe.Channel> channels = universe.getChannels("Common");
        assertEquals(1, channels.size());
        assertEquals("Other", channels.get(0).space.name);
    }

    @Test
    public void testUpdateSpaces() {
        UniverseConfig config1 = new UniverseConfig()
            .withSpace(new SpaceConfig("A").withFilter("A1"));
        universe.configure(config1);
        checkChannel(universe.getChannels("A").get(0), "A", "A1", ALL_CONTRACTS, 0, false);

        // Update existing space
        UniverseConfig config2 = new UniverseConfig()
            .withSpace(new SpaceConfig("A")
                .withFilter("A2")
                .withStoreEverything(true)
                .withStoreEverythingFilter("A*")
                .withEnableWildcards(false)
            );
        universe.configure(config2);
        checkSpaceConfigMatch(config2.getSpaces().get("A"), universe.getSpaceEndpoint("A"));
        checkChannel(universe.getChannels("A").get(0), "A", "A2", ALL_CONTRACTS, 0, false);

        // Add new space
        UniverseConfig config3 = new UniverseConfig()
            .withSpace(new SpaceConfig("A").withFilter("A2"))
            .withSpace(new SpaceConfig("B").withFilter("B1"));
        universe.configure(config3);
        checkSpaceConfigMatch(config3.getSpaces().get("B"), universe.getSpaceEndpoint("B"));
        assertEquals(1, universe.getChannels("A").size());
        assertEquals(1, universe.getChannels("B").size());

        // Deactivate space (not passing it in new config)
        UniverseConfig config4 = new UniverseConfig()
            .withSpace(new SpaceConfig("B").withFilter("B1"));
        universe.configure(config4);
        // A is now inactive
        // FIXME: stale space endpoint is still exists and available as "inactive".
        //   Need to consider proper endpoint closing.
        assertFalse(universe.getSpaceConfig("A").isActive());
        assertNull(universe.getChannels("A")); // no channels for disabled space
        assertEquals(1, universe.getChannels("B").size());
    }

    @Test
    public void testUpdateProducts() {
        UniverseConfig config1 = new UniverseConfig()
            .withSpace(new SpaceConfig("S"))
            .withProduct("P", new ChannelConfig("S").withFilter("F1"));
        universe.configure(config1);
        checkChannel(universe.getChannels("P").get(0), "S", "F1", ALL_CONTRACTS, 0, false);

        // Update product
        UniverseConfig config2 = new UniverseConfig()
            .withSpace(new SpaceConfig("S"))
            .withProduct("P", new ChannelConfig("S").withFilter("F2").withContracts(EnumSet.of(QDContract.STREAM)));
        universe.configure(config2);
        checkChannel(universe.getChannels("P").get(0), "S", "F2", EnumSet.of(QDContract.STREAM), 0, false);

        // Add product
        UniverseConfig config3 = new UniverseConfig()
            .withSpace(new SpaceConfig("S"))
            .withProduct("P", new ChannelConfig("S").withFilter("F2"))
            .withProduct("Q", new ChannelConfig("S").withFilter("F3"));
        universe.configure(config3);
        assertEquals(1, universe.getChannels("P").size());
        assertEquals(1, universe.getChannels("Q").size());

        // Remove product
        UniverseConfig config4 = new UniverseConfig()
            .withSpace(new SpaceConfig("S"))
            .withProduct("Q", new ChannelConfig("S").withFilter("F3"));
        universe.configure(config4);
        assertNull(universe.getChannels("P"));
        assertEquals(1, universe.getChannels("Q").size());
    }

    @Test
    public void testUnknownTargetResolution() {
        UniverseConfig config = new UniverseConfig()
            .withProduct("P", new ChannelConfig("Unknown"));
        universe.configure(config);

        assertSame(Universe.INVALID, universe.getChannels("P"));
        assertNull(universe.getChannels("Unknown"));
    }

    @Test
    public void testComplexProductResolution() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("A"))
            .withSpace(new SpaceConfig("B"))
            .withSpace(new SpaceConfig("C"))
            .withProduct("P",
                new ChannelConfig("A")
                    .withFilter("AF")
                    .withContracts(EnumSet.of(QDContract.STREAM))
                    .withAggregationPeriod(TimePeriod.valueOf(100)),
                new ChannelConfig("B")
                    .withFilter("BF")
                    .withContracts(EnumSet.of(QDContract.TICKER))
                    .withAggregationPeriod(TimePeriod.valueOf(200))
            ).withProduct("MIX",
                new ChannelConfig("P"),
                new ChannelConfig("C").withFilter("C*").withAggregationPeriod(TimePeriod.valueOf(300))
            );

        universe.configure(config);

        List<Universe.Channel> channels = universe.getChannels("P");
        assertEquals(2, channels.size());
        checkChannel(channels.get(0), "A", "AF", EnumSet.of(QDContract.STREAM), 100, false);
        checkChannel(channels.get(1), "B", "BF", EnumSet.of(QDContract.TICKER), 200, false);

        channels = universe.getChannels("MIX");
        assertEquals(3, channels.size());
        checkChannel(channels.get(0), "A", "AF", EnumSet.of(QDContract.STREAM), 100, false);
        checkChannel(channels.get(1), "B", "BF", EnumSet.of(QDContract.TICKER), 200, false);
        checkChannel(channels.get(2), "C", "C*", ALL_CONTRACTS, 300, false);
    }

    @Test
    public void testChannelPropertyInheritance() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("S")
                .withFilter("S*")
                .withContracts(EnumSet.of(QDContract.STREAM, QDContract.HISTORY))
                .withEnableWildcards(false))
            .withProduct("P",
                new ChannelConfig("S")
                    .withFilter("*B")
                    .withContracts(EnumSet.of(QDContract.STREAM))
                    .withAggregationPeriod(TimePeriod.valueOf(1000))
                    .withAllowWildcards(true));
        universe.configure(config);

        List<Universe.Channel> channels = universe.getChannels("P");
        assertEquals(1, channels.size());
        Universe.Channel channel = channels.get(0);
        assertEquals("S", channel.space.name);
        assertEquals(EnumSet.of(QDContract.STREAM), channel.contracts);
        assertEquals(1000, channel.aggregationPeriod);
        assertEquals(true, channel.allowWildcards);

        // Expected filter S* AND *B. Exact representation is not guaranteed, check by filter behavior
        QDFilter filter = channel.filter;
        DataRecord record = scheme.findRecordByName("Quote");
        SymbolCodec codec = scheme.getCodec();
        assertFalse(filter.accept(QDContract.STREAM, record, codec.encode("SA"), "SA")); // Matches 'S*' but not '*B'
        assertFalse(filter.accept(QDContract.STREAM, record, codec.encode("AB"), "AB")); // Matches '*B' but not 'S*'
        assertTrue(filter.accept(QDContract.STREAM, record, codec.encode("SB"), "SB"));  // Matches both
    }

    @Test
    public void testEmptyContractFiltering() {
        UniverseConfig config = new UniverseConfig()
            .withSpace(new SpaceConfig("S").withContracts(EnumSet.of(QDContract.STREAM)))
            .withProduct("P", new ChannelConfig("S").withContracts(EnumSet.of(QDContract.HISTORY)));
        universe.configure(config);

        List<Universe.Channel> channels = universe.getChannels("P");
        assertEquals(0, channels.size());
    }

    private static void checkChannel(Universe.Channel channel,
        String space, String filter, Set<QDContract> contracts, int aggregationPeriod, boolean allowWildcards)
    {
        assertEquals("space", space, channel.space.spaceConfig.getName());
        assertEquals("filter", filter, channel.filter.toString());
        assertEquals("contracts", contracts, channel.contracts);
        assertEquals("aggregation period", aggregationPeriod, channel.aggregationPeriod);
        assertEquals("allow wildcards", allowWildcards, channel.allowWildcards);
    }


    private void checkSpaceConfigMatch(SpaceConfig spaceConfig, QDEndpoint spaceEndpoint) {
        assertEquals(spaceConfig.isActive(), universe.isActiveSpace(spaceConfig.getName()));
        Set<QDContract> contracts = spaceConfig.getContracts();
        assertEquals(contracts, spaceEndpoint.getContracts());
        assertEquals(spaceConfig.isEventTimeSequence(), spaceEndpoint.hasEventTimeSequence());

        if (contracts.contains(QDContract.STREAM))
            assertEquals(spaceConfig.isEnableWildcards(), spaceEndpoint.getStream().getEnableWildcards());
        for (QDContract contract : contracts) {
            QDCollector collector = spaceEndpoint.getCollector(contract);
            assertEquals(spaceConfig.isStoreEverything(), collector.isStoreEverything());
            // FIXME: check filter match - missing access to collector's filter
            //assertEquals(spaceConfig.getFilter(), collector.getStoreEverythingFilter());
        }
    }
}
