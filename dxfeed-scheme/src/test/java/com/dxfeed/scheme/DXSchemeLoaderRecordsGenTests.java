/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.scheme.impl.DXSchemeFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DXSchemeLoaderRecordsGenTests {
    private static final DXSchemeFactory loader = new DXSchemeFactory();

    @Test
    public void testRecordsOkBasic() {
        DataScheme s = loader.createDataScheme("ext:resource:records/01-records-ok-basic.xml");
        assertNotNull(s);

        assertEquals(2, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(2, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);

        r = s.findRecordByName("two");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(2, r.getIntFieldCount());
        checkRecordField(r, "X", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "Y", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkDisabled() {
        DataScheme s = loader.createDataScheme("ext:resource:records/02-records-ok-disabled.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());
        assertNotNull(s.findRecordByName("one"));
        assertNull(s.findRecordByName("two"));
    }

    @Test
    public void testRecordsOkRegionals() {
        DataScheme s = loader.createDataScheme("ext:resource:records/03-records-ok-regionals.xml");
        assertNotNull(s);
        assertEquals(MarketEventSymbols.SUPPORTED_EXCHANGES.length() + 1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(2, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);

        for (char ex : MarketEventSymbols.SUPPORTED_EXCHANGES.toCharArray()) {
            r = s.findRecordByName("one&" + ex);
            assertNotNull(r);
            assertFalse(r.hasTime());
            assertEquals(2, r.getIntFieldCount());
            checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
            checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        }
    }

    @Test
    public void testRecordsOkIndex12() {
        DataScheme s = loader.createDataScheme("ext:resource:records/04-records-ok-index12.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        // Check index <C,B>
        assertEquals("C", r.getIntField(0).getLocalName());
        assertEquals("B", r.getIntField(1).getLocalName());
    }

    @Test
    public void testRecordsOkIndex1_() {
        DataScheme s = loader.createDataScheme("ext:resource:records/05-records-ok-index1_.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        assertEquals(4, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        // Check index <C,$VOID>
        assertEquals("C", r.getIntField(0).getLocalName());
        assertEquals(SerialFieldType.VOID, r.getIntField(1).getSerialType());
    }

    @Test
    public void testRecordsOkIndex_2() {
        DataScheme s = loader.createDataScheme("ext:resource:records/06-records-ok-index_2.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        assertEquals(4, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        // Check index <$VOID,B>
        assertEquals(SerialFieldType.VOID, r.getIntField(0).getSerialType());
        assertEquals("B", r.getIntField(1).getLocalName());
    }

    @Test
    public void testRecordsOkDisabledField() {
        DataScheme s = loader.createDataScheme("ext:resource:records/07-records-ok-fdisabled.xml");
        assertNotNull(s);

        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(2, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        assertNull(r.findFieldByName("B"));
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkFieldCompositeOnly() {
        DataScheme s = loader.createDataScheme("ext:resource:records/08-records-ok-fcompositeonly.xml");
        assertNotNull(s);
        assertEquals(MarketEventSymbols.SUPPORTED_EXCHANGES.length() + 1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        for (char ex : MarketEventSymbols.SUPPORTED_EXCHANGES.toCharArray()) {
            r = s.findRecordByName("one&" + ex);
            assertNotNull(r);
            assertFalse(r.hasTime());
            assertEquals(2, r.getIntFieldCount());
            checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
            assertNull(r.findFieldByName("B"));
            checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
        }
    }

    @Test
    public void testRecordsOkFieldOneAlias() {
        DataScheme s = loader.createDataScheme("ext:resource:records/09-records-ok-falias.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT, "NewB");
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkFieldMainAlias() {
        DataScheme s = loader.createDataScheme("ext:resource:records/10-records-ok-fmainalias.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT, "NewB");
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkFieldMultiAliases() {
        DataScheme s = loader.createDataScheme("ext:resource:records/11-records-ok-fmultialiases.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT, "NewB");
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkFieldMultiAliasesWithMain() {
        DataScheme s = loader.createDataScheme("ext:resource:records/12-records-ok-fmultialiasesmain.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT, "NewB");
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkFieldCustomType() {
        DataScheme s = loader.createDataScheme("ext:resource:records/13-records-ok-customtype.xml");
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertFalse(r.hasTime());
        assertEquals(3, r.getIntFieldCount());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkCopyFromSameFile() {
        DataScheme s = loader.createDataScheme("ext:resource:records/15-records-ok-copy-same.xml");
        assertNotNull(s);
        assertEquals(2, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        r = s.findRecordByName("two");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkCopyFromPrevFile() {
        DataScheme s = loader.createDataScheme("ext:" +
            "resource:records/16-records-ok-copy-prev-a.xml," +
            "resource:records/16-records-ok-copy-prev-b.xml"
        );
        assertNotNull(s);
        assertEquals(2, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);

        r = s.findRecordByName("two");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkCopyFromPrevUpdFile() {
        DataScheme s = loader.createDataScheme("ext:" +
            "resource:records/17-records-ok-copy-prev-up-a.xml," +
            "resource:records/17-records-ok-copy-prev-up-b.xml"
        );
        assertNotNull(s);
        assertEquals(2, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "D", SerialFieldType.COMPACT_INT);

        r = s.findRecordByName("two");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "D", SerialFieldType.COMPACT_INT);
    }

    @Test
    public void testRecordsOkCopyFromMidUpdFile() {
        DataScheme s = loader.createDataScheme("ext:" +
            "resource:records/18-records-ok-copy-mid-up-a.xml," +
            "resource:records/18-records-ok-copy-mid-up-b.xml," +
            "resource:records/18-records-ok-copy-mid-up-c.xml"
        );
        assertNotNull(s);
        assertEquals(2, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "D", SerialFieldType.COMPACT_INT);

        r = s.findRecordByName("two");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "D", SerialFieldType.COMPACT_INT);
    }

    /**
     * QD-1328 regression test: field update shall not require type definition
     */
    @Test
    public void testRecordUpdateWithoutType() {
        DataScheme s = loader.createDataScheme("ext:" +
            "resource:records/QD-1328-a.xml," +
            "resource:records/QD-1328-b.xml"
        );
        assertNotNull(s);
        assertEquals(1, s.getRecordCount());

        DataRecord r;
        r = s.findRecordByName("one");
        assertNotNull(r);
        assertTrue(r.hasTime());
        checkRecordField(r, "A", SerialFieldType.COMPACT_INT, "AA");
        checkRecordField(r, "B", SerialFieldType.COMPACT_INT);
        checkRecordField(r, "C", SerialFieldType.COMPACT_INT);
    }


    static void checkRecordField(DataRecord r, String fieldName, SerialFieldType type) {
        checkRecordField(r, fieldName, type, null);
    }

    static void checkRecordField(DataRecord r, String fieldName, SerialFieldType type, String propertyName) {
        DataField f = r.findFieldByName(fieldName);
        String localName = propertyName == null ? fieldName : propertyName;
        assertNotNull(f);
        assertEquals(localName, f.getLocalName());
        assertEquals(r.getName() + "." + localName, f.getName());
        assertEquals(fieldName, f.getPropertyName());
        assertEquals(type, f.getSerialType());
    }
}

