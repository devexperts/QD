/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.ArrayListAttachmentStrategy;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.ng.RecordCursor;
import junit.framework.TestCase;

import java.util.HashSet;
import java.util.Set;

public class ArrayListAttachmentStrategyTest extends TestCase {
    private static final DataRecord RECORD = new DefaultRecord(0, "Test", false, new DataIntField[0], new DataObjField[0]);

    Set<String> set = new HashSet<>();
    ArrayListAttachmentStrategy<String, ArrayListAttachmentStrategyTest> alas =
        new ArrayListAttachmentStrategy<String, ArrayListAttachmentStrategyTest>() {
            @Override
            public void process(RecordCursor cursor, String attachment, ArrayListAttachmentStrategyTest ctx) {
                if (attachment != null)
                    set.add(attachment);
                else
                    assertTrue(set.isEmpty());
            }
        };

    public void testUpdateAttachment() throws Exception {
        RecordCursor.Owner subOwner = RecordCursor.allocateOwner();
        RecordCursor.Owner dataOwner = RecordCursor.allocateOwner();
        subOwner.setRecord(RECORD);
        dataOwner.setRecord(RECORD);

        subOwner.setAttachment("ONE");
        dataOwner.setAttachment(alas.updateAttachment(null, subOwner.cursor(), false));
        assertSet(dataOwner.cursor(), "ONE");

        subOwner.setAttachment("TWO");
        dataOwner.setAttachment(alas.updateAttachment(dataOwner.cursor().getAttachment(), subOwner.cursor(), false));
        assertSet(dataOwner.cursor(), "ONE", "TWO");

        subOwner.setAttachment("THREE");
        dataOwner.setAttachment(alas.updateAttachment(dataOwner.cursor().getAttachment(), subOwner.cursor(), false));
        assertSet(dataOwner.cursor(), "ONE", "TWO", "THREE");

        subOwner.setAttachment("ONE");
        dataOwner.setAttachment(alas.updateAttachment(dataOwner.cursor().getAttachment(), subOwner.cursor(), true));
        assertSet(dataOwner.cursor(), "TWO", "THREE");

        subOwner.setAttachment("THREE");
        dataOwner.setAttachment(alas.updateAttachment(dataOwner.cursor().getAttachment(), subOwner.cursor(), true));
        assertSet(dataOwner.cursor(), "TWO");

        subOwner.setAttachment("TWO");
        dataOwner.setAttachment(alas.updateAttachment(dataOwner.cursor().getAttachment(), subOwner.cursor(), true));
        assertSet(dataOwner.cursor());
        assertEquals(null, dataOwner.cursor().getAttachment());
    }

    private void assertSet(RecordCursor data, String... ss) {
        set.clear();
        alas.processEach(data, this);
        assertEquals(ss.length, set.size());
        for (String s : ss) {
            assertTrue(set.contains(s));
        }
    }
}
