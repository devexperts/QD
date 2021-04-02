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
import com.devexperts.io.ChunkedInput;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;

import java.util.Arrays;

/**
 * Socket-connection specific QTP parser with support for rtt and lag measurements.
 */
class ConnectionQTPParser extends BinaryQTPParser {
    // ======================== private instance fields ========================

    private final MessageAdapterConnection connection;
    private final ChunkedInput in;

    // initialized on first use, is asynchronously read for QD-387, maps this scheme's record id to desc
    private volatile BinaryRecordDesc[] schemeMap;

    private int currentTimeMark; // =0 when unknown

    // ======================== constructor and instance methods ========================

    ConnectionQTPParser(DataScheme scheme, MessageAdapterConnection connection) {
        super(scheme);
        this.connection = connection;
        in = new ChunkedInput(connection.getChunkPool());
        setInput(in);
    }

    @Override
    public void resetSession() {
        super.resetSession();
        if (schemeMap != null)
            Arrays.fill(schemeMap, null);
    }

    @Override
    protected void remapRecord(int id, BinaryRecordDesc rr) {
        super.remapRecord(id, rr);
        if (rr.getRecord() == null)
            return; // nothing else -- skill incoming data (no record in scheme)
        // Use this scheme's ID (!!!) in schemeMap
        id = rr.getRecord().getId();
        if (schemeMap == null || id >= schemeMap.length)
            schemeMap = newRecordMap(schemeMap, id);
        // the following write races with getRequestedRecordDesc method in general, but is typical interaction where
        // Parser receives subscription and composer writes data on it, there is a synchronization via QD core
        schemeMap[id] = rr;
    }

    // It is called asynchronously from ConnectionQTPComposer via MessageAdapterConnection
    protected BinaryRecordDesc getRequestedRecordDesc(DataRecord record) {
        int id = record.getId();
        BinaryRecordDesc[] schemeMap = this.schemeMap; // atomically read volatile
        return schemeMap != null && id >= 0 && id < schemeMap.length ? schemeMap[id] : null;
    }

    void addChunks(ChunkList chunkList, Object owner) {
        in.addAllToInput(chunkList, owner);
    }

    void setCurrentTimeMark(int currentTimeMark) {
        this.currentTimeMark = currentTimeMark;
    }

    @Override
    protected RecordMode getRecordBufferMode(MessageType messageType) {
        RecordMode mode = super.getRecordBufferMode(messageType);
        return messageType.isData() ? mode.withTimeMark() : mode;
    }

    @Override
    void updateCursorTimeMark(RecordCursor cursor) {
        cursor.setTimeMark(currentTimeMark);
    }

    @Override
    void updateMoreIOReadSubRecordStats() {
        stats.updateIOReadRtts(connection.getConnectionRttMark());
    }

    @Override
    void updateMoreIOReadDataRecordStats() {
        stats.updateIOReadRtts(connection.getConnectionRttMark());
        stats.updateIOReadDataLags(connection.getIncomingLagMark());
    }

    @Override
    void onDescribeProtocol(ProtocolDescriptor desc) {
        connection.processIncomingDescribeProtocol(desc);
    }

    @Override
    void onHeartbeat(HeartbeatPayload heartbeatPayload) {
        connection.processIncomingHeartbeat(heartbeatPayload);
    }
}
