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
package com.dxfeed.ipf.test;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for {@link InstrumentProfileReader} class.
 */
public class InstrumentProfileReaderTest {

    @Test
    public void testSpecial() throws IOException {
        parse2("");
        parse2("\n");
        parse2("\n\n");
        parse2("\n\n\n");
        parse2("\r\n");
        parse2("\r\n\r\n");
        parse2("\r\n\r\n\r\n");
        parse2("# comment");
        parse2("#");
        parse2("##");
        parse2("##COMPLETE");
        parse2("# comment\n#\n##\n##COMPLETE");
    }

    @Test
    public void testDefinition() throws IOException {
        parse2("#::=TYPE");
        parse2("#::=TYPE,SYMBOL");
        parse2("#STOCK::=TYPE");
        parse2("#STOCK::=TYPE,SYMBOL");
        parse2("#REMOVED::=TYPE,SYMBOL");
        String s = "#::=TYPE\n#::=TYPE,SYMBOL\n#STOCK::=TYPE\n#STOCK::=TYPE,SYMBOL\n#REMOVED::=TYPE,SYMBOL";
        parse2(s);
        parse2(s + "\n" + s + "\n" + s);
    }

    @Test
    public void testProfile() throws IOException {
        parse2("#::=TYPE\n\n");
        parse2("#::=TYPE,SYMBOL\n,\n,", "", "", "", "");
        parse2("#::=TYPE,SYMBOL\n,IBM", "", "IBM");
        parse2("#::=TYPE,SYMBOL\n,IBM\n,AAPL", "", "IBM", "", "AAPL");
        parse2("#STOCK::=TYPE\nSTOCK\nSTOCK", "STOCK", "", "STOCK", "");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,", "STOCK", "");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,IBM", "STOCK", "IBM");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,\nSTOCK,IBM\nSTOCK,AAPL", "STOCK", "", "STOCK", "IBM", "STOCK", "AAPL");
        parse2("#STOCK::=TYPE,SYMBOL\n#ETF::=TYPE,SYMBOL,DESCRIPTION\nSTOCK,IBM\nETF,AAPL,Apple",
            "STOCK", "IBM", "ETF", "AAPL");
        parse2(
            "#STOCK::=TYPE,SYMBOL\n#ETF::=TYPE,SYMBOL,DESCRIPTION,COUNTRY\n#REMOVED::=TYPE,SYMBOL\n" +
            "STOCK,IBM\nETF,AAPL,Apple,US\nREMOVED,IBM",
            "STOCK", "IBM", "ETF", "AAPL", "REMOVED", "IBM");
    }

    private void parse2(String ipf, String... typeSymbolPairs) throws IOException {
        parse(ipf, typeSymbolPairs);
        if (!ipf.isEmpty() && !ipf.startsWith("\n") && !ipf.startsWith("\r"))
            parse("\n\n\n" + ipf.replace("\n", "\n\n\n") + "\n\n\n", typeSymbolPairs);
    }

    private void parse(String ipf, String[] typeSymbolPairs) throws IOException {
        assertEquals("wrong number of arguments", 0, typeSymbolPairs.length % 2);
        List<InstrumentProfile> profiles = new InstrumentProfileReader() {
            @Override
            protected void handleIncomplete(String address) {
                // Skip handling for tests
            }
        }.read(new ByteArrayInputStream(ipf.getBytes(StandardCharsets.UTF_8)), "test");
        assertEquals("wrong number of instruments", typeSymbolPairs.length / 2, profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            InstrumentProfile ip = profiles.get(i);
            assertEquals("wrong type", typeSymbolPairs[i * 2], ip.getType());
            assertEquals("wrong symbol", typeSymbolPairs[i * 2 + 1], ip.getSymbol());
        }
    }
}
