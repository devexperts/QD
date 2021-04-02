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
package com.devexperts.qd.qtp;

import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.TimeMarkUtil;

import java.io.IOException;

/**
 * Extended byte array composer whit support for socket-connection specific like rtt and lag.
 */
class ConnectionQTPComposer extends BinaryQTPComposer {

    // ======================== private instance fields ========================

    private final MessageAdapterConnection connection;
    private final ChunkedOutput out;

    private long lastPosition;

    private int composingTimeMark;

    // these are per-packet counts that are reset after each packet by addComposingLag
    private int countDataRecords;
    private long sumTimeMarks;

    // these are total counts that are reset when heartbeat is sent by getTotalAverageLagAndClear
    private int totalCountDataRecords;
    private long totalSumTimeMarks;

    // ======================== constructor and instance methods ========================

    ConnectionQTPComposer(DataScheme scheme, MessageAdapterConnection connection) {
        super(scheme, true);
        this.connection = connection;
        out = new ChunkedOutput(connection.getChunkPool());
        setOutput(out);
    }

    // Limits the size of the packet that is composed for socket transmission.
    // Too many bytes should not be buffered for socket transmission to limit the latency.
    @Override
    public boolean hasCapacity() {
        return getProcessed() + getMessagePayloadSize() < QTPConstants.COMPOSER_THRESHOLD;
    }

    // write chunks directly to internal out
    void writeAllFromChunkList(ChunkList chunks, Object owner) {
        try {
            out.writeAllFromChunkList(chunks, owner);
        } catch (IOException e) {
            throw new RuntimeQTPException(e); // should not happen, because it is a memory-only BufferedOutput implementation
        }
    }

    // returns number of bytes written to output after last call to getOutput
    long getProcessed() {
        return out.totalPosition() - lastPosition;
    }

    // returns chunks written so far (null if nothing)
    ChunkList getOutput(Object owner) {
        lastPosition = out.totalPosition();
        return out.getOutput(owner);
    }

    void setComposingTimeMark(int composingTimeMark) {
        this.composingTimeMark = composingTimeMark;
    }

    void addComposingLag(int composingLagMark, QDStats stats) {
        if (countDataRecords == 0)
            return;
        long totalLag = sumTimeMarks + (long) composingLagMark * countDataRecords;
        stats.updateIOWriteDataLags(totalLag);
        totalCountDataRecords += countDataRecords;
        totalSumTimeMarks += totalLag; // include composing lag
        countDataRecords = 0;
        sumTimeMarks = 0;
    }

    int getTotalAverageLagAndClear() {
        if (totalCountDataRecords == 0)
            return 0;
        int result = (int) (totalSumTimeMarks / totalCountDataRecords);
        totalCountDataRecords = 0;
        totalSumTimeMarks = 0;
        return result;
    }

    @Override
    void updateMoreIOWriteRecordStats() {
        stats.updateIOWriteRtts(connection.getConnectionRttMark());
    }

    @Override
    public void append(RecordCursor cursor) {
        super.append(cursor);
        if (currentMessageType.isData()) {
            countDataRecords++;
            int timeMark = cursor.getTimeMark();
            if (timeMark != 0)
                sumTimeMarks += TimeMarkUtil.signedDeltaMark(composingTimeMark - timeMark);
        }
    }

    @Override
    protected BinaryRecordDesc getRequestedRecordDesc(DataRecord record) {
        return connection.getRequestedRecordDesc(record);
    }

    boolean wideDecimalSupported = true;
    //FIXME preciseTimeSupported

    @Override
    protected boolean isWideDecimalSupported() {
        return wideDecimalSupported;
    }
}
