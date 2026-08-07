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
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.dxfeed.api.DXEndpoint;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Asserts the scheme tuning properties contract: every scheme tuning property can be supplied
 * via {@link DXEndpoint.Builder} (and thus via properties file), deprecated aliases keep working,
 * and an endpoint never builds a scheme that diverges from the JVM-configured default scheme.
 */
public class DxFeedSchemeTuningPropertiesTest {

    private static final String[] SCHEME_TUNING_PROPERTIES = {
        SchemeProperties.DXSCHEME_WIDE_PROPERTY,
        SchemeProperties.DXSCHEME_FOB_PROPERTY,
        SchemeProperties.DXSCHEME_FOB_SUFFIXES_PROPERTY,
        SchemeProperties.DXSCHEME_FOB_SUFFIXES_PROPERTY + "Ext",
        SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + ".Order",
        "dxscheme.suffixesExt.Order",
        SchemeProperties.DXSCHEME_EXCHANGES_PROPERTY + ".Quote",
    };

    @Test
    public void testBuilderSupportsSchemeTuningProperties() {
        DXEndpoint.Builder builder = DXEndpoint.newBuilder();
        for (String key : SCHEME_TUNING_PROPERTIES) {
            assertTrue(key, builder.supportsProperty(key));
        }
    }

    @Test
    public void testSchemeTuningPropertyViaEndpointBuilder() {
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.LOCAL_HUB)
            .withProperty(SchemeProperties.DXSCHEME_WIDE_PROPERTY, "false")
            .build();
        try {
            DataScheme scheme = ((DXEndpointImpl) endpoint).getQDEndpoint().getScheme();
            DataField field = scheme.findRecordByName("Quote").findFieldByName("Bid.Price");
            assertEquals(SerialFieldType.DECIMAL.withName("QUOTE_PRICE"), field.getSerialType());
        } finally {
            endpoint.close();
        }
    }

    @Test
    public void testSuffixesExtAloneExtendsAbsentBase() {
        Properties props = new Properties();
        props.setProperty("dxscheme.suffixesExt.Order", "#ABCD");
        SchemeProperties schemeProperties = new SchemeProperties(props);

        assertEquals("#ABCD", schemeProperties.getSuffixes(
            "dxscheme.suffixes.Order", "com.dxfeed.event.market.impl.Order.suffixes", null));
    }

    @Test
    public void testNewPropertyWinsOverOld() {
        Properties p = new Properties();
        p.setProperty("dxscheme.suffixes.Order", "|#NEW");
        p.setProperty("com.dxfeed.event.market.impl.Order.suffixes", "|#OLD");

        SchemeProperties sp = new SchemeProperties(p);
        assertEquals("|#NEW", sp.getSuffixes(
            "dxscheme.suffixes.Order", "com.dxfeed.event.market.impl.Order.suffixes", "|#DEF"));
    }

    @Test
    public void testBareNanoTimeFlagEnablesFields() {
        Properties props = new Properties();
        props.setProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "");
        SchemeProperties schemeProperties = new SchemeProperties(props);

        // Empty value means "true" for boolean properties, according to SystemProperties.parseBooleanValue
        assertEquals(Boolean.TRUE, schemeProperties.isEventPropertyEnabled("Sequence", "Quote"));
    }
}
