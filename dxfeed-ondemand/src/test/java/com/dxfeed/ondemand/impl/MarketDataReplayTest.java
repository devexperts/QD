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
package com.dxfeed.ondemand.impl;

import org.junit.Test;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MarketDataReplayTest {

    @Test
    public void testAddrToURL() throws MalformedURLException {
        // host[:port]
        checkAddrToURL("127.0.0.1", "http://127.0.0.1/MarketDataReplay");
        checkAddrToURL("127.0.0.1:8080", "http://127.0.0.1:8080/MarketDataReplay");
        checkAddrToURL("localhost", "http://localhost/MarketDataReplay");
        checkAddrToURL("localhost:8080", "http://localhost:8080/MarketDataReplay");
        checkAddrToURL("[::ffff:d05d:67aa]", "http://[::ffff:d05d:67aa]/MarketDataReplay");
        checkAddrToURL("[::ffff:d05d:67aa]:8080", "http://[::ffff:d05d:67aa]:8080/MarketDataReplay");

        // URL
        checkAddrToURL("http://localhost/MarketDataReplay", "http://localhost/MarketDataReplay");
        checkAddrToURL("https://localhost/MarketDataReplay", "https://localhost/MarketDataReplay");
        checkAddrToURL("https://127.0.0.1:11443/MDReplay", "https://127.0.0.1:11443/MDReplay");
        checkAddrToURL("https://[::ffff:d05d:67aa]:11443/MDReplay", "https://[::ffff:d05d:67aa]:11443/MDReplay");
    }

    private static void checkAddrToURL(String addr, String expected) throws MalformedURLException {
        URL url = MarketDataReplay.addrToURL(addr);
        assertEquals(expected, url.toString());
    }

    @Test
    public void testGetResolvedAddresses() throws Exception {
        // test host preservation for hosts with single IP and single host configured
        assertEquals(Collections.singletonList("http://host1/MarketDataReplay"),
            resolve("http://host1/MarketDataReplay", "host1=127.0.0.1"));
        assertEquals(Collections.singletonList("https://host1/MarketDataReplay"),
            resolve("https://host1/MarketDataReplay", "host1=127.0.0.1"));

        // test host preservation for hosts with single IP and several hosts configured
        assertEquals(Arrays.asList(
                "http://host1/MarketDataReplay",
                "http://host2/MarketDataReplay"),
            resolve("http://host1/MarketDataReplay,http://host2/MarketDataReplay",
                "host1=127.0.0.1", "host2=127.0.0.2"));
        assertEquals(Arrays.asList(
                "https://host1/MarketDataReplay",
                "https://host2/MarketDataReplay"),
            resolve("https://host1/MarketDataReplay,https://host2/MarketDataReplay",
                "host1=127.0.0.1", "host2=127.0.0.2"));

        // test host for 2 IPs - http shall use both IPs in 2 URLs, https shall use original host in 1 URL
        assertEquals(Arrays.asList(
                "http://127.0.0.1/MarketDataReplay",
                "http://127.0.0.2/MarketDataReplay"),
            resolve("http://host/MarketDataReplay", "host=127.0.0.1,127.0.0.2"));
        assertEquals(Arrays.asList(
                "https://host/MarketDataReplay"),
            resolve("https://host/MarketDataReplay", "host=127.0.0.1,127.0.0.2"));

        // an original complex test that includes: URL expansion to full form,
        // host preservation for hosts with single IP, IP sorting within single host resolving,
        // IP deduplication when several hosts route to same IP with all other URL parts being identical
        List<String> actual = resolve("host1:8080,host2,host3:8080",
            "host1=127.0.0.2,127.0.0.1", "host2=127.0.0.2", "host3=127.0.0.1,::1");
        List<String> expected = Arrays.asList(
            "http://127.0.0.1:8080/MarketDataReplay",
            "http://127.0.0.2:8080/MarketDataReplay",
            "http://host2/MarketDataReplay",
            "http://[0:0:0:0:0:0:0:1]:8080/MarketDataReplay");
        assertEquals(expected, actual);
    }

    // DNS is: "host=ip" or "host=ip,ip,ip" etc
    private static List<String> resolve(String address, String... dns) {
        MarketDataReplay mock = new MarketDataReplay() {
            @Override
            InetAddress[] getAllByName(String host) throws UnknownHostException {
                for (String s : dns) {
                    if (s.startsWith(host + "=")) {
                        String[] ips = s.substring(host.length() + 1).split(",");
                        InetAddress[] result = new InetAddress[ips.length];
                        for (int i = 0; i < result.length; i++) {
                            result[i] = InetAddress.getByName(ips[i]);
                        }
                        return result;
                    }
                }
                throw new UnknownHostException("Unknown host: " + host);
            }
        };
        return mock.getResolvedAddresses(address);
    }
}
