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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

/**
 * The <code>TickerMatrix</code> stores current values of certain data record
 * for all symbols. The {@link TickerStorage} maintains a set of <code>TickerMatrix</code>
 * to store current values of all data records.
 * <p>
 * The <code>TickerMatrix</code> requires synchronized write access, but it allows
 * unsynchronized read-only access at any time. The synchronization is usually
 * inherited from appropriate structural global lock of QD. For convenience
 * and maintainability, all write access must be performed only via corresponding
 * {@link TickerStorage} instance.
 */
final class TickerMatrix extends AbstractMatrix {
    private final DataRecord record;
    private final boolean withEventTimeSequence;
    private final int intOffset;
    private final int objOffset;

    TickerMatrix(DataRecord record, Mapper mapper, int capacity, int prev_magic, boolean withEventTimeSequence) {
        super(mapper, record.getIntFieldCount() + 1 + (withEventTimeSequence ? 2 : 0), record.getObjFieldCount(), capacity, prev_magic, Hashing.MAX_SHIFT);
        this.record = record;
        this.withEventTimeSequence = withEventTimeSequence;
        this.intOffset = 1 + (withEventTimeSequence ? 2 : 0);
        this.objOffset = 0;
    }

    // ========== Maintenance ==========
    // Shall be used only by TickerStorage class.

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    TickerMatrix rehash() {
        // allocate memory first -- leave structure untouched if crashes due to OOM
        TickerMatrix dest = new TickerMatrix(record, mapper, payloadSize, magic, withEventTimeSequence);
        startRehash();
        for (int index = matrix.length; (index -= step) > 0;) {
            int key = matrix[index];
            if ((key & Mapping.PAYLOAD) != 0) {
                int dest_index = dest.getIndex(key, -1);
                if (dest.matrix[dest_index] != 0)
                    throw new IllegalStateException("Repeated key");
                System.arraycopy(matrix, index, dest.matrix, dest_index, step);
                if (obj_step != 0)
                    System.arraycopy(obj_matrix, (index / step) * obj_step, dest.obj_matrix, (dest_index / step) * obj_step, obj_step);
                dest.overallSize++;
                dest.addPayload();
            } else if (key != 0 && key != Mapping.DELETED_CIPHER)
                mapper.decCounter(key | Mapping.VALID_KEY);
        }
        if (dest.overallSize != payloadSize || dest.payloadSize != payloadSize)
            throw new IllegalStateException("Payload integrity corrupted.");
        return dest;
    }

    // ========== Record Transfer ==========
    // Shall be used only by Ticker class.

    /**
     * Puts single record into storage. Returns whether some value has changed.
     * <p>
     * <b>NOTE:</b> The <code>key</code> must correspond to the iterated record.
     */
    // SYNC: global
    boolean putRecordCursor(int key, int rid, RecordCursor cursor, RecordCursorKeeper keeper, QDStats stats) {
        int index = getOrReuseIndex(key);
        boolean inserted = addIndex(key, index);
        RecordCursor.Owner owner = keeper.getForTickerMatrix(this, false);
        owner.setSymbol(cursor.getCipher(), cursor.getSymbol());
        owner.setOffsets(index + intOffset, obj_step == 0 ? 0 : index / step * obj_step + objOffset);
        RecordCursor to = owner.cursor();
        boolean changed = record.update(cursor, to);
        if (changed)
            to.setEventTimeSequence(cursor.getEventTimeSequence());
        if (inserted)
            stats.updateAdded(rid);
        else if (changed)
            stats.updateChanged(rid);
        return inserted || changed;
    }

    private int getOrReuseIndex(int key) {
        int index = getIndex(key, -1);
        // just return if found or cannot reuse it at all
        if (matrix[index] != 0 || (key & SymbolCodec.VALID_CIPHER) != 0)
            return index;
        // compute index for original key, but look for deleted key
        index = ((key * magic) >>> shift) * step;
        key = key & ~Mapping.VALID_KEY;
        int test_key;
        while ((test_key = matrix[index]) != key) {
            if (test_key == 0) {
                if (index > 0)
                    return index;
                index = matrix.length;
            }
            index -= step;
        }
        return index;
    }

    // SYNC: global
    private boolean addIndex(int key, int index) {
        int prev_key = matrix[index];
        if (prev_key == key)
            return false;
        if (key == 0)
            throw new IllegalArgumentException("Undefined key");
        if (prev_key != 0) {
            // reuse of cleared slot with same unencodeable symbol (key with erased bit)
            if ((prev_key | Mapping.VALID_KEY) != key)
                throw new IllegalArgumentException("Reusing wrong key slot");
        } else {
            if ((key & SymbolCodec.VALID_CIPHER) == 0)
                mapper.incCounter(key);
            overallSize++;
        }
        addPayload();
        matrix[index] = key;
        return true;
    }

    /**
     * Gets single record from storage into a given sink and returns true if it was there.
     * If called for non-available record, will return false without appending record.
     * This method does not check if sink has capacity (shall be checked beforehand).
     */
    // SYNC: local
    boolean getRecordData(int key, RecordSink sink, RecordCursorKeeper keeper, int mark, Object attachment) {
        int cipher = key;
        String symbol = null;
        if ((key & SymbolCodec.VALID_CIPHER) == 0) {
            cipher = 0;
            // UNSYNC: the caller guarantees that key is present in mapper.
            symbol = mapper.getSymbol(key);
            // UNSYNC: if this matrix has its own mapping (it was rehashed),
            // then key shall be remapped using local mapping.
            if (mapping != null)
                key = mapping.getKey(symbol);
        }
        int index = getIndex(key, 0);
        if (index == 0)
            return false;
        getRecordInternal(index, cipher, symbol, sink, keeper, mark, attachment);
        return true;
    }

