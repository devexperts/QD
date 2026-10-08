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
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.qd.hub.config.ChannelConfig;
import com.devexperts.qd.hub.config.DownlinkConfig;
import com.devexperts.qd.hub.config.HubConfig;
import com.devexperts.qd.hub.config.SpaceConfig;
import com.devexperts.qd.hub.config.UniverseConfig;
import com.devexperts.qd.hub.config.UplinkConfig;
import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A model Hub test configuring Hub module without launcher
 * <ul>
 * <li>emulates a required execution context without creating Launcher
 * <li>constructs hub configuration programmatically as a HubConfig bean instance
 * </ul>
 */
@SuppressWarnings({"resource", "BusyWait"})
public class HubBaseIsolatedTest {

    Logging log = Logging.getLogging(HubBaseIsolatedTest.class);
    private JMXEndpoint jmxEndpoint;
    private MARSEndpoint marsEndpoint;
    private Hub hub;

    @After
    public void tearDown() {
        if (hub != null)
            hub.close();
        if (marsEndpoint != null)
            marsEndpoint.release();
        if (jmxEndpoint != null)
            jmxEndpoint.release();
    }

    @Test
    public void testBaseFunctionality() throws InterruptedException {

        Promise<Integer> uplinkAPromise = ServerSocketTestHelper.createPortPromise("ul-A");
        Promise<Integer> uplinkBPromise = ServerSocketTestHelper.createPortPromise("ul-B");
        Promise<Integer> downlinkAPromise = ServerSocketTestHelper.createPortPromise("dl-A");
        Promise<Integer> downlinkBPromise = ServerSocketTestHelper.createPortPromise("dl-B");
        Promise<Integer> downlinkABPromise = ServerSocketTestHelper.createPortPromise("dl-AB");

        //Launcher launcher = Launcher.startTestLauncher(config, true);

        Properties endpointProps = new Properties();
        endpointProps.put("mars.root", "hub-test");

        // optional: activate monitoring
        endpointProps.put("monitoring.stat", "10s");

        // optional: JMX/MARS interfaces
        // jmxEndpoint = JMXEndpoint.newBuilder().withProperties(endpointProps).acquire();
        // marsEndpoint = MARSEndpoint.newBuilder().withProperties(endpointProps).acquire();

        HubConfig hubConfig = new HubConfig()
            .withUniverseConfig(new UniverseConfig()
                .withSpace(new SpaceConfig("A"))
                .withSpace(new SpaceConfig("B"))
                .withProduct("mixAB",
                    new ChannelConfig("A").withFilter("A*"),
                    new ChannelConfig("B").withFilter("B*")))
            .withUplink(new UplinkConfig("ul-A", ":0").withSpace("A"))
            .withUplink(new UplinkConfig("ul-B", ":0").withSpace("B"))
            .withDownlink(new DownlinkConfig("dl-A", ":0").withSpace("A"))
            .withDownlink(new DownlinkConfig("dl-B", ":0").withSpace("B"))
            .withDownlink(new DownlinkConfig("dl-AB", ":0").withProduct(new ChannelConfig("mixAB")));

        hub = (Hub) new HubFactory().createModule(new TestModuleContext("test-hub", endpointProps));
        hub.start(hubConfig);

        int uplinkA = uplinkAPromise.await(10, TimeUnit.SECONDS);
        int uplinkB = uplinkBPromise.await(10, TimeUnit.SECONDS);
        int downlinkA = downlinkAPromise.await(10, TimeUnit.SECONDS);
        int downlinkB = downlinkBPromise.await(10, TimeUnit.SECONDS);
        int downlinkAB = downlinkABPromise.await(10, TimeUnit.SECONDS);

        List<String> symbols = Arrays.asList("A1", "A2", "B1", "B2");
        Map<String, Quote> quotesA =
            symbols.stream().collect(Collectors.toConcurrentMap(s -> s, s -> makeQuote(s, 1000)));
        Map<String, Quote> quotesB =
            symbols.stream().collect(Collectors.toConcurrentMap(s -> s, s -> makeQuote(s, 2000)));
        Map<String, Quote> quotesAB = symbols.stream()
            .collect(Collectors.toConcurrentMap(s -> s, s -> (s.startsWith("A") ? quotesA : quotesB).get(s)));

        connectUplink(uplinkA, "uplinkA", quotesA::get);
        connectUplink(uplinkB, "uplinkB", quotesB::get);

        connectClient(downlinkA, "clientA", symbols, quotesA);
        connectClient(downlinkB, "clientB", symbols, quotesB);
        connectClient(downlinkAB, "client-mixAB", symbols, quotesAB);

        // await("hub is running").forever().pollInterval(1, TimeUnit.SECONDS).until(() -> !hub.isActive());
    }

