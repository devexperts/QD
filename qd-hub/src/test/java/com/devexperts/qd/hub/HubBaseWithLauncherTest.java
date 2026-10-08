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
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.tools.launcher.Launcher;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A model hub test example using a complete Launcher configuration starting from a complete text config.
 */
@SuppressWarnings({"resource", "BusyWait", "RedundantThrows"})
public class HubBaseWithLauncherTest {

    Logging log = Logging.getLogging(HubBaseWithLauncherTest.class);

    @Test
    public void testBaseFunctionality() throws Exception {
        Config config = ConfigFactory.parseString("{\n" +
            "    launcherConfig: {\n" +
            "        configCheckPeriod = 0\n" +
            "        configReadPeriod = 0\n" +
            "    }\n" +
            "\n" +
            "    endpointProperties: {\n" +
            "        mars.root = \"hub-test\"\n" +
            "        #jmx.html.port = 8000\n" +
            "        monitoring.stat = 10s\n" +
            "    }\n" +
            "\n" +
            "    modules: [{\n" +
            "        # Common module configuration\n" +
            "        type = Hub\n" +
            "        name = test-hub\n" +
            "\n" +
            "        universeConfig: {\n" +
            "            spaces: {\n" +
            "               A: {}\n" +
            "               B: {}\n" +
            "            }\n" +
            "           products: {\n" +
            "               mixAB: [{base=A, filter=\"A*\"}, {base=B, filter=\"B*\"}]\n" +
            "           }\n" +
            "        }\n" +
            "\n" +
            "        # Hub uplinks (aka distributors)\n" +
            "        uplinks: {\n" +
            "            ul-A: {\n" +
            "                address = \":0\"\n" +
            "                spaces: [A]\n" +
            "            }\n" +
            "            ul-B: {\n" +
            "                address = \":0\"\n" +
            "                spaces: [B]\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        # Hub downlinks (aka agents)\n" +
            "        downlinks: {\n" +
            "            dl-A: {\n" +
            "                address = \":0\"\n" +
            "                spaces: [A]\n" +
            "            }\n" +
            "            dl-B: {\n" +
            "                address = \":0\"\n" +
            "                spaces: [B]\n" +
            "            }\n" +
            "            dl-AB: {\n" +
            "                address = \":0\"\n" +
            "                spaces: []\n" +
            "                products: [mixAB]\n" +
            "            }\n" +
            "        }" +
            "    }]\n" +
            "}\n");

        Promise<Integer> uplinkAPromise = ServerSocketTestHelper.createPortPromise("ul-A");
        Promise<Integer> uplinkBPromise = ServerSocketTestHelper.createPortPromise("ul-B");
        Promise<Integer> downlinkAPromise = ServerSocketTestHelper.createPortPromise("dl-A");
        Promise<Integer> downlinkBPromise = ServerSocketTestHelper.createPortPromise("dl-B");
        Promise<Integer> downlinkABPromise = ServerSocketTestHelper.createPortPromise("dl-AB");

        Launcher launcher = Launcher.startTestLauncher(config, true);
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

        /*
        while (launcher.isActive()) {
            Thread.sleep(1000);
        }
        */

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
