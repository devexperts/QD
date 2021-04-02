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
package com.devexperts.qd;

import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.util.LegacyIteratorUtils;

/**
 * The <code>DataBuffer</code> class is an universal buffer for data,
 * able to receive and retrieve data via all appropriate interfaces and
 * via random access. It uses cyclic buffers for efficient data storage.
 * <p>
 * <b>NOTE:</b> the <code>DataBuffer</code> is <b>not</b> thread-safe;
 * it is designed to be used in a single-thread mode.
 *
 * @deprecated Use {@link RecordBuffer} instead.
 */
public class DataBuffer implements DataConsumer, DataProvider, DataIterator, DataVisitor {

    static {
        Deprecation.warning(DataBuffer.class);
    }

    private DataListener listener;

    private DataRecord[] records = new DataRecord[16];
    private int[] ciphers = new int[records.length];
    private String[] symbols = new String[records.length];
    private int[] int_ref = new int[records.length];
    private int[] obj_ref = new int[records.length];
    private int record_mask = records.length - 1;
    private int record_head = 0;
    private int record_tail = 0;

    private int[] int_fields = new int[64];
    private int int_mask = int_fields.length - 1;
    private int int_head = 0;
    private int int_tail = 0;

    private Object[] obj_fields = new Object[16];
    private int obj_mask = obj_fields.length - 1;
    private int obj_head = 0;
    private int obj_tail = 0;

    private int it_cipher = 0;
    private String it_symbol = null;

    private int it_field = -1;
    private int it_int_count = 0;
    private int it_obj_count = 0;

    private int vis_field = -1;
    private int vis_int_count = 0;
    private int vis_obj_count = 0;

    // ========== Utility Stuff ==========

    /**
     * The instance of DataBuffer that is always empty.
     * @deprecated Many cases in code that use to this object do not need it any more. Double-check first.
     * If really needed, then use
     * {@link DataVisitor#VOID},
     * {@link DataIterator#VOID},
     * {@link DataConsumer#VOID}, or
     * {@link DataListener#VOID}.
     */
    public static final DataBuffer VOID = new VoidBuffer();

    /**
     * Processes data from specified iterator via specified visitor.
     * Returns <code>true</code> if some data may still remain in
     * the iterator or <code>false</code> if all data were processed.
     * @deprecated Use
     * {@link RecordBuffer#retrieveData(DataVisitor)} or
     * {@link LegacyIteratorUtils#processData(DataIterator, DataVisitor)} as last resort.
     */
    public static boolean process(DataIterator iterator, DataVisitor visitor) {
        return LegacyIteratorUtils.processData(iterator, visitor);
    }

    // ========== Random Access ==========

    /**
     * Returns number of records in this data buffer.
     */
    public int size() {
        return (record_tail - record_head) & record_mask;
    }

    /**
     * Returns <code>true</code> if this data buffer has no records.
     */
    public boolean isEmpty() {
        return record_tail == record_head;
    }

    /**
     * Clears this data buffer.
     */
    public void clear() {
        if (vis_field >= 0) {
            // Complete ongoing visit to properly clear visited data.
            vis_field = vis_int_count + vis_obj_count;
            completeVisit();
        }

        for (int i = record_head; i != record_tail; i = (i + 1) & record_mask) {
            records[i] = null;
            ciphers[i] = 0;
            symbols[i] = null;
            int_ref[i] = 0;
            obj_ref[i] = 0;
        }
        record_head = 0;
        record_tail = 0;

        for (int i = int_head; i != int_tail; i = (i + 1) & int_mask)
            int_fields[i] = 0;
        int_head = 0;
        int_tail = 0;

        for (int i = obj_head; i != obj_tail; i = (i + 1) & obj_mask)
            obj_fields[i] = null;
        obj_head = 0;
        obj_tail = 0;

        it_cipher = 0;
        it_symbol = null;

        it_field = -1;
        it_int_count = 0;
        it_obj_count = 0;

        vis_field = -1;
        vis_int_count = 0;
        vis_obj_count = 0;
    }

