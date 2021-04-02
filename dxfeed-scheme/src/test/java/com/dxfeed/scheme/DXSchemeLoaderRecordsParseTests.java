/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme;

import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecord;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DXSchemeLoaderRecordsParseTests {
    @Test
    public void testRecordsOkBasic() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/01-records-ok-basic.xml").load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, "Record one", null, false, null, null, null, 2);
        checkSchemeField(r, "A", false, "Field A", "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");

        r = file.getRecords().get("two");
        checkRecord(r, "two", false, null, null, false, null, null, null, 2);
        checkSchemeField(r, "X", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "X");
        checkRecordFieldTags(r, "X");
        checkSchemeField(r, "Y", false, "Field Y", "compact_int", false, null);
        checkRecordFieldAliases(r, "Y");
        checkRecordFieldTags(r, "Y");
    }

    @Test
    public void testRecordsOkDisabled() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/02-records-ok-disabled.xml").load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 2);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");

        r = file.getRecords().get("two");
        checkRecord(r, "two", true, null, null, false, null, null, null, 2);
        checkSchemeField(r, "X", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "X");
        checkRecordFieldTags(r, "X");
        checkSchemeField(r, "Y", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "Y");
        checkRecordFieldTags(r, "Y");
    }

    @Test
    public void testRecordsOkRegionals() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/03-records-ok-regionals.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, true, null, null, null, 2);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
    }

    @Test
    public void testRecordsOkIndex12() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/04-records-ok-index12.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "C", "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkIndex1_() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/05-records-ok-index1_.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "C", null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkIndex_2() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/06-records-ok-index_2.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkDisabledField() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/07-records-ok-fdisabled.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", true, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldCompositeOnly() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/08-records-ok-fcompositeonly.xml").load();
        assertNotNull(file);
        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, true, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", true, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldOneAlias() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/09-records-ok-falias.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B", "NewB");
        checkRecordFieldTags(file.getRecords().get("one"), "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldMainAlias() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/10-records-ok-fmainalias.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B", "NewB");
        checkRecordFieldTags(file.getRecords().get("one"), "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldMultiAliases() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/11-records-ok-fmultialiases.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B", "NewB", "Other");
        checkRecordFieldTags(file.getRecords().get("one"), "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldMultiAliasesWithMain() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/12-records-ok-fmultialiasesmain.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B", "NewB", "Other");
        checkRecordFieldTags(file.getRecords().get("one"), "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldCustomType() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/13-records-ok-customtype.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "custom", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B");
        checkRecordFieldTags(file.getRecords().get("one"), "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkFieldTags() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/14-records-ok-ftags.xml").load();
        assertNotNull(file);

        assertEquals(1, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, null, null, null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(file.getRecords().get("one"), "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(file.getRecords().get("one"), "B");
        checkRecordFieldTags(file.getRecords().get("one"), "B", "t1", "t2");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkCopyFromSameFile() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification("resource:records/15-records-ok-copy-same.xml").load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "A", "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");

        r = file.getRecords().get("two");
        checkRecord(r, "two", false, null, "one", false, "A", "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkCopyFromPrevFile() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification(
                "resource:records/16-records-ok-copy-prev-a.xml," +
                "resource:records/16-records-ok-copy-prev-b.xml"
            )
            .load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "A", "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");

        r = file.getRecords().get("two");
        checkRecord(r, "two", false, null, "one", false, "A", "B", null, 3);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
    }

    @Test
    public void testRecordsOkCopyFromPrevUpdFile() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification(
                "resource:records/17-records-ok-copy-prev-up-a.xml," +
                "resource:records/17-records-ok-copy-prev-up-b.xml"
            )
            .load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "A", "B", null, 4);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
        checkSchemeField(r, "D", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "D");
        checkRecordFieldTags(r, "D");

        r = file.getRecords().get("two");
        checkRecord(r, "two", false, null, "one", false, "A", "B", null, 4);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
        checkSchemeField(r, "D", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "D");
        checkRecordFieldTags(r, "D");
    }

    @Test
    public void testRecordsOkCopyFromMidUpdFile() throws IOException, SchemeException {
        SchemeModel file = SchemeModel.newLoader()
            .fromSpecification(
                "resource:records/18-records-ok-copy-mid-up-a.xml," +
                "resource:records/18-records-ok-copy-mid-up-b.xml," +
                "resource:records/18-records-ok-copy-mid-up-c.xml"
            )
            .load();
        assertNotNull(file);
        assertEquals(2, file.getRecords().size());

        SchemeRecord r;
        r = file.getRecords().get("one");
        checkRecord(r, "one", false, null, null, false, "A", "B", null, 4);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
        checkSchemeField(r, "D", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "D");
        checkRecordFieldTags(r, "D");

        r = file.getRecords().get("two");
        checkRecord(r, "two", false, null, "one", false, "A", "B", null, 4);
        checkSchemeField(r, "A", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "A");
        checkRecordFieldTags(r, "A");
        checkSchemeField(r, "B", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "B");
        checkRecordFieldTags(r, "B");
        checkSchemeField(r, "C", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "C");
        checkRecordFieldTags(r, "C");
        checkSchemeField(r, "D", false, null, "compact_int", false, null);
        checkRecordFieldAliases(r, "D");
        checkRecordFieldTags(r, "D");
    }

    static void checkRecord(SchemeRecord rec, String name, boolean dis, String doc, String base, boolean regs,
        String i1, String i2, String eventName, int fields)
    {
        checkRecordBase(rec, name, dis, doc, base, regs, i1, i2, eventName, fields);
        assertFalse(rec.isTemplate());
        assertNull(rec.getParentGenerator());
    }

    static void checkRecordBase(SchemeRecord rec, String name, boolean dis, String doc, String base, boolean regs,
        String i1, String i2, String eventName, int fields)
    {
        assertNotNull(rec);
        assertEquals(name, rec.getName());
        assertEquals(doc, rec.getDoc());
        assertEquals(regs, rec.hasRegionals());
        assertEquals(dis, rec.isDisabled());
        if (base != null) {
            assertTrue(rec.hasBase());
        } else {
            assertFalse(rec.hasBase());
        }
        assertEquals(base, rec.getBase());
        if (i1 != null || i2 != null) {
            assertTrue(rec.hasEventFlags());
        } else {
            assertFalse(rec.hasEventFlags());
        }
        assertEquals(i1, rec.getIndex1());
        assertEquals(i2, rec.getIndex2());
        if (eventName != null) {
            assertEquals(eventName, rec.getEventName());
        } else {
            assertEquals(name, rec.getEventName());
        }
        assertEquals(fields, rec.getFields().size());
    }

    static void checkSchemeField(SchemeRecord rec, String name, boolean dis, String doc, String type, boolean composite,
        String eventName)
    {
        SchemeRecord.Field f = rec.getField(name);
        assertNotNull(f);
        assertEquals(name, f.getName());
        assertEquals(dis, f.isDisabled());
        assertEquals(doc, f.getDoc());
        assertEquals(type, f.getType());
        assertEquals(composite, f.isCompositeOnly());
        if (eventName == null) {
            assertEquals(rec.getEventName(), f.getEventName());
        } else {
            assertEquals(eventName, f.getEventName());
        }
    }

    static void checkRecordFieldAliases(SchemeRecord rec, String name, String... aliases) {
        SchemeRecord.Field f = rec.getField(name);
        assertNotNull(f);
        if (aliases.length > 0) {
            assertEquals(aliases[0], f.getMainAlias());
        } else {
            assertEquals(name, f.getMainAlias());
        }
        assertEquals(new HashSet<>(Arrays.asList(aliases)),
            f.getAliases().stream().map(SchemeRecord.Field.Alias::getValue).collect(Collectors.toSet()));
    }

    static void checkRecordFieldTags(SchemeRecord rec, String name, String... tags) {
        SchemeRecord.Field f = rec.getField(name);
        assertNotNull(f);
        assertEquals(new HashSet<>(Arrays.asList(tags)), f.getTags());
    }
}

