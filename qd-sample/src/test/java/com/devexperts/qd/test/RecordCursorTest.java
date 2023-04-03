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
package com.devexperts.qd.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordCursorTest {

    private static final DataRecord RECORDI = new DefaultRecord(
        0, "i", false, new DataIntField[] { new CompactIntField(0, "i.i") }, null
    );

    private static final DataRecord RECORDO = new DefaultRecord(
        1, "o", false, null, new DataObjField[] { new StringField(0, "o.o") }
    );

    private static final PentaCodec CODEC = new PentaCodec();

    private static final DefaultScheme SCHEME = new DefaultScheme(CODEC,
        new DataRecord[] { RECORDI, RECORDO });

    @Test
    public void testCopyFrom() {
        RecordCursor cur1 = RecordCursor.allocate(RECORDI, "X");
        cur1.setInt(0, 1234);
        assertEquals(cur1.getRecord(), RECORDI);
        assertEquals(cur1.getCipher(), CODEC.encode("X"));
        assertEquals(cur1.getSymbol(), "X");
        assertEquals(cur1.getInt(0), 1234);

        RecordCursor cur2 = RecordCursor.allocate(RECORDI, "Y");
        cur2.copyFrom(cur1);
        assertEquals(cur2.getRecord(), RECORDI);
        assertEquals(cur2.getCipher(), CODEC.encode("Y"));
        assertEquals(cur2.getSymbol(), "Y");
        assertEquals(cur2.getInt(0), 1234);

        RecordCursor cur3 = RecordCursor.allocate(RECORDO, "A");
        cur3.setObj(0, "1234");
        assertEquals(cur3.getRecord(), RECORDO);
        assertEquals(cur3.getCipher(), CODEC.encode("A"));
        assertEquals(cur3.getSymbol(), "A");
        assertEquals(cur3.getObj(0), "1234");

        RecordCursor cur4 = RecordCursor.allocate(RECORDO, "B");
        cur4.copyFrom(cur3);
        assertEquals(cur4.getRecord(), RECORDO);
        assertEquals(cur4.getCipher(), CODEC.encode("B"));
        assertEquals(cur4.getSymbol(), "B");
        assertEquals(cur4.getObj(0), "1234");
    }

    @Test
    public void testToStringPrintsEventFlags() {
        RecordCursor cur1 = RecordCursor.allocate(RECORDI, "X", RecordMode.FLAGGED_DATA);
        cur1.setInt(0, 1234);
        for (EventFlag flag : EventFlag.values()) {
            cur1.setEventFlags(flag.flag());
            assertTrue(cur1.toString().endsWith("eventFlags=0x" + Integer.toHexString(flag.flag())));
        }
    }
}