    /**
     * Returns cipher of the record by its index within this buffer.
     * Returns 0 if not encoded.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public int getCipher(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        return ciphers[(record_head + index) & record_mask];
    }

    /**
     * Returns symbol of the record by its index within this buffer.
     * Returns null if encoded.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public String getSymbol(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        return symbols[(record_head + index) & record_mask];
    }

    /**
     * Returns record by its index within this buffer.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public DataRecord getRecord(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        return records[(record_head + index) & record_mask];
    }

    /**
     * Returns time of the record given by its index within this buffer.
     * Time of the record is composed 0th and 1st ints in the record.
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     * @throws IllegalArgumentException is the corresponding record has no time.
     */
    public long getTime(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        if (!records[index].hasTime())
            throw new IllegalArgumentException();
        int ref = int_ref[index] ;
        return ((long) int_fields[ref & int_mask] << 32) | ((long) int_fields[(ref + 1) & int_mask] & 0xFFFFFFFFL);
    }

    /**
     * Returns specified Int-field of the record by its index within this buffer.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     * @throws IndexOutOfBoundsException if the int_field_index is out of range
     *         (int_field_index &lt; 0 || int_field_index &gt;= getIntFieldCount()) of corresponding record.
     */
    public int getInt(int index, int int_field_index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        index = (record_head + index) & record_mask;

        if (int_field_index < 0 || int_field_index >= records[index].getIntFieldCount())
            throw new IndexOutOfBoundsException();

        return int_fields[(int_ref[index] + int_field_index) & int_mask];
    }

    /**
     * Returns specified Obj-field of the record by its index within this buffer.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     * @throws IndexOutOfBoundsException if the obj_field_index is out of range
     *         (obj_field_index &lt; 0 || obj_field_index &gt;= getObjFieldCount()) of corresponding record.
     */
    public Object getObj(int index, int obj_field_index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        index = (record_head + index) & record_mask;

        if (obj_field_index < 0 || obj_field_index >= records[index].getObjFieldCount())
            throw new IndexOutOfBoundsException();

        return obj_fields[(obj_ref[index] + obj_field_index) & obj_mask];
    }

    /**
     * Examines record by its index within this buffer via specivied visitor.
     * <p>
     * <b>NOTE:</b> unlike bulk transfer methods, this method does not check
     * {@link DataVisitor#hasCapacity} method of specified visitor.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public void examineRecord(int index, DataVisitor visitor) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        index = (record_head + index) & record_mask;

        DataRecord record = records[index];
        int iref = int_ref[index];
        int oref = obj_ref[index];

        visitor.visitRecord(record, ciphers[index], symbols[index]);
        for (int i = 0, n = record.getIntFieldCount(); i < n; i++)
            visitor.visitIntField(record.getIntField(i), int_fields[(iref + i) & int_mask]);
        for (int i = 0, n = record.getObjFieldCount(); i < n; i++)
            visitor.visitObjField(record.getObjField(i), obj_fields[(oref + i) & obj_mask]);
    }

    /**
     * Examines accumulated data via specified data visitor.
     * Unlike retrieval, examination does not consume processed data.
     * Returns <code>true</code> if not all accumulated data were examined
     * or <code>false</code> if all accumulated data were examined.
     */
    public boolean examineData(DataVisitor visitor) {
        for (int i = 0, n = size(); i < n; i++) {
            if (!visitor.hasCapacity())
                return true;
            examineRecord(i, visitor);
        }
        return false;
    }

    public DataProvider examiningProvider() {
        return new LocalProvider();
    }

    /**
     * Returns data iterator that examines this data buffer.
     * <p>
     * <b>NOTE:</b> returned iterator possesses no special protection against
     * concurrent modifications of this buffer and it will fail in unexpected
     * way in such event; it should be used up before any such modifications.
     */
    public DataIterator examiningIterator() {
        return examiningIterator(0, size());
    }

