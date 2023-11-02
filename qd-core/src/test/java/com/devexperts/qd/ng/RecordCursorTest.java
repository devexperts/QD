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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.kit.WideDecimalField;
import com.devexperts.util.WideDecimal;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordCursorTest {

    @Test
    public void testWideDecimalFieldToString() {
        DefaultRecord record = new DefaultRecord(0, "Test", false,
            new DataIntField[]{new WideDecimalField(0, "Test.Wd0"), new VoidIntField(1, "Test.Wd0Tail")},
            new DataObjField[]{new MarshalledObjField(0, "Test.Obj0")});
        //init scheme for correct work with RecordCursor
        new DefaultScheme(PentaCodec.INSTANCE, record);

        RecordBuffer buf = new RecordBuffer(RecordMode.DATA);
        RecordCursor cur = buf.add(record, 0, "TEST");
        double doublePrice = 12345.67890;
        long wdValue = WideDecimal.composeWide(doublePrice);
        cur.setLong(0, wdValue);
        String objectValue = "TestObj";
        cur.setObj(0, objectValue);
        String cursorStr = cur.toString();
        //string representation of cursor shall contain string representation of its fields.
        assertTrue(cursorStr.contains(Double.toString(doublePrice)));
        assertTrue(cursorStr.contains(objectValue));
        //wide decimal field shall be converted to double in string representation. QD-1113
        //see RecordCursor#getLongImpl, tail part of wide decimal field shall not be added to string presentation
        assertFalse(cursorStr.contains(Integer.toString((int) wdValue)));
    }
}
