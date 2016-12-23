/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.RecordCursor;
import junit.framework.TestCase;

public class RecordCursorTest extends TestCase {

    private static final DataRecord RECORDI = new DefaultRecord(
        0, "i", false, new DataIntField[] { new CompactIntField(0, "i.i") }, null
    );

    private static final DataRecord RECORDO = new DefaultRecord(
        1, "o", false, null, new DataObjField[] { new StringField(0, "o.o") }
    );

    private static final PentaCodec CODEC = new PentaCodec();

    private static final DefaultScheme SCHEME = new DefaultScheme(CODEC,
        new DataRecord[] { RECORDI, RECORDO });

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
}