    /**
     * Returns data iterator that examines this data buffer with optional reversal.
     * <p>
     * <b>NOTE:</b> returned iterator possesses no special protection against
     * concurrent modifications of this buffer and it will fail in unexpected
     * way in such event; it should be used up before any such modifications.
     *
     * @param reversed Record order is reversed if it is <code>true</code>.
     */
    public DataIterator examiningIterator(boolean reversed) {
        return reversed ? examiningIterator(size() - 1, -1) : examiningIterator(0, size());
    }

    /**
     * Returns data iterator that examines this data buffer in the given range.
     * <ul>
     * <li>If <code>index == last_index</code> then no data is examined.
     * <li>If <code>index &lt; last_index</code> then data is examined in forward order.
     * <li>If <code>index &gt; last_index</code> then data is examined in reverse order.
     * </ul>
     * <p>
     * <b>NOTE:</b> returned iterator possesses no special protection against
     * concurrent modifications of this buffer and it will fail in unexpected
     * way in such event; it should be used up before any such modifications.
     * @param index first index inclusive.
     * @param last_index exclusive.
     */
    public DataIterator examiningIterator(int index, int last_index) {
        if (index < last_index) {
            if (index < 0 || last_index > size())
                throw new IllegalArgumentException("indices are out of range (forward)");
                    return new LocalIterator(index, last_index, 1);
        } else if (index > last_index) {
            if (index >= size() || last_index < -1)
                throw new IllegalArgumentException("indices are out of range (reversed)");
                    return new LocalIterator(index, last_index, -1);
        } else {
            // empty iteration index == last_index
            if (index > size() || index < -1)
                throw new IllegalArgumentException("indices are out of range (empty)");
            return DataBuffer.VOID;
        }
    }

    // ========== DataConsumer Implementation ==========

    public void processData(DataIterator iterator) {
        boolean was_empty = isEmpty();
        LegacyIteratorUtils.processData(iterator, this);
        if (was_empty && !isEmpty())
            notifyListener();
    }

    // ========== DataProvider Implementation ==========

    public boolean retrieveData(DataVisitor visitor) {
        return LegacyIteratorUtils.processData(this, visitor);
    }

    public void setDataListener(DataListener listener) {
        this.listener = listener;
        if (!isEmpty())
            notifyListener();
    }

    // ========== DataIterator Implementation ==========

    public int getCipher() {
        return it_cipher;
    }

    public String getSymbol() {
        return it_symbol;
    }

    public DataRecord nextRecord() {
        if (it_field >= 0)
            throw new IllegalStateException();
        if (record_head == record_tail)
            return null;

        DataRecord record = records[record_head];
        it_cipher = ciphers[record_head];
        it_symbol = symbols[record_head];

        it_field = 0;
        it_int_count = record.getIntFieldCount();
        it_obj_count = record.getObjFieldCount();

        completeIteration();
        return record;
    }

    public int nextIntField() {
        int i = it_field++;
        if (i < 0 || i >= it_int_count) {
            it_field--;
            throw new IllegalStateException();
        }

        int value = int_fields[(int_head + i) & int_mask];

        completeIteration();
        return value;
    }

    public Object nextObjField() {
        int i = it_field++ - it_int_count;
        if (i < 0 || i >= it_obj_count) {
            it_field--;
            throw new IllegalStateException();
        }

        Object value = obj_fields[(obj_head + i) & obj_mask];

        completeIteration();
        return value;
    }

    // ========== DataVisitor Implementation ==========

    public boolean hasCapacity() {
        return true;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol) {
        if (vis_field >= 0)
            throw new IllegalStateException();

        ensureCapacity(record);

        records[record_tail] = record;
        ciphers[record_tail] = cipher;
        symbols[record_tail] = symbol;
        int_ref[record_tail] = int_tail;
        obj_ref[record_tail] = obj_tail;

        vis_field = 0;
        vis_int_count = record.getIntFieldCount();
        vis_obj_count = record.getObjFieldCount();

        completeVisit();
    }