    private void connectClient(int port, String name, List<String> symbols, Map<String, Quote> expected) {
        try (DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withName(name)
            .withRole(DXEndpoint.Role.FEED)
            .build())
        {
            List<Quote> received;
            received = Collections.synchronizedList(new ArrayList<>());
            DXFeedSubscription<Quote> sub = endpoint.getFeed().createSubscription(Quote.class);
            sub.addEventListener(events -> {
                log.info("client " + name + "received: " + prettyPrint(events));
                received.addAll(events);
            });
            sub.addSymbols(symbols);
            endpoint.connect("127.0.0.1:" + port);

            Awaitility.waitAtMost(10, TimeUnit.SECONDS).until(() -> received.size() >= expected.size());
            assertEquals(expected.size(), received.size());
            Map<String, Quote> remaining = new HashMap<>(expected);
            received.forEach((Quote q) -> {
                Quote expectedQuote = remaining.remove(q.getEventSymbol());
                assertNotNull("unexpected quote symbol: " + q, expectedQuote);
                assertEquals("bidTime", expectedQuote.getBidTime(), q.getBidTime());
                assertEquals("bidPrice", expectedQuote.getBidPrice(),  q.getBidPrice(), 0);
                assertEquals("askTime", expectedQuote.getAskTime(), q.getAskTime());
                assertEquals("askPrice", expectedQuote.getAskPrice(),  q.getAskPrice(), 0);
            });
            
        }
    }

    private void connectUplink(int port, String name, Function<String, Quote> provider) {
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withName(name)
            .withRole(DXEndpoint.Role.PUBLISHER)
            .build();

        ObservableSubscription<Quote> sub = endpoint.getPublisher().getSubscription(Quote.class);
        sub.addChangeListener(symbols -> {
            log.info("uplink " + name + " received subscription" + symbols);
            List<Quote> events = symbols.stream()
                .map(s -> provider.apply(s.toString()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            if (!events.isEmpty()) {
                log.info("uplink " + name + " publish: " + prettyPrint(events));
                endpoint.getPublisher().publishEvents(events);
            }
        });
        endpoint.connect("127.0.0.1:" + port);

        Awaitility.waitAtMost(10, TimeUnit.SECONDS).until(() -> endpoint.getState() == DXEndpoint.State.CONNECTED);
        log.info("uplink " + name + " connected.");
    }

    private Quote makeQuote(String symbol, double basePrice) {
        long now = System.currentTimeMillis() / 1000 * 1000; // millis lost by default???
        Quote quote = new Quote(symbol);
        quote.setBidPrice(basePrice - 1);
        quote.setBidSizeAsDouble(10);
        quote.setBidTime(now - 1000);
        quote.setAskPrice(basePrice + 1);
        quote.setAskSizeAsDouble(12);
        quote.setAskTime(now - 2000);
        return quote;
    }

    String prettyPrint(Collection<?> c) {
        return c.stream().map(Object::toString).collect(Collectors.joining(",\n", "[\n", "\n]"));
    }

}
