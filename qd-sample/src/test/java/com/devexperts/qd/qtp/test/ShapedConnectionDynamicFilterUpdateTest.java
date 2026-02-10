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
package com.devexperts.qd.qtp.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.samplecert.SampleCert;
import com.devexperts.rmi.test.NTU;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.event.market.Quote;
import com.dxfeed.ipf.filter.IPFSymbolFilter;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// shaped connections used to have problems with ipf filter updates (see QD-1721)
@RunWith(Parameterized.class)
public class ShapedConnectionDynamicFilterUpdateTest {

    private static final long WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    static final Logging log = Logging.getLogging(ShapedConnectionDynamicFilterUpdateTest.class);

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Object[] params() {
        return new Object[] {
            "", // no codecs
            "shaped[outLimit=1000K]+",
            "ssl+",
            "shaped[outLimit=2000K]+shaped[outLimit=1000K]+",
            "ssl+shaped[outLimit=1000K]+",
            "shaped[outLimit=1000K]+ssl+"
        };
    }

    @Parameterized.Parameter(0)
    public String codecSpec;

    private String testId;
    private DXEndpoint server;
    private DXEndpoint client;
    private File ipfFile;
    private long baseTime = System.currentTimeMillis() / 1000 * 1000;

    @Before
    public void setUp() throws Exception {
        testId = UUID.randomUUID().toString();
        SampleCert.init();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
        if (ipfFile != null)
            ipfFile.delete();
    }

    @Test
    public void testCodecWithDynamicFilterUpdate() throws Exception {
        String name = testId + "-pub";
        Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(name);
        server = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .build();

        ObservableSubscription<Quote> sub = server.getPublisher().getSubscription(Quote.class);
        sub.addChangeListener(symbols -> {
            log.debug("uplink " + name + " received subscription" + symbols);
            List<Quote> events = symbols.stream()
                .map(s -> makeQuote(s.toString(), 1000))
                .collect(Collectors.toList());
            if (!events.isEmpty()) {
                server.getPublisher().publishEvents(events);
            }
        });

        boolean useSsl = codecSpec.contains("ssl") || codecSpec.contains("tls");
        server.connect((useSsl ? "ssl[isServer=true]+" : "") + ":0[name=" + name + ",bindAddr=127.0.0.1]");
        int port = portPromise.await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS);

        ipfFile = File.createTempFile("dynamic", ".ipf");
        String ipfName =  ipfFile.getCanonicalPath().replaceAll("\\\\", "/");
        writeIpfFile(ipfFile, "IBM", "MSFT");

        Set<String> receivedEvents = Collections.synchronizedSet(new HashSet<>());
        client = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.FEED)
            .withName("client")
            .build();
        DXFeedSubscription<Quote> subscription = client.getFeed().createSubscription(Quote.class);
        subscription.addEventListener(events -> events.forEach(e -> receivedEvents.add(e.getEventSymbol())));
        subscription.addSymbols("AAPL", "IBM", "MSFT");
        // (shaped[outLimit=2000]+shaped[outLimit=1000]+demo.dxfeed.com:7300(filter=ipf[./x.ipf,update=1s]))
        // client.connect("(shaped[outLimit=1000]+127.0.0.1:" + port + "(filter=ipf[" + ipfName + ",update=1s]))");
        // client.connect("ssl+127.0.0.1:" + port);

        // Create shared(!) registered(!!) instance of dynamic IPF filter,
        // which will provide canonical specification via toString() method
        IPFSymbolFilter ipfFilter =
            IPFSymbolFilter.create(QDFactory.getDefaultScheme(), "ipf[" + ipfName + ",update=1s]");

        client.connect("(" + codecSpec + "127.0.0.1:" + port + "(filter=" + ipfFilter + "))");

        // Wait until the connection is established
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT, 10, () -> client.getState() == DXEndpoint.State.CONNECTED));

        // Wait until IBM & MSFT quotes received
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT, 10, () -> receivedEvents.size() >= 2));
        assertEquals(2, receivedEvents.size());
        assertTrue(receivedEvents.contains("IBM"));
        assertTrue(receivedEvents.contains("MSFT"));

        receivedEvents.clear();

        // Force dynamic filter update
        writeIpfFile(ipfFile, "AAPL");
        ((IPFSymbolFilter) ipfFilter.getUpdatedFilter()).forceUpdate();

        // client should eventually resubscribe using the new ipf filter
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT, 10, () -> receivedEvents.size() >= 1));
        assertEquals(1, receivedEvents.size());
        assertTrue(receivedEvents.contains("AAPL"));
    }

    private static void writeIpfFile(File file, String... symbols) throws IOException {
        List<String> ipf = new ArrayList<>();
        ipf.add("#STOCK::=TYPE,SYMBOL");
        for (String s : symbols) {
            ipf.add("STOCK," + s);
        }
        ipf.add("##COMPLETE");

        Files.write(file.toPath(), ipf);
    }

    private Quote makeQuote(String symbol, double basePrice) {
        Quote quote = new Quote(symbol);
        quote.setBidPrice(basePrice - 1);
        quote.setBidSizeAsDouble(10);
        quote.setBidTime(baseTime - 1000);
        quote.setAskPrice(basePrice + 1);
        quote.setAskSizeAsDouble(12);
        quote.setAskTime(baseTime - 2000);
        return quote;
    }
}
