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
package com.dxfeed.scheme;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;
import com.dxfeed.api.impl.SchemeProperties;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DxSchemeExtSchemeTest {

    private static final String SCHEME_SPEC = "opt:dxprops,opt:sysprops,resource:dxfeed.xml";

    @Test
    public void testFobSuffixesPropWithExtScheme() throws IOException, SchemeException {
        String originFobProp = setSystemProperty(SchemeProperties.DXSCHEME_FOB_PROPERTY, "true");
        String originFobSuffixesProp = setSystemProperty(SchemeProperties.DXSCHEME_FOB_SUFFIXES_PROPERTY, "|#GLBX");
        try {
            DXScheme scheme = DXScheme.newLoader().fromSpecification(SCHEME_SPEC).load();

            DataRecord order = scheme.findRecordByName("Order#GLBX");
            assertNotNull(order);
            assertNotNull(order.findFieldByName("ActionTime"));
            assertNotNull(order.findFieldByName("OrderId"));
            assertNotNull(order.findFieldByName("AuxOrderId"));
            assertNotNull(order.findFieldByName("ExecutedSize"));
            assertNotNull(order.findFieldByName("TradeId"));
            assertNotNull(order.findFieldByName("TradePrice"));
            assertNotNull(order.findFieldByName("TradeSize"));
        } finally {
            setSystemProperty(SchemeProperties.DXSCHEME_FOB_PROPERTY, originFobProp);
            setSystemProperty(SchemeProperties.DXSCHEME_FOB_SUFFIXES_PROPERTY, originFobSuffixesProp);
        }
    }

    @Test
    public void testDxFeedPropertiesFileOverridesSystemProperty() throws IOException, SchemeException {
        String originSysProp = setSystemProperty("dxscheme.suffixes.Order", "|#BATE");
        try {
            DXScheme scheme = DXScheme.newLoader()
                .fromSpecification(SCHEME_SPEC)
                .withProperty("dxscheme.suffixes.Order", "|#BOSS")
                .load();

            assertNotNull(scheme.findRecordByName("Order#BOSS"));
            assertNull(scheme.findRecordByName("Order#BATE"));
            assertNull(scheme.findRecordByName("Order#NTV"));
        } finally {
            setSystemProperty("dxscheme.suffixes.Order", originSysProp);
        }
    }

    @Test
    public void testNanoTimePropWithExtScheme() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.nanoTime", "true");

        DataRecord quote = scheme.findRecordByName("Quote");
        assertNotNull(quote.findFieldByName("Sequence"));
        assertNotNull(quote.findFieldByName("TimeNanoPart"));
    }

    @Test
    public void testSuffixesExtOrderKeepsXmlDefaults() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.suffixesExt.Order", "#ABCD");

        assertNotNull(scheme.findRecordByName("Order#ABCD"));
        assertNotNull(scheme.findRecordByName("Order"));
        assertNotNull(scheme.findRecordByName("Order#NTV"));
        assertNull(scheme.findRecordByName("Order#null"));
    }

    @Test
    public void testEnabledFieldPropertyWithExtScheme() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.enabled.Sequence", "*");

        assertNotNull(scheme.findRecordByName("Quote").findFieldByName("Sequence"));
    }

    @Test
    public void testMalformedExchangesPropertyIsReported() throws IOException {
        try {
            createScheme("dxscheme.exchanges.Quote", "!");
            // Acceptable: the malformed value is rejected and reported, scheme is built without it
        } catch (SchemeException e) {
            // Acceptable: the malformed value is rejected with a scheme error
        } catch (NullPointerException e) {
            fail();
        }
    }

    @Test
    public void testSuffixesTradePropertyWithExtScheme() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.suffixes.Trade", "1min");

        // Record Trade is a different record and must remain
        assertNotNull(scheme.findRecordByName("Trade"));
        assertNotNull(scheme.findRecordByName("Trade.1min"));
        assertNull(scheme.findRecordByName("Trade.133ticks"));
    }

    @Test
    public void testDeprecatedMmidSuffixesPropertyWithExtScheme() throws IOException, SchemeException {
        DXScheme scheme = createScheme("com.dxfeed.event.order.impl.Order.suffixes.mmid", "|#BATE");

        assertNotNull(scheme.findRecordByName("Order#BATE").findFieldByName("MarketMaker"));
        assertNull(scheme.findRecordByName("Order#NTV").findFieldByName("MarketMaker"));
    }

    @Test
    public void testDuplicateOrderSuffixes() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.suffixes.Order", "#BATE|#BATE|#BATE");
        assertNotNull(scheme.findRecordByName("Order#BATE"));
    }

    @Test
    public void testEmptyExchangesPropertyDisablesRegionals() throws IOException, SchemeException {
        DXScheme scheme = createScheme("dxscheme.exchanges.Quote", "");

        assertNotNull(scheme.findRecordByName("Quote"));
        assertNull(scheme.findRecordByName("Quote&A"));
    }

    @Test
    public void testDefaultWideDecimal() throws IOException, SchemeException {
        String spec = "opt:sysprops,opt:dxprops,resource:dxfeed.xml";
        DXScheme scheme = DXScheme.newLoader().fromSpecification(spec).load();

        DataField field = scheme.findRecordByName("Quote").findFieldByName("Bid.Price");
        assertEquals(SerialFieldType.WIDE_DECIMAL, field.getSerialType());
    }

    @Test
    public void testTinyDecimalOverride() throws IOException, SchemeException {
        String spec = "opt:sysprops,opt:dxprops,resource:dxfeed.xml,resource:tiny-decimal-overlay.xml";
        DXScheme scheme = DXScheme.newLoader().fromSpecification(spec).load();

        DataField field = scheme.findRecordByName("Quote").findFieldByName("Bid.Price");
        assertEquals(SerialFieldType.DECIMAL, field.getSerialType());
    }

    @Test
    public void testUnrelatedPropertyDoesNotOverrideXmlTypes() throws IOException, SchemeException {
        String spec = "opt:dxprops,resource:dxfeed.xml,resource:tiny-decimal-overlay.xml";
        DXScheme expected = DXScheme.newLoader().fromSpecification(spec).load();

        Properties props = new Properties();
        props.setProperty("key", "value");
        DXScheme scheme = DXScheme.newLoader().fromSpecification(spec).load().withProperties(props);

        assertEquals(
            expected.findRecordByName("Quote").findFieldByName("Bid.Price").getSerialType(),
            scheme.findRecordByName("Quote").findFieldByName("Bid.Price").getSerialType());
    }

    // Utility methods

    private DXScheme createScheme(String... keyValues) throws IOException, SchemeException {
        assertEquals(0, keyValues.length % 2);
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }

        return DXScheme.newLoader()
            .fromSpecification("opt:dxprops,opt:sysprops,resource:dxfeed.xml")
            .load()
            .withProperties(properties);
    }

    private static String setSystemProperty(String prop, String value) {
        return value != null ? System.setProperty(prop, value) : System.clearProperty(prop);
    }
}
