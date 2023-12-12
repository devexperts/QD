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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.TimeFormat;

import java.io.IOException;

class StampComposer extends TextQTPComposer {

    private static final Logging log = Logging.getLogging(StampComposer.class);

    private final RecordFields[] rfs;
    private long timeMillis;

    StampComposer(DataScheme scheme, RecordFields[] rfs) {
        super(scheme);
        this.rfs = rfs;
    }

    @Override
    public void append(RecordCursor cursor) {
        // This is an unrolled implementation of
        // AbstractByteArrayComposer.append and TextByteArrayComposer.composeRecordHeader so that
        // we can get hold onto the EventTime from the cursor.getEventTime or from HeartbeatPayload.getTimeMillis
        // and prepend them before record description and before record data
        try {
            long timeSequence = getEventTimeSequence(cursor);
            timeMillis = timeSequence == 0 ?
                System.currentTimeMillis() :
                TimeSequenceUtil.getTimeMillisFromTimeSequence(timeSequence);
            DataRecord record = cursor.getRecord();
            beginRecord(record);
            writeln();
            write(TimeFormat.DEFAULT.withMillis().format(timeMillis));
            separator();
            write(record.getName());
            separator();
            write(record.getScheme().getCodec().decode(cursor.getCipher(), cursor.getSymbol()));
            writeRecordPayload(cursor, cursor.getEventFlags());
        } catch (IOException e) {
            log.error("Unexpected IO exception", e);
            throw new AssertionError(e.toString());
        }
    }

    @Override
    protected void describeRecord(DataRecord record) {
        writeln();
        write(TimeFormat.DEFAULT.withMillis().format(timeMillis));
        separator();
        writeDescribeRecordLine(record);
    }

    @Override
    protected boolean acceptField(DataField f) {
        return super.acceptField(f) && (rfs == null || rfs[f.getRecord().getId()].contains(f));
    }
}
