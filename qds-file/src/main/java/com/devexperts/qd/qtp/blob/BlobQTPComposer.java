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
package com.devexperts.qd.qtp.blob;

import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.MessageType;

import java.io.IOException;

/**
 * Composes QTP messages in blob format into byte stream.
 * The output for this composer must be configured with {@link #setOutput(BufferedOutput)} method
 * immediately after construction.
 *
 * @see AbstractQTPComposer
 */
public class BlobQTPComposer extends AbstractQTPComposer {

    // ======================== private instance fields ========================

    private final DataRecord record;
    private final int cipher;
    private final String symbol;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs blob composer with a specified record and symbol.
     * You must {@link #setOutput(BufferedOutput) setOutput} before using this composer.
     *
     * @param record composing record.
     * @param symbol composing symbol.
     */
    public BlobQTPComposer(DataRecord record, String symbol) {
        super(record.getScheme(), false);
        this.record = record;
        this.cipher = record.getScheme().getCodec().encode(symbol);
        this.symbol = symbol;
    }

    // ------------------------ AbstractQTPComposer implementation ------------------------

    @Override
    protected int writeRecordHeader(DataRecord record, int cipher, String symbol, int eventFlags) throws IOException {
        if (record != this.record)
            throw new IOException("Wrong record for this BLOB");
        if (cipher != this.cipher || cipher == 0 && !this.symbol.equals(symbol))
            throw new IOException("Wrong symbol for this BLOB");
        return 0;
    }

    @Override
    protected void writeHistorySubscriptionTime(DataRecord record, long time) throws IOException {
        throw new IOException("Unsupported message for BLOB");
    }

    @Override
    protected void writeIntField(DataIntField field, int value) throws IOException {
        field.writeInt(msg, value);
    }

    @Override
    protected void writeObjField(DataObjField field, Object value) throws IOException {
        field.writeObj(msg, value);
    }

    @Override
    protected void writeField(DataField field, RecordCursor cursor) throws IOException {
        field.write(msg, cursor);
    }

    @Override
    protected void writeMessageHeader(MessageType messageType) throws IOException {
        if (messageType != MessageType.HISTORY_DATA && messageType != MessageType.RAW_DATA)
            throw new IOException("Unsupported message for BLOB");
    }
}
