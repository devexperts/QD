/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.util.Decimal;
import junit.framework.TestCase;

public class FieldAdaptationTest extends TestCase {
    static final String SYMBOL = "TEST";
    static final int VALUE = 123000;

    static final DefaultRecord srcRecord = new DefaultRecord(0, "rec", false, new DataIntField[] {new CompactIntField(0, "rec.i0")}, null);
    static final DataScheme srcScheme = new TestDataScheme(1, 0, TestDataScheme.Type.SIMPLE, srcRecord);

    static final DefaultRecord dstRecord = new DefaultRecord(0, "rec", false, new DataIntField[] {new DecimalField(0, "rec.i0")}, null);
    static final DataScheme dstScheme = new TestDataScheme(1, 0, TestDataScheme.Type.SIMPLE, dstRecord);

    public void testFieldAdaptation() {
        RecordBuffer srcBuffer = new RecordBuffer();
        RecordCursor srcCursor = srcBuffer.add(srcRecord, srcScheme.getCodec().encode(SYMBOL), SYMBOL);
        srcCursor.setInt(0, VALUE);
        ChunkedOutput output = new ChunkedOutput();
        BinaryQTPComposer composer = new BinaryQTPComposer(srcScheme, true);
        composer.setOutput(output);
        assertFalse(composer.visitData(srcBuffer, MessageType.STREAM_DATA));

        // copy composed chunks to chunked input
        ChunkedInput input = new ChunkedInput();
        input.addAllToInput(output.getOutput(this), this);

        // parser from this input
        BinaryQTPParser parser = new BinaryQTPParser(dstScheme);
        parser.setInput(input);

        parser.parse(new MessageConsumerAdapter() {
            @Override
            public void handleCorruptedStream() {
                fail("handleCorruptedStream");
            }

            @Override
            public void handleCorruptedMessage(int messageTypeId) {
                fail("handleCorruptedMessage " + messageTypeId);
            }

            @Override
            public void handleUnknownMessage(int messageTypeId) {
                fail("handleUnknownMessage " + messageTypeId);
            }

            @Override
            public void processStreamData(DataIterator iterator) {
                RecordBuffer dstBuffer = (RecordBuffer) iterator;
                RecordCursor dstCursor = dstBuffer.next();
                assertNotNull("no data", dstCursor);
                assertEquals("wrong record", dstRecord, dstCursor.getRecord());
                assertEquals("wrong symbol", SYMBOL, dstScheme.getCodec().decode(dstCursor.getCipher(), dstCursor.getSymbol()));
                assertEquals("wrong value", VALUE, Decimal.toDouble(dstCursor.getInt(0)), 0);
                assertNull("extra data", dstBuffer.next());
            }
        });
    }
}
