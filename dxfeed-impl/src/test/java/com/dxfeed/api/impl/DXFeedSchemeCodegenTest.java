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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DXFeedSchemeCodegenTest {

    @Test
    public void testSchemeWide() {
        DataScheme scheme = createScheme(SchemeProperties.DXSCHEME_WIDE_PROPERTY, "true");

        DataField field = scheme.findRecordByName("Quote").findFieldByName("Bid.Price");
        assertEquals(SerialFieldType.WIDE_DECIMAL, field.getSerialType());
    }

    @Test
    public void testSchemeTiny() {
        DataScheme scheme = createScheme(SchemeProperties.DXSCHEME_WIDE_PROPERTY, "false");

        DataField field = scheme.findRecordByName("Quote").findFieldByName("Bid.Price");
        assertEquals(SerialFieldType.DECIMAL.withName("QUOTE_PRICE"), field.getSerialType());
    }

    @Test
    public void testSchemeVolume() {
        DataScheme scheme = createScheme("dxscheme.volume", "int");

        DataField field = scheme.findRecordByName("Trade").findFieldByName("Volume");
        assertEquals(SerialFieldType.COMPACT_INT, field.getSerialType());
    }

    @Test
    public void testSchemeNanoTime() {
        DataScheme scheme = createScheme("dxscheme.nanoTime", "true");

        DataRecord record = scheme.findRecordByName("Quote");
        assertNotNull(record.findFieldByName("Sequence"));
        assertNotNull(record.findFieldByName("TimeNanoPart"));
    }

    @Test
    public void testSchemeFobSuffixes() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_FOB_PROPERTY, "true",
            SchemeProperties.DXSCHEME_FOB_SUFFIXES_PROPERTY + "Ext", "|#BATE");

        DataRecord record = scheme.findRecordByName("Order#BATE");
        assertNotNull(record);
        assertNotNull(record.findFieldByName("OrderId"));

        DataRecord record1 = scheme.findRecordByName("Order#NTV");
        assertNotNull(record1);
        assertNotNull(record1.findFieldByName("OrderId"));

        DataRecord record2 = scheme.findRecordByName("Order#ntv");
        assertNotNull(record2);
        assertNull(record2.findFieldByName("ActionTime"));
    }

    @Test
    public void testSchemeOrderSuffixes() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + ".Order", "|#BATE");

        assertNotNull(scheme.findRecordByName("Order#BATE"));
        assertNull(scheme.findRecordByName("Order#NTV"));
    }

    @Test
    public void testSchemeOldNewPriority() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + ".Order", "#BATE",
            "com.dxfeed.event.market.impl.Order.suffixes", "#BOSS");

        assertNotNull(scheme.findRecordByName("Order#BATE"));
        assertNull(scheme.findRecordByName("Order#BOSS"));
    }

    @Test
    public void testSchemeOrderSuffixesExtKeepsDefaults() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + "Ext.Order", "#BATE");

        assertNotNull(scheme.findRecordByName("Order#BATE"));
        assertNotNull(scheme.findRecordByName("Order#NTV"));
    }

    @Test
    public void testSchemeOrderSuffixesExtKeepsDuplicateDefaults() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + "Ext.Order", "|#BATE");

        assertNotNull(scheme.findRecordByName("Order"));
        assertNotNull(scheme.findRecordByName("Order#BATE"));
        assertNotNull(scheme.findRecordByName("Order#NTV"));
    }

    @Test
    public void testSchemeOrderFieldSuffixes() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + ".Order.count", "|#BATE");

        DataRecord record = scheme.findRecordByName("Order#BATE");
        assertNotNull(record);
        assertNotNull(record.findFieldByName("Count"));

        DataRecord record2 = scheme.findRecordByName("Order#NTV");
        assertNotNull(record2);
        assertNull(record2.findFieldByName("Count"));
    }

    @Test
    public void testSchemeOrderFieldSuffixesExt() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + "Ext.Order.count", "|#BATE");

        DataRecord record = scheme.findRecordByName("Order#BATE");
        assertNotNull(record);
        assertNotNull(record.findFieldByName("Count"));
    }

    @Test
    public void testSchemeExchanges() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_EXCHANGES_PROPERTY + ".Quote", "A-F");

        for (char exchange = 'A'; exchange <= 'F'; exchange++) {
            assertNotNull(scheme.findRecordByName("Quote&" + exchange));
        }
        for (char exchange = 'G'; exchange <= 'Z'; exchange++) {
            assertNull(scheme.findRecordByName("Quote&" + exchange));
        }
    }

    @Test
    public void testSchemeEmptyExchanges() {
        DataScheme scheme = createScheme(
            SchemeProperties.DXSCHEME_EXCHANGES_PROPERTY + ".Quote", "");

        assertNotNull(scheme.findRecordByName("Quote"));
        for (char exchange = 'A'; exchange <= 'Z'; exchange++) {
            assertNull(scheme.findRecordByName("Quote&" + exchange));
        }
    }

    // Utility methods

    private DataScheme createScheme(String... keyValues) {
        assertEquals(0, keyValues.length % 2);
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }

        SchemeProperties schemeProperties = new SchemeProperties(properties);
        //FIXME Using package-protected method to obtain scheme with properties
        return DXFeedScheme.withProperties(schemeProperties);
    }
}