    public void visitIntField(DataIntField field, int value) {
        int i = vis_field++;
        if (i < 0 || i >= vis_int_count || field != records[record_tail].getIntField(i)) {
            vis_field--;
            throw new IllegalStateException();
        }

        int_fields[(int_tail + i) & int_mask] = value;

        completeVisit();
    }

    public void visitObjField(DataObjField field, Object value) {
        int i = vis_field++ - vis_int_count;
        if (i < 0 || i >= vis_obj_count || field != records[record_tail].getObjField(i)) {
            vis_field--;
            throw new IllegalStateException();
        }

        obj_fields[(obj_tail + i) & obj_mask] = value;

        completeVisit();
    }


    // ========== Implementation Details ==========

    /**
     * Checks and completes ongoing iteration if it has finished current record.
     */
    protected void completeIteration() {
        if (it_field != it_int_count + it_obj_count)
            return;

        records[record_head] = null;
        ciphers[record_head] = 0;
        symbols[record_head] = null;
        int_ref[record_head] = 0;
        obj_ref[record_head] = 0;
        for (int i = it_int_count; --i >= 0;)
            int_fields[(int_head + i) & int_mask] = 0;
        for (int i = it_obj_count; --i >= 0;)
            obj_fields[(obj_head + i) & obj_mask] = null;

        record_head = (record_head + 1) & record_mask;
        int_head = (int_head + it_int_count) & int_mask;
        obj_head = (obj_head + it_obj_count) & obj_mask;

        it_field = -1;
        it_int_count = 0;
        it_obj_count = 0;
    }

    /**
     * Checks and completes ongoing visit if it has finished current record.
     */
    protected void completeVisit() {
        if (vis_field != vis_int_count + vis_obj_count)
            return;

        record_tail = (record_tail + 1) & record_mask;
        int_tail = (int_tail + vis_int_count) & int_mask;
        obj_tail = (obj_tail + vis_obj_count) & obj_mask;

        vis_field = -1;
        vis_int_count = 0;
        vis_obj_count = 0;
    }

    /**
     * Notifies data listener used in <code>DataProvider</code> part
     * of this data buffer. Does not check actual data availability.
     */
    protected void notifyListener() {
        DataListener listener = this.listener; // Atomic read.
        if (listener != null)
            listener.dataAvailable(this);
    }

    /**
     * Ensures that this buffer has capacity to visit and store specified record.
     * If required, reallocates all internal buffers, copies data and rearranges
     * heads and tails to start with 0.
     */
    protected void ensureCapacity(DataRecord record) {
        int size = (record_tail - record_head) & record_mask;
        if (record_mask - size <= 2) {
            int length = records.length;
            int new_length = length << 1;

            copy(records, records = new DataRecord[new_length], record_head, record_tail, length);
            copy(ciphers, ciphers = new int[new_length], record_head, record_tail, length);
            copy(symbols, symbols = new String[new_length], record_head, record_tail, length);
            copy(int_ref, int_ref = new int[new_length], record_head, record_tail, length);
            copy(obj_ref, obj_ref = new int[new_length], record_head, record_tail, length);

            record_mask = new_length - 1;
            record_head = 0;
            record_tail = size;
        }

        size = (int_tail - int_head) & int_mask;
        if (int_mask - size <= record.getIntFieldCount() + 1) {
            int length = int_fields.length;
            int new_length = growLength(length, size + record.getIntFieldCount() + 1);

            copy(int_fields, int_fields = new int[new_length], int_head, int_tail, length);
            for (int i = record_head; i != record_tail; i = (i + 1) & record_mask)
                int_ref[i] = (int_ref[i] - int_head) & int_mask;

            int_mask = new_length - 1;
            int_head = 0;
            int_tail = size;
        }

        size = (obj_tail - obj_head) & obj_mask;
        if (obj_mask - size <= record.getObjFieldCount() + 1) {
            int length = obj_fields.length;
            int new_length = growLength(length, size + record.getObjFieldCount() + 1);

            copy(obj_fields, obj_fields = new Object[new_length], obj_head, obj_tail, length);
            for (int i = record_head; i != record_tail; i = (i + 1) & record_mask)
                obj_ref[i] = (obj_ref[i] - obj_head) & obj_mask;

            obj_mask = new_length - 1;
            obj_head = 0;
            obj_tail = size;
        }
    }

