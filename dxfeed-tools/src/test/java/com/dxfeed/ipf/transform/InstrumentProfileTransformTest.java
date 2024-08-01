/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.transform;

import com.dxfeed.ipf.InstrumentProfile;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class InstrumentProfileTransformTest {
    private final InstrumentProfileTransform containsTransform = InstrumentProfileTransform.compile(
        new StringReader("EXCHANGES = (EXCHANGES hasItem OPOL) ? 'true' : 'false';")
    );

    private final InstrumentProfileTransform addTransform = InstrumentProfileTransform.compile(
        new StringReader("EXCHANGES = addItem(EXCHANGES, OPOL);")
    );

    private final InstrumentProfileTransform removeTransform = InstrumentProfileTransform.compile(
        new StringReader("EXCHANGES = removeItem(EXCHANGES, OPOL);")
    );

    public InstrumentProfileTransformTest() throws IOException, TransformCompilationException {
    }

    @Test
    public void testEmptyItemContainsTransform() {
        check("ABC;DEF", containsTransform, "", "false");
        check("", containsTransform, "", "false");
    }

    @Test
    public void testLongerItemContainsTransform() {
        check("ABC;DEF", containsTransform, "ABCDEFGHI", "false");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testItemWithSemicolonNotAllowedByContainsItem() {
        check("ABC;DEF", containsTransform, "AB;DE", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLongerItemWithSemicolonNotAllowedByContainsItem() {
        check("ABC;DEF", containsTransform, "ABC;DEFGH", "");
    }

    @Test
    public void testContainsItem() {
        check("TMP;XYZ;YT", containsTransform, "TMP", "true");
        check("ABC;TMP;YT", containsTransform, "TMP", "true");
        check("ABC;BTMP;TMP", containsTransform, "TMP", "true");

        check("ABC;TMPB;TMPY", containsTransform, "TMP", "false");
        check("TM;XYZ", containsTransform, "TMP", "false");
        check("TM;TMPA;TMPB;YTMP", containsTransform, "TMP", "false");
    }

    @Test
    public void testEmptyItemAddTransform() {
        check("ABC;DEF", addTransform, "", "ABC;DEF");
        check("", addTransform, "", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testItemWithSemicolonNotAllowedByAddItem() {
        check("ABC;DEF", addTransform, "AB;DE", "");
    }

    @Test
    public void testAddItem() {
        check("TMP;XYZ;YT", addTransform, "TMP", "TMP;XYZ;YT");
        check("XYZ;YT", addTransform, "TMP", "TMP;XYZ;YT");
        check("ABC;DE", addTransform, "TMP", "ABC;DE;TMP");
        check("ABC;XYZ", addTransform, "TMP", "ABC;TMP;XYZ");
    }

    @Test
    public void testEmptyItemRemoveTransform() {
        check("ABC;DEF", removeTransform, "", "ABC;DEF");
        check("", removeTransform, "", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testItemWithSemicolonNotAllowedByRemoveItem() {
        check("ABC;DEF", removeTransform, "AB;DE", "");
    }

    @Test
    public void testRemoveItem() {
        check("XYZ;YT", removeTransform, "TMP", "XYZ;YT");
        check("TMP;XYZ;YT", removeTransform, "TMP", "XYZ;YT");
        check("ABC;DE;TMP", removeTransform, "TMP", "ABC;DE");
        check("ABC;TMP;XYZ", removeTransform, "TMP", "ABC;XYZ");
        check("AAA;A", removeTransform, "A", "AAA");
        check("AAA;A;B", removeTransform, "A", "AAA;B");
    }

    @Test
    public void testRetainFields() throws Exception {
        InstrumentProfile profile = new InstrumentProfile();
        profile.setSymbol("TEST");
        profile.setType("STOCK");
        profile.setDescription("Description");
        profile.setExchangeData("ExchangeData");
        profile.setOPOL("OPOL");
        profile.setField("FOO", "1");
        profile.setField("BAR", "2");
        profile.setField("BAZ", "3");

        InstrumentProfileTransform transform = InstrumentProfileTransform.compile(
            new StringReader("retainFields(SYMBOL,TYPE,OPOL,BAR);")
        );

        List<InstrumentProfile> transformed = transform.transform(Collections.singletonList(profile));
        assertEquals(1, transformed.size());
        InstrumentProfile result = transformed.get(0);

        assertEquals("TEST", result.getSymbol());
        assertEquals("STOCK", result.getType());
        assertEquals("", result.getDescription());
        assertEquals("", result.getExchangeData());
        assertEquals("OPOL", result.getOPOL());
        assertEquals("", result.getField("FOO"));
        assertEquals("2", result.getField("BAR"));
        assertEquals("", result.getField("BAZ"));
    }

    private void check(String old, InstrumentProfileTransform transform, String item, String expected) {
        InstrumentProfile profile = new InstrumentProfile();
        profile.setSymbol("TEST_SYMBOL");
        profile.setExchanges(old);
        profile.setOPOL(item);
        List<InstrumentProfile> transformed = transform.transform(Collections.singletonList(profile));
        assertEquals(1, transformed.size());
        assertEquals(expected, transformed.get(0).getExchanges());
    }
}
