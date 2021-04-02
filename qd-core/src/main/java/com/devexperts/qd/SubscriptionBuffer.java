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
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.util.LegacyIteratorUtils;

/**
 * The <code>SubscriptionBuffer</code> class is an universal buffer for subscription,
 * able to receive and retrieve subscription via all appropriate interfaces and via
 * random access. It uses cyclic buffers for efficient subscription storage.
 * <p>
 * <b>NOTE:</b> the <code>SubscriptionBuffer</code> is <b>not</b> thread-safe;
 * it is designed to be used in a single-thread mode.
 *
 * @deprecated Use {@link RecordBuffer} with a mode of
 * {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} or {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}.
 */
@Deprecated
public class SubscriptionBuffer implements SubscriptionConsumer, SubscriptionProvider, SubscriptionIterator, SubscriptionVisitor {

    static {
        Deprecation.warning(SubscriptionBuffer.class);
    }

    private SubscriptionListener listener;

    private DataRecord[] records = new DataRecord[16];
    private int[] ciphers = new int[records.length];
    private String[] symbols = new String[records.length];
    private long[] times = new long[records.length];
    private int record_mask = records.length - 1;
    private int record_head = 0;
    private int record_tail = 0;

    private int it_cipher = 0;
    private String it_symbol = null;
    private long it_time = 0;

    // ========== Utility Stuff ==========

    /**
     * The instance of SubscriptionBuffer that is always empty.
     * @deprecated Many cases in code that use to this object do not need it any more. Double-check first.
     * If really needed, then use
     * {@link SubscriptionVisitor#VOID},
     * {@link SubscriptionIterator#VOID},
     * {@link SubscriptionConsumer#VOID}, or
     * {@link SubscriptionListener#VOID}.
     */
    public static final SubscriptionBuffer VOID = new VoidBuffer();

    /**
     * Processes subscription from specified iterator via specified visitor.
     * Returns <code>true</code> if some subscription may still remain in
     * the iterator or <code>false</code> if all subscription were processed.
     * @deprecated Use
     * {@link RecordBuffer#retrieveSubscription(SubscriptionVisitor)} or
     * {@link LegacyIteratorUtils#processSubscription(SubscriptionIterator, SubscriptionVisitor)} as last resort.
     */
    public static boolean process(SubscriptionIterator iterator, SubscriptionVisitor visitor) {
        return LegacyIteratorUtils.processSubscription(iterator, visitor);
    }

    // ========== Random Access ==========

    /**
     * Returns number of records in this subscription buffer.
     */
    public int size() {
        return (record_tail - record_head) & record_mask;
    }

    /**
     * Returns <code>true</code> if this subscription buffer has no records.
     */
    public boolean isEmpty() {
        return record_tail == record_head;
    }

