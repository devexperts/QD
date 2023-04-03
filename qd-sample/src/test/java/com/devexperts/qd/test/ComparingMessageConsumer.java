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

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageType;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

class ComparingMessageConsumer extends MessageConsumerAdapter {
    private final Map<MessageType, RecordBuffer> bufs = new EnumMap<MessageType, RecordBuffer>(MessageType.class);
    private final Map<MessageType, ? extends DataProvider> providers;
    private int recordCounter;

    ComparingMessageConsumer(Map<MessageType, ? extends DataProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void handleCorruptedStream() {
        fail();
    }

    @Override
    public void handleCorruptedMessage(int messageTypeId) {
        fail();
    }

    @Override
    public void handleUnknownMessage(int messageTypeId) {
        fail();
    }

    public int getRecordCounter() {
        return recordCounter;
    }

    @Override
    protected void processData(DataIterator iterator, MessageType messageType) {
        DataRecord record;
        while ((record = iterator.nextRecord()) != null) {
            recordCounter++;
            // get buf
            RecordBuffer buf = bufs.get(messageType);
            if (buf == null)
                bufs.put(messageType, buf = new RecordBuffer());
            // Make sure we have something in buffer
            if (!buf.hasNext())
                buf.compact();
            while (buf.isEmpty())
                providers.get(messageType).retrieveData(buf);
            // Compare
            DataRecord expectedRecord = buf.nextRecord();
            assertSame("record", expectedRecord, record);
            assertEquals("cipher", buf.getCipher(), iterator.getCipher());
            String s1 = iterator.getSymbol();
            String s2 = buf.getSymbol();
            if (s1 != null && s2 != null)
                assertEquals("symbol", s1, s2);
            for (int i = 0, n = record.getIntFieldCount(); i < n; i++)
                assertEquals("intField", buf.nextIntField(), iterator.nextIntField());
            for (int i = 0, n = record.getObjFieldCount(); i < n; i++)
                assertEquals("objField", buf.nextObjField(), iterator.nextObjField());
        }
    }
}