    void setupOwner(RecordCursor.Owner owner, boolean readOnly) {
        owner.setReadOnly(readOnly);
        owner.setRecord(record, withEventTimeSequence ? RecordMode.TIMESTAMPED_DATA : RecordMode.DATA);
        owner.setArrays(matrix, obj_matrix);
    }

    private void getRecordInternal(int index, int cipher, String symbol, RecordSink sink, RecordCursorKeeper keeper,
        int mark, Object attachment)
    {
        RecordCursor.Owner owner = keeper.getForTickerMatrix(this, true);
        owner.setSymbol(cipher, symbol);
        owner.setOffsets(index + intOffset, obj_step == 0 ? 0 : index / step * obj_step + objOffset);
        owner.setTimeMark(mark);
        owner.setAttachment(attachment);
        sink.append(owner.cursor());
    }

    void removeRecord(int key, int rid, QDStats stats) {
        int index = getIndex(key, 0);
        if (index == 0)
            return;
        if (--payloadSize < 0)
            throw new IllegalStateException("Payload size underflow");
        matrix[index] = (key & SymbolCodec.VALID_CIPHER) != 0 ?
            Mapping.DELETED_CIPHER : key & ~Mapping.VALID_KEY;
        clearIndexData(index, 1);
        stats.updateRemoved(rid);
    }

    boolean hasRecord(int key) {
        return getIndex(key, 0) != 0;
    }

    // ========== Access ==========
    // Shall be used only by Ticker direct access methods.

    boolean isAvailable(int cipher, String symbol) {
        return getIndex(cipher, symbol) != 0;
    }

    int getInt(int cipher, String symbol, int int_field_index) {
        return matrix[getIndex(cipher, symbol) + intOffset + int_field_index];
    }

    Object getObj(int cipher, String symbol, int obj_field_index) {
        return obj_matrix[(getIndex(cipher, symbol) / step) * obj_step + objOffset + obj_field_index];
    }

    void getData(RecordCursor.Owner owner, int cipher, String symbol) {
        int index = getIndex(cipher, symbol);
        getDataAt(index, owner, cipher, symbol);
    }

    public boolean getDataIfAvailable(RecordCursor.Owner owner, int cipher, String symbol) {
        int index = getIndex(cipher, symbol);
        if (index == 0)
            return false;
        getDataAt(index, owner, cipher, symbol);
        return true;
    }

    private void getDataAt(int index, RecordCursor.Owner owner, int cipher, String symbol) {
        owner.setReadOnly(true);
        owner.setSymbol(cipher, symbol);
        owner.setRecord(record, withEventTimeSequence ? RecordMode.TIMESTAMPED_DATA : RecordMode.DATA);
        owner.setArrays(matrix, obj_matrix);
        owner.setOffsets(index + intOffset, obj_step == 0 ? 0 : index / step * obj_step + objOffset);
    }

    // returns -1 when sink has no more capacity or number of records examined in next batch so far
    int examineData(RecordSink sink, RecordCursorKeeper keeper, int nExaminedInBatch) {
        // iterate over matrix
        for (int index = matrix.length; (index -= step) >= 0;)
            if ((matrix[index] & Mapping.PAYLOAD) != 0) {
                int key = matrix[index];
                int cipher = key;
                String symbol = null;
                if ((key & SymbolCodec.VALID_CIPHER) == 0) {
                    cipher = 0;
                    symbol = getMapping().getSymbolIfPresent(key); // do not cache mapping to see concurrent mapping rehash
                    if (symbol == null)
                        continue;  // not found -- was just added, but we don't "see" its mapping (mapping was rehashed, or...)
                }
                if (!sink.hasCapacity()) {
                    if (nExaminedInBatch > 0)
                        sink.flush();
                    return -1;
                }
                getRecordInternal(index, cipher, symbol, sink, keeper, 0, null);
                nExaminedInBatch++;
                if (nExaminedInBatch >= Collector.EXAMINE_BATCH_SIZE) {
                    sink.flush();
                    nExaminedInBatch = 0;
                }
            }
        return nExaminedInBatch;
    }

    void examineDataAlways(int key, int cipher, String symbol, RecordSink sink,
        RecordCursorKeeper keeper, Object attachment)
    {
        int index = getIndex(key, 0);
        if (index != 0)
            getRecordInternal(index, cipher, symbol, sink, keeper, 0, attachment);
    }

    private void addPayload() {
        if (++payloadSize > overallSize)
            throw new IllegalStateException("Payload size overflow");
    }

    // ========== Debugging ==========

    @Override
    public String toString() {
        return "TickerMatrix#" + record.getId();
    }

    void visitTickerMatrixSymbols(CollectorDebug.SymbolReferenceVisitor srv, CollectorDebug.SymbolReferenceLocation srl) {
        srl.index = 0;
        srv.visitSubMatrix(srl);
        int rid = record.getId();
        for (int index = matrix.length; (index -= step) >= 0;) {
            int key = getInt(index + Collector.KEY);
            if (key == 0 || key == Mapping.DELETED_CIPHER)
                continue;
            srl.index = index;
            if ((key & SymbolCodec.VALID_CIPHER) != 0) {
                srv.visitSymbolReference(key, rid, true, srl);
            } else {
                boolean payload = (key & Mapping.VALID_KEY) != 0;
                if (!payload)
                    key |= Mapping.VALID_KEY;
                srv.visitSymbolReference(key, rid, payload, srl);
            }
        }
    }

}