    /**
     * Clears this subscription buffer.
     */
    public void clear() {
        for (int i = record_head; i != record_tail; i = (i + 1) & record_mask) {
            records[i] = null;
            ciphers[i] = 0;
            symbols[i] = null;
            times[i] = 0;
        }
        record_head = 0;
        record_tail = 0;

        it_cipher = 0;
        it_symbol = null;
        it_time = 0;
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
     * Returns time of the record by its index within this buffer.
     * Returns 0 if not historical.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public long getTime(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        return times[(record_head + index) & record_mask];
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
     * Examines record by its index within this buffer via specivied visitor.
     * <p>
     * <b>NOTE:</b> unlike bulk transfer methods, this method does not check
     * {@link SubscriptionVisitor#hasCapacity} method of specified visitor.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    public void examineRecord(int index, SubscriptionVisitor visitor) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException();
        index = (record_head + index) & record_mask;
        visitor.visitRecord(records[index], ciphers[index], symbols[index], times[index]);
    }

    /**
     * Examines accumulated subscription via specified subscription visitor.
     * Unlike retrieval, examination does not consume processed subscription.
     * Returns <code>true</code> if not all accumulated subscription were examined
     * or <code>false</code> if all accumulated subscription were examined.
     */
    public boolean examineSubscription(SubscriptionVisitor visitor) {
        for (int i = record_head; i != record_tail; i = (i + 1) & record_mask) {
            if (!visitor.hasCapacity())
                return true;
            visitor.visitRecord(records[i], ciphers[i], symbols[i], times[i]);
        }
        return false;
    }

    /**
     * Returns subscription iterator that examines this subscription buffer.
     * <p>
     * <b>NOTE:</b> returned iterator possesses no special protection against
     * concurrent modifications of this buffer and it will fail in unexpected
     * way in such event; it should be used up before any such modifications.
     */
    public SubscriptionIterator examiningIterator() {
        return new SubscriptionIterator() {
            private int index;

            private int it_cipher;
            private String it_symbol;
            private long it_time;

            public int getCipher() {
                return it_cipher;
            }

            public String getSymbol() {
                return it_symbol;
            }

            public long getTime() {
                return it_time;
            }

            public DataRecord nextRecord() {
                if (index >= SubscriptionBuffer.this.size())
                    return null;
                it_cipher = SubscriptionBuffer.this.getCipher(index);
                it_symbol = SubscriptionBuffer.this.getSymbol(index);
                it_time = SubscriptionBuffer.this.getTime(index);
                return SubscriptionBuffer.this.getRecord(index++);
            }
        };
    }

    // ========== SubscriptionConsumer Implementation ==========

    public void processSubscription(SubscriptionIterator iterator) {
        boolean was_empty = isEmpty();
        LegacyIteratorUtils.processSubscription(iterator, this);
        if (was_empty && !isEmpty())
            notifyListener();
    }

    // ========== SubscriptionProvider Implementation ==========

    public boolean retrieveSubscription(SubscriptionVisitor visitor) {
        return LegacyIteratorUtils.processSubscription(this, visitor);
    }

    public void setSubscriptionListener(SubscriptionListener listener) {
        this.listener = listener;
        if (!isEmpty())
            notifyListener();
    }

    // ========== SubscriptionIterator Implementation ==========

    public int getCipher() {
        return it_cipher;
    }

    public String getSymbol() {
        return it_symbol;
    }

    public long getTime() {
        return it_time;
    }

    public DataRecord nextRecord() {
        if (record_head == record_tail)
            return null;

        DataRecord record = records[record_head];
        it_cipher = ciphers[record_head];
        it_symbol = symbols[record_head];
        it_time = times[record_head];

        records[record_head] = null;
        ciphers[record_head] = 0;
        symbols[record_head] = null;
        times[record_head] = 0;

        record_head = (record_head + 1) & record_mask;

        return record;
    }

    // ========== SubscriptionVisitor Implementation ==========

    public boolean hasCapacity() {
        return true;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        if (record == null)
            throw new NullPointerException("record");
        ensureCapacity(record);

        records[record_tail] = record;
        ciphers[record_tail] = cipher;
        symbols[record_tail] = symbol;
        times[record_tail] = time;

        record_tail = (record_tail + 1) & record_mask;
    }

    /**
     * Visits next record using <code>time = 0</code>.
     */
    public void visitRecord(DataRecord record, int cipher, String symbol) {
        visitRecord(record, cipher, symbol, 0);
    }

    // ========== Implementation Details ==========

    /**
     * Notifies subscription listener used in <code>SubscriptionProvider</code> part
     * of this subscription buffer. Does not check actual subscription availability.
     */
    protected void notifyListener() {
        SubscriptionListener listener = this.listener; // Atomic read.
        if (listener != null)
            listener.subscriptionAvailable(this);
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
            copy(times, times = new long[new_length], record_head, record_tail, length);

            record_mask = new_length - 1;
            record_head = 0;
            record_tail = size;
        }
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

    private static class VoidBuffer extends SubscriptionBuffer implements SubscriptionListener {
        public SubscriptionIterator examiningIterator() {
            return this;
        }

        public void setSubscriptionListener(SubscriptionListener listener) {}

        public void visitRecord(DataRecord record, int cipher, String symbol, long time) {}

        public void subscriptionAvailable(SubscriptionProvider provider) {
            provider.retrieveSubscription(this);
        }
    }
}