    /**
     * Grows length twice until it is larger than size.
     */
    protected static int growLength(int length, int size) {
        do {
            length <<= 1;
        } while (length > 0 && length < size);
        if (length <= 0 || size < 0)
            throw new OutOfMemoryError();
        return length;
    }

    /**
     * Copies cyclic array buffer from source to destination.
     * Uses source, source head index, source tail index and source length.
     * The destination buffer will start with 0 and end with original size.
     */
    protected static void copy(Object src, Object dst, int head, int tail, int length) {
        if (tail < head) {
            System.arraycopy(src, head, dst, 0, length - head);
            System.arraycopy(src, 0, dst, length - head, tail);
        } else
            System.arraycopy(src, head, dst, 0, tail - head);
    }

    private static class VoidBuffer extends DataBuffer implements DataListener {
        VoidBuffer() {}

        public DataIterator examiningIterator() {
            return this;
        }

        public void setDataListener(DataListener listener) {}

        public void visitRecord(DataRecord record, int cipher, String symbol) {}

        public void visitIntField(DataIntField field, int value) {}

        public void visitObjField(DataObjField field, Object value) {}

        public void dataAvailable(DataProvider provider) {
            provider.retrieveData(this);
        }
    }

    private class LocalProvider implements DataProvider {
        private final DataIterator it = examiningIterator();

        LocalProvider() {}

        public boolean retrieveData(DataVisitor visitor) {
            return LegacyIteratorUtils.processData(it, visitor);
        }

        public void setDataListener(DataListener listener) {
            // nothing here
        }
    }

    private class LocalIterator implements DataIterator {
        private int index;
        private final int last_index;
        private final int increment;

        private int it_cipher;
        private String it_symbol;

        private int it_field = -1;
        private int it_int_count;
        private int it_obj_count;

        public LocalIterator(int first_index, int last_index, int increment) {
            this.index = first_index;
            this.last_index = last_index;
            this.increment = increment;
        }

        public int getCipher() {
            return it_cipher;
        }

        public String getSymbol() {
            return it_symbol;
        }

        public DataRecord nextRecord() {
            if (it_field >= 0)
                throw new IllegalStateException();
            if (index == last_index)
                return null;

            DataRecord record = DataBuffer.this.getRecord(index);
            it_cipher = DataBuffer.this.getCipher(index);
            it_symbol = DataBuffer.this.getSymbol(index);

            it_field = 0;
            it_int_count = record.getIntFieldCount();
            it_obj_count = record.getObjFieldCount();

            completeIteration();
            return record;
        }

        public int nextIntField() {
            int i = it_field++;
            if (i < 0 || i >= it_int_count) {
                it_field--;
                throw new IllegalStateException();
            }

            int value = DataBuffer.this.getInt(index, i);

            completeIteration();
            return value;
        }

        public Object nextObjField() {
            int i = it_field++ - it_int_count;
            if (i < 0 || i >= it_obj_count) {
                it_field--;
                throw new IllegalStateException();
            }

            Object value = DataBuffer.this.getObj(index, i);

            completeIteration();
            return value;
        }

        private void completeIteration() {
            if (it_field != it_int_count + it_obj_count)
                return;

            index += increment;

            it_field = -1;
            it_int_count = 0;
            it_obj_count = 0;
        }
    }
}
