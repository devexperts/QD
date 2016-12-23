/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.ipf.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import junit.framework.TestCase;

/**
 * Unit test for {@link InstrumentProfileReader} class.
 */
public class InstrumentProfileReaderTest extends TestCase {
    public InstrumentProfileReaderTest(String s) {
        super(s);
    }

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

    public void testProfile() throws IOException {
        parse2("#::=TYPE\n\n");
        parse2("#::=TYPE,SYMBOL\n,\n,", "", "", "", "");
        parse2("#::=TYPE,SYMBOL\n,IBM", "", "IBM");
        parse2("#::=TYPE,SYMBOL\n,IBM\n,AAPL", "", "IBM", "", "AAPL");
        parse2("#STOCK::=TYPE\nSTOCK\nSTOCK", "STOCK", "", "STOCK", "");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,", "STOCK", "");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,IBM", "STOCK", "IBM");
        parse2("#STOCK::=TYPE,SYMBOL\nSTOCK,\nSTOCK,IBM\nSTOCK,AAPL", "STOCK", "", "STOCK", "IBM", "STOCK", "AAPL");
        parse2("#STOCK::=TYPE,SYMBOL\n#ETF::=TYPE,SYMBOL,DESCRIPTION\nSTOCK,IBM\nETF,AAPL,Apple", "STOCK", "IBM", "ETF", "AAPL");
        parse2("#STOCK::=TYPE,SYMBOL\n#ETF::=TYPE,SYMBOL,DESCRIPTION,COUNTRY\n#REMOVED::=TYPE,SYMBOL\nSTOCK,IBM\nETF,AAPL,Apple,US\nREMOVED,IBM", "STOCK", "IBM", "ETF", "AAPL", "REMOVED", "IBM");
    }

    private void parse2(String ipf, String... typeSymbolPairs) throws IOException {
        parse(ipf, typeSymbolPairs);
        if (!ipf.isEmpty() && !ipf.startsWith("\n") && !ipf.startsWith("\r"))
            parse("\n\n\n" + ipf.replace("\n", "\n\n\n") + "\n\n\n", typeSymbolPairs);
    }

    private void parse(String ipf, String[] typeSymbolPairs) throws IOException {
        assertTrue("wrong number of arguments", typeSymbolPairs.length % 2 == 0);
        List<InstrumentProfile> profiles = new InstrumentProfileReader().read(new ByteArrayInputStream(ipf.getBytes("UTF-8")));
        assertEquals("wrong number of instruments", typeSymbolPairs.length / 2, profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            InstrumentProfile ip = profiles.get(i);
            assertEquals("wrong type", typeSymbolPairs[i * 2], ip.getType());
            assertEquals("wrong symbol", typeSymbolPairs[i * 2 + 1], ip.getSymbol());
        }
    }
}
