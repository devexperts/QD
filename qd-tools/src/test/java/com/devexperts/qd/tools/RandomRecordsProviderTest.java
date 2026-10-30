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
package com.devexperts.qd.tools;

import com.devexperts.io.IOUtil;
import com.devexperts.io.Marshalled;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RandomRecordsProviderTest {

    private static final DataRecord RECORD = new DefaultRecord(0, "Attached", false,
        new DataIntField[] {new CompactIntField(0, "Attached.Value")},
        new DataObjField[] {
            new MarshalledObjField(0, "Attached.Payload"),
            new StringField(1, "Attached.Text"),
        });

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);

    private static RecordBuffer generate(int count) {
        SymbolList symbols = new SymbolList(new String[] {"AAPL", "IBM"}, SCHEME.getCodec());
        RandomRecordsProvider provider =
            new RandomRecordsProvider(new DataRecord[] {RECORD}, symbols, count, count);
        RecordBuffer buffer = new RecordBuffer();
        provider.retrieve(buffer);
        return buffer;
    }

    @Test
    public void testSerialObjectFieldCarriesSerializableAttachment() throws IOException {
        RecordBuffer buffer = generate(16);
        int records = 0;
        for (RecordCursor cursor; (cursor = buffer.next()) != null;) {
            records++;
            Object payload = cursor.getObj(0);
            assertNotNull("serial object field is generated", payload);
            byte[] bytes = IOUtil.objectToBytes(payload);
            assertTrue("attachment is not empty", bytes.length > 0);
            assertEquals(payload.toString(), Marshalled.forBytes(bytes).getObject().toString());
        }
        assertEquals(16, records);
    }

    @Test
    public void testAttachmentsDifferBetweenRecords() {
        RecordBuffer buffer = generate(64);
        Object first = null;
        boolean differs = false;
        for (RecordCursor cursor; (cursor = buffer.next()) != null;) {
            Object payload = cursor.getObj(0);
            if (first == null) {
                first = payload;
            } else if (payload != first) {
                differs = true;
            }
        }
        assertTrue("attachments vary between records", differs);
    }

    @Test
    public void testStringFieldKeepsItsOwnGenerator() {
        RecordBuffer buffer = generate(4);
        for (RecordCursor cursor; (cursor = buffer.next()) != null;) {
            assertTrue("string field carries a string", cursor.getObj(1) instanceof String);
        }
    }

    @Test
    public void testSchemeConstantsMatchTheRecordUnderTest() {
        assertEquals(SerialFieldType.SERIAL_OBJECT, RECORD.getObjField(0).getSerialType());
    }
}
