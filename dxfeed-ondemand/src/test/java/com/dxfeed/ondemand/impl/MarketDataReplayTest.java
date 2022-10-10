/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
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
import java.util.ArrayList;

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
        MarketDataReplay mock = new MarketDataReplay() {
            @Override
            InetAddress[] getAllByName(String host) throws UnknownHostException {
                switch (host) {
                    case "host1":
                        return new InetAddress[] {InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1")};
                    case "host2":
                        return new InetAddress[] {InetAddress.getByName("127.0.0.2")};
                    case "host3":
                        return new InetAddress[] {InetAddress.getByName("127.0.0.3")};
                    case "host4":
                        // duplicates host1 and host2 IPs. Shall be eliminated if other parts are identical
                        return new InetAddress[] {InetAddress.getByName("127.0.0.1"),
                            InetAddress.getByName("127.0.0.2")};
                }
                throw new UnknownHostException("Unknown host: " + host);
            }
        };
        ArrayList<String> addresses =
            mock.getResolvedAddresses("https://host3/MarketDataReplay,host4:8080,host2,host1:8080");
        assertEquals(5, addresses.size());
        //System.out.println("addresses = " + addresses);
        assertEquals("https://127.0.0.3/MarketDataReplay", addresses.get(0));
        assertEquals("http://127.0.0.1:8080/MarketDataReplay", addresses.get(1));
        assertEquals("http://127.0.0.2:8080/MarketDataReplay", addresses.get(2));
        assertEquals("http://127.0.0.2/MarketDataReplay", addresses.get(3));
        assertEquals("http://[0:0:0:0:0:0:0:1]:8080/MarketDataReplay", addresses.get(4));
    }
}
