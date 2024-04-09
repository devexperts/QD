/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.ng;

import com.devexperts.io.BufferedInput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.util.TimeMarkUtil;
import com.devexperts.qd.util.TimeSequenceUtil;

import java.io.DataInput;
import java.io.IOException;
import java.util.Objects;

/**
 * Provides high-performance safe read (and optional write) access to one record
 * (one row on information in DB terms) directly from any <code>int[]</code> and <code>Object[]</code>
 * data arrays that might constitute a part of a larger data structure.
 *
 * <p>Data can be read directly with {@link #getInt getInt} and {@link #getObj getObj} methods
 * and written with {@link #setInt setInt}, {@link #setObj setObj}, and other helper methods (unless cursor is
 * {@link #isReadOnly read-only}).
 * Users of the cursor cannot change the record and symbol this cursor works with.
 * To update this information (and change the read-only flag) one must own the reference to {@link Owner Owner} object
 * for this <code>Cursor</code> and use {@link Owner#setRecord Owner.setRecord},
 * {@link Owner#setSymbol Owner.setSymbol}, and {@link Owner#setReadOnly Owner.setReadOnly} methods.
 *
 * <p><code>RecordCursor</code> and the corresponding {@link Owner Owner} objects can be allocated with the following methods:
 * <ul>
 * <li>{@link #allocateOwner()} - to allocate fresh cursor without any memory allocated for record.
 * <li>{@link #allocateOwner(DataRecord)} - to allocate fresh cursor with memory storage for a given record.
 * <li>{@link #allocateOwner(DataRecord, int, String)} - same as above, also specify symbol.
 * <li>{@link #allocateOwner(RecordCursor)} - same as above, but copies everything (including int and obj fields) from the other record source.
 * </ul>
 *
 * <p>All freshly allocated cursors are not read-only.
 * Use {@link Owner#setReadOnly Owner.useReadOnly} if you need them be read-only.
 *
 * <p>When you do not need {@link Owner Owner}, then you can save on allocation of the corresponding {@link Owner Owner} object
 * by calling the following methods:
 * <ul>
 * <li>{@link #allocate(DataRecord, int, String)} - to allocate fresh cursor with memory storage for a given record and also specify symbol.
 * <li>{@link #allocate(DataRecord, String)} - to allocate fresh cursor with memory storage for a given record and also specify symbol
 *                   (this method also encodes symbol to cipher).
 * <li>{@link #allocate(RecordCursor)} - same as above, but copies everything (including int and obj fields) from the other record source.
 * </ul>
 *
 * <p><b>This class is not synchronized and is not thread-safe without external synchronization.</b>
 */
public final class RecordCursor {
    static final int UNLINKED = -1;

    private boolean readOnly;
    private DataRecord record;
    private RecordMode mode;
    private int cipher;
    private String symbol;
    private int[] intFlds;
    private Object[] objFlds;
    private int intOffset;
    private int objOffset;

    // These fields are directly used by RecordBuffer
    int intCount;
    int objCount;

    // These fields can be directly set via Owner reference
    int eventFlags;
    int timeMark;
    Object attachment;

    //----------------------- public API -----------------------

    /**
     * <code>RecordCursor</code> contains an implicit reference to {@link RecordCursor}.
     * It is used to configure the cursor via
     * {@link RecordCursor.Owner#setRecord}, {@link RecordCursor.Owner#setSymbol}, and
     * {@link RecordCursor.Owner#setReadOnly}
     * methods and to reset it via {@link #reset reset} method. Users of the
     * {@link RecordCursor} itself cannot change the data this cursor points to.
     */
    public final class Owner {
        Owner() {}

        /**
         * Returns owned {@link RecordCursor}.
         */
        public RecordCursor cursor() {
            return RecordCursor.this;
        }

        /**
         * Changes read only mode of the corresponding cursor.
         * @param read_only read only mode.
         */
        public void setReadOnly(boolean read_only) {
            setReadOnlyInternal(read_only);
        }

        /**
         * Changes record and sets a default mode of {@link RecordMode#DATA DATA}.
         * @param record the record.
         */
        public void setRecord(DataRecord record) {
            setRecordInternal(record, RecordMode.DATA);
        }

        /**
         * Changes record and sets a specified mode.
         * @param record the record.
         * @param mode the mode.
         */
        public void setRecord(DataRecord record, RecordMode mode) {
            setRecordInternal(record, mode);
        }

        /**
         * Changes cipher and symbol.
         * @param cipher the cipher.
         * @param symbol the symbol.
         */
        public void setSymbol(int cipher, String symbol) {
            setSymbolInternal(cipher, symbol);
        }

        /**
         * Changes pointer to data integer and object array.
         * @param intFlds data integer array.
         * @param objFlds data object array.
         */
        public void setArrays(int[] intFlds, Object[] objFlds) {
            setArraysInternal(intFlds, objFlds);
        }

        /**
         * Changes offsets in data integer and data object arrays.
         * @param intOffset integer data offset.
         * @param objOffset object data offset.
         */
        public void setOffsets(int intOffset, int objOffset) {
            setOffsetsInternal(intOffset, objOffset);
            //rangeCheckInternal(); // Don't range check for performance, will fail later
        }

        /**
         * Configures the {@link #cursor() cursor} to be exactly as the other cursor.
         * The cursor will point to the same backing arrays as the other cursor.
         * All properties are copied, including
         * {@link #isReadOnly() readOnly}, {@link #getRecord() record}, {@link #getMode() mode}, etc.
         * As a result, {@link #setEventFlags(int) eventFlags},
         * {@link #setTimeMark(int) timeMark}, or {@link #setAttachment(Object) attachment} can be changed in the cursor
         * without actually copying any data even if the original cursor was {@link #isReadOnly() readOnly}.
         * @param other the other cursor.
         */
        public void setAs(RecordCursor other) {
            setAsInternal(other);
        }

        /**
         * Resets cursor to its initial state. Internal state is completely cleared with the
         * exception of {@link #getMode() mode}, which retains its value.
         */
        public void reset() {
            resetInternal();
        }

        /**
         * Changes event flags that are associated with the current cursor
         * when event flags are not part of the record mode.
         * This method has no effect when {@link #getMode() mode} has its own {@link RecordMode#hasEventFlags() event flags}.
         */
        public void setEventFlags(int eventFlags) {
            RecordCursor.this.eventFlags = eventFlags;
        }

        /**
         * Changes time mark that is associated with the current cursor
         * when time mark is not part of the record mode.
         * This method has no effect when {@link #getMode() mode} has its own {@link RecordMode#hasTimeMark() time mark}.
         * @see TimeMarkUtil
         */
        public void setTimeMark(int timeMark) {
            RecordCursor.this.timeMark = timeMark;
        }

        /**
         * Changes object attachment that is associated with the current cursor
         * when attachment is not part of the record mode.
         * This method has no effect when {@link #getMode() mode} has its own {@link RecordMode#hasAttachment() attachment}.
         */
        public void setAttachment(Object attachment) {
            RecordCursor.this.attachment = attachment;
        }

    }

    /**
     * Allocates <code>RecordCursor</code> that points to a freshly allocated storage with a copy of
     * data from a given source.
     * This method is a shortcut to
     * {@code allocateOwner(source).cursor()},
     * but this method does not actually allocate memory for {@link Owner Owner} object.
     */
    public static RecordCursor allocate(RecordCursor source) {
        RecordCursor cursor = allocate(source.getRecord(), source.getCipher(), source.getSymbol());
        cursor.copyFrom(source);
        return cursor;
    }

    /**
     * Allocates writable <code>RecordCursor</code> that points to a freshly allocated storage for a given record's
     * integer and object field values and also sets symbol. It uses a default {@link RecordMode#DATA DATA} mode.
     * This method is a shortcut to
     * {@code allocateOwner(record, cipher, symbol).cursor()},
     * but this method does not actually allocate memory for {@link Owner Owner} object.
     */
    public static RecordCursor allocate(DataRecord record, int cipher, String symbol) {
        return allocate(record, cipher, symbol, RecordMode.DATA);
    }

    /**
     * Allocates writable <code>RecordCursor</code> that points to a freshly allocated storage for a given record's
     * integer and object field values using a specified mode and also sets symbol.
     * This method is a shortcut to
     * {@code allocateOwner(record, cipher, symbol, mode).cursor()},
     * but this method does not actually allocate memory for {@link Owner Owner} object.
     */
    public static RecordCursor allocate(DataRecord record, int cipher, String symbol, RecordMode mode) {
        RecordCursor cursor = allocateInternal(record, mode);
        cursor.setSymbolInternal(cipher, symbol);
        return cursor;
    }

    /**
     * Allocates writable <code>RecordCursor</code> that points to a freshly allocated storage for a given record's
     * integer and object field values and also sets symbol. It uses a default {@link RecordMode#DATA DATA} mode.
     * This method is a shortcut to
     * {@code allocate(record, record.getScheme().getCodec().encode(symbol), symbol)}.
     */
    public static RecordCursor allocate(DataRecord record, String symbol) {
        return allocate(record, record.getScheme().getCodec().encode(symbol), symbol);
    }

    /**
     * Allocates writable <code>RecordCursor</code> that points to a freshly allocated storage for a given record's
     * integer and object field values using a specified mode and also sets symbol.
     * This method is a shortcut to
     * {@code allocate(record, record.getScheme().getCodec().encode(symbol), symbol, mode)}.
     */
    public static RecordCursor allocate(DataRecord record, String symbol, RecordMode mode) {
        return allocate(record, record.getScheme().getCodec().encode(symbol), symbol, mode);
    }

    /**
     * Allocates <code>RecordCursor.Owner</code> that points to a freshly allocated storage with a copy of
     * data from a given source.
     * This method is a shortcut to <pre>
     * allocateOwner(source.getRecord(), source.getCipher(), source.getSymbol()).
     *     copyFrom(source)</pre>.
     */
    public static Owner allocateOwner(RecordCursor source) {
        return allocate(source).new Owner();
    }

    /**
     * Allocates writable <code>RecordCursor.Owner</code> that points to a freshly allocated storage for a given record's
     * integer and object field values and also sets symbol. It uses a default {@link RecordMode#DATA DATA} mode.
     * This method is a shortcut to
     * {@code allocateOwner(record).setSymbol(cipher, symbol)}.
     */
    public static Owner allocateOwner(DataRecord record, int cipher, String symbol) {
        return allocate(record, cipher, symbol).new Owner();
    }

    /**
     * Allocates writable <code>RecordCursor.Owner</code> that points to a freshly allocated storage for a given record's
     * integer and object field values using a specified mode and also sets symbol.
     * This method is a shortcut to
     * {@code allocateOwner(record, mode).setSymbol(cipher, symbol)}.
     */
    public static Owner allocateOwner(DataRecord record, int cipher, String symbol, RecordMode mode) {
        return allocate(record, cipher, symbol, mode).new Owner();
    }

    /**
     * Allocates writable <code>RecordCursor.Owner</code> that points to a freshly allocated storage for a given record's
     * integer and object field values. It uses a default {@link RecordMode#DATA DATA} mode.
     * This method is a shortcut to <pre>
     * Owner.allocateOwner().setRecord(record,
     *     <b>new</b> <b>int</b>[record.getIntFieldCount()], 0,
     *     <b>new</b> Object[record.getObjFieldCount()], 0)</pre>
     */
    public static Owner allocateOwner(DataRecord record) {
        return allocateInternal(record, RecordMode.DATA).new Owner();
    }

    /**
     * Allocates writable <code>RecordCursor.Owner</code> that points to a freshly allocated storage for a given record's
     * integer and object field values using a specified mode.
     */
    public static Owner allocateOwner(DataRecord record, RecordMode mode) {
        return allocateInternal(record, mode).new Owner();
    }

    /**
     * Returns newly allocated record cursor owner object.
     * The resulting owner and the cursor are not configured, use
     * {@link Owner#setRecord Owner.setRecord}, {@link Owner#setSymbol Owner.setSymbol}, and {@link Owner#setReadOnly Owner.setReadOnly}
     * methods to configure the corresponding cursor.
     */
    public static Owner allocateOwner() {
        return new RecordCursor(false).new Owner();
    }

    /**
     * Returns record.
     * @return record.
     */
    public DataRecord getRecord() {
        return record;
    }

    /**
     * Returns mode.
     * @return mode.
     */
    public RecordMode getMode() {
        return mode;
    }

    /**
     * Returns cipher or 0 if symbol is not coded.
     * @return cipher.
     */
    public int getCipher() {
        return cipher;
    }

    /**
     * Returns symbol or {@code null} if symbol is coded in {@link #getCipher() cipher}.
     * @return symbol.
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns symbol that is decoded from cipher if needed.
     * @return decoded symbol.
     */
    public String getDecodedSymbol() {
        return record.getScheme().getCodec().decode(cipher, symbol);
    }

    /**
     * Returns the number of integer data fields.
     * @return the number of integer data fields.
     */
    public int getIntCount() {
        return intCount;
    }

    /**
     * Returns the value of the specified integer field for the record pointed to by this cursor.
     * @param intFieldIndex index of the field starting from 0.
     * @return the value of integer field.
     * @throws IndexOutOfBoundsException if intFieldIndex is negative or more or equal to the number of
     * integer fields in the corresponding record.
     */
    public int getInt(int intFieldIndex) {
        if (intFieldIndex < 0 || intFieldIndex >= intCount)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount);
        return intFlds[intOffset + intFieldIndex];
    }

    int getIntMappedImpl(DataRecord record, int intFieldIndex) {
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        return intFlds[intOffset + intFieldIndex];
    }

    /**
     * Returns the value of the specified integer field for the record pointed to by this cursor.
     * @param intFieldIndex index of the field starting from 0.
     * @return the value of integer field.
     * @throws IndexOutOfBoundsException if intFieldIndex is negative or more or equal to the number of
     * integer fields in the corresponding record.
     */
    public long getLong(int intFieldIndex) {
        if (intFieldIndex < 0 || intFieldIndex >= intCount - 1)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount - 1);
        return getLongImpl(intFieldIndex);
    }

    long getLongMappedImpl(DataRecord record, int intFieldIndex) {
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        return getLongImpl(intFieldIndex);
    }

    /**
     * Returns the number of object data fields.
     * @return the number of object data fields.
     */
    public int getObjCount() {
        return objCount;
    }

    /**
     * Returns the value of the specified object field for the record pointed to by this cursor.
     * @param objFieldIndex index of the field starting from 0.
     * @return the value of object field.
     * @throws IndexOutOfBoundsException if objFieldIndex is negative or more or equal to the number of
     * object fields in the corresponding record.
     */
    public Object getObj(int objFieldIndex) {
        if (objFieldIndex < 0 || objFieldIndex >= objCount)
            Throws.throwIndexOutOfBoundsException(objFieldIndex, objCount);
        return objFlds[objOffset + objFieldIndex];
    }

    Object getObjMappedImpl(DataRecord record, int objFieldIndex) {
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        return objFlds[objOffset + objFieldIndex];
    }

    public void getIntsTo(int intFieldIndex, int[] to, int offset, int length) {
        // get minimum of requested and available
        length = Math.min(length, intCount - intFieldIndex);
        // do range check
        if (intFieldIndex < 0 || length < 0)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount, length);
        getIntsImpl(intFieldIndex, to, offset, length);
    }

    public void getObjsTo(int objFieldIndex, Object[] to, int offset, int length) {
        // get minimum of requested and available
        length = Math.min(length, objCount - objFieldIndex);
        // do range check
        if (objFieldIndex < 0 || length < 0)
            Throws.throwIndexOutOfBoundsException(objFieldIndex, objCount, length);
        getObjsImpl(objFieldIndex, to, offset, length);
    }

    public boolean updateIntsTo(int intFieldIndex, int[] to, int offset, int length) {
        // update minimum of requested and available
        length = Math.min(length, intCount - intFieldIndex);
        // do range check
        if (intFieldIndex < 0 || length < 0)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount, length);
        if (offset < 0 || offset > to.length - length)
            Throws.throwIndexOutOfBoundsException(offset, to.length, length);
        boolean changed = false;
        for (int i = 0; i < length; i++) {
            int v = intFlds[intOffset + intFieldIndex + i];
            if (!record.getIntField(intFieldIndex + i).equals(to[offset + i], v)) {
                to[offset + i] = v;
                changed = true;
            }
        }
        return changed;
    }

    public boolean updateObjsTo(int objFieldIndex, Object[] to, int offset, int length) {
        // get minimum of requested and available
        length = Math.min(length, objCount - objFieldIndex);
        // do range check
        if (objFieldIndex < 0 || length < 0)
            Throws.throwIndexOutOfBoundsException(objFieldIndex, objCount, length);
        if (offset < 0 || offset > to.length - length)
            Throws.throwIndexOutOfBoundsException(offset, to.length, length);
        boolean changed = false;
        for (int i = 0; i < length; i++) {
            Object v = objFlds[objOffset + objFieldIndex + i];
            if (!record.getObjField(objFieldIndex + i).equals(to[offset + i], v)) {
                to[offset + i] = v;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Returns {@code true} if the cursor contains time value, that is when current
     * {@link #getRecord() record} {@link DataRecord#hasTime() has time} and
     * current {@link #getMode() mode} is either
     * {@link RecordMode#DATA DATA} or {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}.
     * @return {@code true} if the cursor contains time value.
     */
    public boolean hasTime() {
        return record.hasTime() && intCount >= 2;
    }

    /**
     * Returns time of this record that is a long value composed of the value of the first two integer fields.
     * This value is used in history protocol.
     * This method returns zero when {@link #hasTime() hasTime} returns {@code false}.
     */
    public long getTime() {
        return hasTime() ? getLongImpl(0) : 0;
    }

    /**
     * Changes time of this record that is a long value composed of the value of the first two integer fields.
     * This value is used in history protocol.
     * This method does nothing when {@link #hasTime() hasTime} returns {@code false}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     */
    public void setTime(long time) {
        if (hasTime())
            setLongImpl(0, time);
    }

    /**
     * Returns {@code true} when this cursor keeps additional event flags with each data record.
     * Additional event flags either provided with the data when {@link #getMode() mode} has
     * {@link RecordMode#hasEventFlags() event flags} or by the cursor owner via
     * {@link Owner#setEventFlags(int) Owner.setEventFlags} method.
     */
    public boolean hasEventFlags() {
        return mode.eventFlagsOfs != 0 || eventFlags != 0;
    }

    /**
     * Returns event flags that are associated with the current record.
     * This method returns zero when {@link #hasEventFlags() hasEventFlags} returns {@code false}.
     * @see TimeMarkUtil
     */
    public int getEventFlags() {
        return mode.eventFlagsOfs != 0 ? intFlds[intOffset + mode.eventFlagsOfs] : eventFlags;
    }

    /**
     * Changes event flags that are associated with the current record.
     * This method does nothing when {@link #getMode() mode} does not have {@link RecordMode#hasEventFlags() event flags}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     */
    public void setEventFlags(int eventFlags) {
        if (mode.eventFlagsOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            intFlds[intOffset + mode.eventFlagsOfs] = eventFlags;
        }
    }

    /**
     * Returns {@code true} when this cursor keeps additional integer time mark with each data record.
     * Additional time mark is either provided with the data when {@link #getMode() mode} has
     * {@link RecordMode#hasTimeMark() time mark} or by the cursor owner via
     * {@link Owner#setTimeMark(int) Owner.setTimeMark} method.
     * @see TimeMarkUtil
     */
    public boolean hasTimeMark() {
        return mode.timeMarkOfs != 0 || timeMark != 0;
    }

    /**
     * Returns time mark that is associated with the current record.
     * This method returns zero when {@link #hasTimeMark() hasTimeMark} returns {@code false}.
     * @see TimeMarkUtil
     */
    public int getTimeMark() {
        return mode.timeMarkOfs != 0 ? intFlds[intOffset + mode.timeMarkOfs] : timeMark;
    }

    /**
     * Changes time mark that is associated with the current record.
     * This method does nothing when {@link #getMode() mode} does not have {@link RecordMode#hasTimeMark() time mark}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @see TimeMarkUtil
     */
    public void setTimeMark(int timeMark) {
        if (mode.timeMarkOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            intFlds[intOffset + mode.timeMarkOfs] = timeMark;
        }
    }

    /**
     * Returns {@code true} when this cursor keeps additional long event time sequence with each data record.
     * It is a shortcut to
     * <code>{@link #getMode() getMode}().{@link RecordMode#hasEventTimeSequence() hasEventTimeSequence}()</code>.
     * @see TimeSequenceUtil
     */
    public boolean hasEventTimeSequence() {
        return mode.eventTimeSequenceOfs != 0;
    }

    /**
     * Returns event time sequence that is associated with the current record.
     * This method returns zero when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @see TimeSequenceUtil
     */
    public long getEventTimeSequence() {
        return mode.eventTimeSequenceOfs != 0 ? getLongImpl(mode.eventTimeSequenceOfs) : 0;
    }

    /**
     * Returns event time seconds value that is associated with the current record.
     * This method returns zero when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @see #getEventTimeSequence()
     * @see TimeSequenceUtil
     */
    public int getEventTimeSeconds() {
        return mode.eventTimeSequenceOfs != 0 ? intFlds[intOffset + mode.eventTimeSequenceOfs] : 0;
    }

    /**
     * Returns event sequence that is associated with the current record.
     * This method returns zero when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @see #getEventTimeSequence()
     * @see TimeSequenceUtil
     */
    public int getEventSequence() {
        return mode.eventTimeSequenceOfs != 0 ? intFlds[intOffset + mode.eventTimeSequenceOfs + 1] : 0;
    }

    /**
     * Changes event time sequence that is associated with the current record.
     * This method does nothing when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @see TimeSequenceUtil
     */
    public void setEventTimeSequence(long eventTimeSequence) {
        if (mode.eventTimeSequenceOfs != 0)
            setLongImpl(mode.eventTimeSequenceOfs, eventTimeSequence);
    }

    /**
     * Changes event time seconds value that is associated with the current record.
     * This method does nothing when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @see #setEventTimeSequence(long)
     * @see TimeSequenceUtil
     */
    public void setEventTimeSeconds(int eventTimeSeconds) {
        if (mode.eventTimeSequenceOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            intFlds[intOffset + mode.eventTimeSequenceOfs] = eventTimeSeconds;
        }
    }

    /**
     * Changes event sequence that is associated with the current record.
     * This method does nothing when {@link #hasEventTimeSequence() hasEventTimeSequence} returns {@code false}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @see #setEventTimeSequence(long)
     * @see TimeSequenceUtil
     */
    public void setEventSequence(int eventSequence) {
        if (mode.eventTimeSequenceOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            intFlds[intOffset + mode.eventTimeSequenceOfs + 1] = eventSequence;
        }
    }

    /**
     * Returns {@code true} when this cursor keeps additional link to other record with each data record.
     * It is a shortcut to
     * <code>{@link #getMode() getMode}().{@link RecordMode#hasLink() hasLink}()</code>.
     */
    public boolean hasLink() {
        return mode.linkOfs != 0;
    }

    /**
     * Returns {@code true} when the current record was part of a chain unlinked with
     * {@link RecordBuffer#unlinkFrom(long) RecordBuffer.unlinkFrom(position)} method.
     * This method returns {@code false} when {@link #hasLink() hasLink} returns {@code false}.
     */
    public boolean isUnlinked() {
        return mode.linkOfs != 0 && intFlds[intOffset + mode.linkOfs] == UNLINKED;
    }

    /**
     * Changes link to the previous record in list that is associated with the current record.
     * This method does nothing when {@link #hasLink() hasLink} returns {@code false}.
     * @param position the position of the previous record.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @throws IllegalArgumentException if position does not refer to the previous position in record buffer.
     */
    public void setLinkTo(long position) {
        if (mode.linkOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            int delta = getIntPositionInternal() - (int) position;
            if (delta <= 0)
                throw new IllegalArgumentException("Should link to previous position in buffer");
            intFlds[intOffset + mode.linkOfs] = delta;
        }
    }

    /**
     * Returns {@code true} when this cursor keeps additional object attachment with each record.
     * Additional object attachment is either provided with the data when {@link #getMode() mode} has
     * {@link RecordMode#hasAttachment() attachment} or by the cursor owner via
     * {@link Owner#setAttachment(Object) Owner.setAttachment} method.
     */
    public boolean hasAttachment() {
        return mode.attachmentOfs != 0 || attachment != null;
    }

    /**
     * Returns object attachment that is associated with the current record.
     * This method returns {@code null} when {@link #hasAttachment() hasAttachment} returns {@code false}.
     */
    public Object getAttachment() {
        return mode.attachmentOfs != 0 ? objFlds[objOffset + mode.attachmentOfs] : attachment;
    }

    /**
     * Changes object attachment that is associated with the current record.
     * This method does nothing when {@link #getMode() mode} does not have {@link RecordMode#hasAttachment() attachment}.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     */
    public void setAttachment(Object attachment) {
        if (mode.attachmentOfs != 0) {
            if (readOnly)
                Throws.throwReadOnly();
            objFlds[objOffset + mode.attachmentOfs] = attachment;
        }
    }

    /**
     * Returns <code>true</code> if this cursor is in read-only mode.
     * Read-only cursor cannot be modified via {@link #setInt setInt}, {@link #setObj setObj} and
     * other modification methods.
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (mode == null || record == null) {
            sb.append("empty cursor");
        } else {
            sb.append(mode).append(" cursor ").append(record);
            sb.append(' ').append(getDecodedSymbol());
            sb.append(" @");
            formatPosition(sb, intOffset, objOffset);
            if (intFlds != null && intOffset >= 0)
                for (int i = 0; i < intCount && intOffset + i < intFlds.length; i++)
                    if (acceptField(record.getIntField(i)))
                        sb.append(' ').append(record.getIntField(i).getString(this));
            if (objFlds != null && objOffset >= 0)
                for (int i = 0; i < objCount && objOffset + i < objFlds.length; i++)
                    if (acceptField(record.getObjField(i)))
                        sb.append(' ').append(record.getObjField(i).getString(this));
        }
        if (readOnly)
            sb.append(", readOnly");
        if (hasEventFlags())
            sb.append(", eventFlags=0x").append(Integer.toHexString(getEventFlags()));
        return sb.toString();
    }

    public void setInt(int intFieldIndex, int value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (intFieldIndex < 0 || intFieldIndex >= intCount)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount);
        intFlds[intOffset + intFieldIndex] = value;
    }

    void setIntMappedImpl(DataRecord record, int intFieldIndex, int value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        intFlds[intOffset + intFieldIndex] = value;
    }

    public void setLong(int intFieldIndex, long value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (intFieldIndex < 0 || intFieldIndex >= intCount - 1)
            Throws.throwIndexOutOfBoundsException(intFieldIndex, intCount - 1);
        setLongImpl(intFieldIndex, value);
    }

    void setLongMappedImpl(DataRecord record, int intFieldIndex, long value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        setLongImpl(intFieldIndex, value);
    }

    public void setObj(int objFieldIndex, Object value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (objFieldIndex < 0 || objFieldIndex >= objCount)
            Throws.throwIndexOutOfBoundsException(objFieldIndex, objCount);
        objFlds[objOffset + objFieldIndex] = value;
    }

    void setObjMappedImpl(DataRecord record, int objFieldIndex, Object value) {
        if (readOnly)
            Throws.throwReadOnly();
        if (record != this.record)
            Throws.throwWrongRecord(this.record, record);
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        objFlds[objOffset + objFieldIndex] = value;
    }

    /**
     * Clears all data fields (ints set to 0 and objs set to null).
     * @deprecated Renamed to {@link #clearData()}
     */
    public void clearFields() {
        clearData();
    }

    public void clearData() {
        if (readOnly)
            Throws.throwReadOnly();
        clearDataInternalFrom(0, 0);
    }

    /**
     * Clears all data fields but time (ints set to 0 and objs set to null).
     */
    public void clearDataButTime() {
        if (readOnly)
            Throws.throwReadOnly();
        int iFrom = 2;
        int oFrom = 0;
        clearDataInternalFrom(iFrom, oFrom);
    }

    /**
     * Copies data from the specified iterator into this cursor overwriting everything unconditionally.
     */
    public void copyFrom(DataIterator iterator) {
        if (readOnly)
            Throws.throwReadOnly();
        for (int i = 0; i < intCount; i++)
            intFlds[intOffset + i] = iterator.nextIntField();
        for (int i = 0; i < objCount; i++)
            objFlds[objOffset + i] = iterator.nextObjField();
    }

    /**
     * Copies data and extra values from the specified cursor into this cursor overwriting everything unconditionally.
     * The minimal subset of fields based on current {@link #getMode() mode} and {@code from} mode is copied.
     * All values are copied when both modes are {@link RecordMode#DATA DATA}, only time if at least one
     * of them is {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}, and nothing is copied
     * if at least one of them is {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} (without time).
     * Extra values (like {@link RecordCursor#getEventTimeSequence() time sequence},
     *{@link RecordCursor#getTimeMark() time mark}, and {@link RecordCursor#getAttachment() attachment})
     * are copied when supported in both specified from and in this cursor's modes.
     *
     * <p>This method also copies all extra information ({@link RecordMode#hasEventFlags() event flags},
     * {@link RecordMode#hasTimeMark() time marks}, {@link RecordMode#hasEventTimeSequence() time sequence},
     * and {@link RecordMode#hasAttachment() attachment}) that can be
     * stored under this buffer's mode (note that {@link RecordMode#hasLink() links} are not copied),
     * including cursor-local event flags, time mark and attachment that can be set via its owner's
     * {@link Owner#setEventFlags(int) Owner.setEventFlags},
     * {@link Owner#setTimeMark(int) Owner.setTimeMark}, and
     * {@link Owner#setAttachment(Object) Owner.setAttachment} methods.
     *
     * <p>Symbol and record are not updated nor checked. Thus, method can be used to copy data between different
     * symbols of the same record and/or between different records with the same fields. It is caller's responsibility
     * to ensure that this operation makes sense.
     *
     * @param from the cursor to copy from.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @see #copyDataFrom(RecordCursor)
     */
    public void copyFrom(RecordCursor from) {
        if (readOnly)
            Throws.throwReadOnly();
        int iCount = Math.min(intCount, from.intCount);
        int oCount = Math.min(objCount, from.objCount);
        from.copyDataInternalTo(iCount, oCount, intFlds, intOffset, objFlds, objOffset);
        from.copyExtraInternalTo(mode, intFlds, intOffset, objFlds, objOffset);
    }

    /**
     * Copies data from the specified {@link RecordCursor}.
     * If the specified cursor has mode that does not have full data
     * ({@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION} or {@link RecordMode#SUBSCRIPTION}),
     * then the missing data fields are cleared.
     *
     * <p>Extra information from the cursor (like event time marks, flags, etc) is <b>NOT</b> copied.
     *
     * <p>Symbol and record are not updated nor checked. Thus, method can be used to copy data between different
     * symbols of the same record and/or between different records with the same fields. It is caller's responsibility
     * to ensure that this operation makes sense.
     *
     * @param from the cursor to copy data from.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @throws IllegalArgumentException if the cursor has too many int/obj fields to copy into this one.
     * @see RecordBuffer#addDataAndCompactIfNeeded(RecordCursor)
     */
    public void copyDataFrom(RecordCursor from) {
        if (readOnly)
            Throws.throwReadOnly();
        int iCount = from.intCount;
        int oCount = from.objCount;
        if (iCount > intCount || oCount > objCount)
            throw new IllegalArgumentException("too many incoming fields");
        from.copyDataInternalTo(iCount, oCount, intFlds, intOffset, objFlds, objOffset);
        clearDataInternalFrom(iCount, oCount);
    }

    /**
     * Updates data from the specified cursor into this cursor, checking each field with
     * {@link DataIntField#equals(int,int)} or {@link DataObjField#equals(Object,Object)} respectively.
     * The minimal subset of fields based on current {@link #getMode() mode} and {@code from} mode is updated.
     * All values are updated when both modes are {@link RecordMode#DATA DATA}, only time if at least one
     * of them is {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}, and nothing is updated
     * if at least one of them is {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} (without time).
     * Symbol is not updated nor checked.
     *
     * <p>Extra information from the cursor (like event time marks, flags, etc) is <b>NOT</b> updated.
     *
     * @return <code>true</code> if anything was changed.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @throws IllegalArgumentException when from cursor has different record.
     * @deprecated Renamed to {@link #updateDataFrom(RecordCursor)}
     */
    public boolean updateFrom(RecordCursor from) {
        return updateDataFrom(from);
    }

    /**
     * Updates data from the specified cursor into this cursor, checking each field with
     * {@link DataIntField#equals(int,int)} or {@link DataObjField#equals(Object,Object)} respectively.
     * The minimal subset of fields based on current {@link #getMode() mode} and {@code from} mode is updated.
     * All values are updated when both modes are {@link RecordMode#DATA DATA}, only time if at least one
     * of them is {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}, and nothing is updated
     * if at least one of them is {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} (without time).
     * Symbol is not updated nor checked.
     *
     * <p>Extra information from the cursor (like event time marks, flags, etc) is <b>NOT</b> updated.
     *
     * @return <code>true</code> if anything was changed.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @throws IllegalArgumentException when from cursor has different record.
     */
    public boolean updateDataFrom(RecordCursor from) {
        if (readOnly)
            Throws.throwReadOnly();
        if (record != from.record)
            Throws.throwWrongRecord(this.record, from.record);
        boolean changed = false;
        int iCount = Math.min(intCount, from.intCount);
        for (int i = 0; i < iCount; i++) {
            int v = from.intFlds[from.intOffset + i];
            if (!record.getIntField(i).equals(intFlds[intOffset + i], v)) {
                intFlds[intOffset + i] = v;
                changed = true;
            }
        }
        int oCount = Math.min(objCount, from.objCount);
        for (int i = 0; i < oCount; i++) {
            Object v = from.objFlds[from.objOffset + i];
            if (!record.getObjField(i).equals(objFlds[objOffset + i], v)) {
                objFlds[objOffset + i] = v;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Returns <code>true</code> when data contents of this cursor are equal (identity-wise) to the contents
     * of the other cursor.
     * The minimal subset of fields based on current {@link #getMode() mode} and {@code from} mode is checked.
     * All values are checked when both modes are {@link RecordMode#DATA DATA}, only time if at least one
     * of them is {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}, and nothing is checked
     * (always returns {@code true})
     * if at least one of them is {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} (without time).
     * Symbol is not checked.
     *
     * <p>Extra information from the cursor (like event time marks, flags, etc) is <b>NOT</b> checked.
     *
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @throws IllegalArgumentException when other cursor has different record.
     */
    public boolean isDataIdenticalTo(RecordCursor other) {
        if (record != other.record)
            Throws.throwWrongRecord(this.record, other.record);
        int iCount = Math.min(intCount, other.intCount);
        for (int i = 0; i < iCount; i++)
            if (intFlds[intOffset + i] != other.intFlds[other.intOffset + i])
                return false;
        int oCount = Math.min(objCount, other.objCount);
        for (int i = 0; i < oCount; i++)
            if (objFlds[objOffset + i] != other.objFlds[other.objOffset + i])
                return false;
        return true;
    }

    /**
     * Reads field values from the specified data input.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @deprecated Use {@link com.devexperts.qd.qtp.BinaryQTPParser} class
     */
    public void readFrom(DataInput in) throws IOException {
        if (readOnly)
            Throws.throwReadOnly();
        for (int i = 0; i < intCount; i++)
            intFlds[intOffset + i] = record.getIntField(i).readInt(in);
        for (int i = 0; i < objCount; i++)
            objFlds[objOffset + i] = record.getObjField(i).readObj(in);
    }

    /**
     * Reads data field values from the specified buffered input.
     * @throws IllegalStateException if the cursor is {@link #isReadOnly() read-only}.
     * @deprecated Use {@link com.devexperts.qd.qtp.BinaryQTPParser} class
     */
    public void readDataFrom(BufferedInput in) throws IOException {
        if (readOnly)
            Throws.throwReadOnly();
        record.readFields(in, this);
    }

    /**
     * Examines this cursor into the specified data visitor,
     * passing record, symbol and all field values.
     * Note, that when visitor implements {@link RecordSink} interface this method invokes
     * {@link RecordSink#append(RecordCursor) append(this)}.
     *
     * @param visitor data visitor.
     * @return {@code true} when data could not be examined completely because visitor does not have capacity
     *       (need to examine again), or {@code false} otherwise (everything was examined).
     * @throws IllegalStateException if this cursor mode does not {@link RecordMode#hasData() have data}.
     */
    public boolean examineData(DataVisitor visitor) {
        if (record == null)
            return false;
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        if (!visitor.hasCapacity())
            return true;
        if (visitor instanceof RecordSink)
            ((RecordSink) visitor).append(this);
        else {
            visitor.visitRecord(record, cipher, symbol);
            for (int i = 0; i < intCount; i++)
                visitor.visitIntField(record.getIntField(i), intFlds[intOffset + i]);
            for (int i = 0; i < objCount; i++)
                visitor.visitObjField(record.getObjField(i), objFlds[objOffset + i]);
        }
        return false;
    }

    /**
     * Examines this cursor into the specified data visitor,
     * passing record, symbol and time (if this cursor {@link #hasTime() has time}).
     * Note, that when visitor implements {@link RecordSink} interface this method invokes
     * {@link RecordSink#append(RecordCursor) append(this)}.
     *
     * @param visitor data visitor.
     * @return {@code true} when data could not be examined completely because visitor does not have capacity
     *       (need to examine again), or {@code false} otherwise (everything was examined).
     * @throws IllegalStateException if this cursor mode {@link RecordMode#hasData() has data}.
     */
    public boolean examineSubscription(SubscriptionVisitor visitor) {
        if (record == null)
            return false;
        if (mode.hasData())
            Throws.throwWrongMode(mode);
        if (!visitor.hasCapacity())
            return true;
        if (visitor instanceof RecordSink)
            ((RecordSink) visitor).append(this);
        else
            visitor.visitRecord(record, cipher, symbol, hasTime() ? getTime() : 0L);
        return false;
    }

    //----------------------- deprecated API -----------------------

    /**
     * @deprecated This method will not be public in the future versions.
     * Use one of <code>RecordCursor.allocate(...)</code> or <code>RecordCursor.allocateOwner(...)</code> methods.
     */
    public RecordCursor() {
        this(false);
    }

    /**
     * @deprecated This method will be removed in the future versions.
     * Use one of <code>RecordCursor.allocateOwner(...)</code> to get owner and use Owner's
     * <code>setXXX(...)</code> methods to configure the cursor as needed.
     */
    public void setAs(DataRecord record, int cipher, String symbol,
        int[] int_flds, int int_offset, Object[] obj_flds, int obj_offset, boolean writable)
    {
        setReadOnlyInternal(!writable);
        setRecordInternal(record, RecordMode.DATA);
        setSymbolInternal(cipher, symbol);
        setArraysInternal(int_flds, obj_flds);
        setOffsetsInternal(int_offset, obj_offset);
        rangeCheckInternal();
    }

    /**
     * @deprecated This method will not be public in the future versions.
     * Use {@link RecordCursor.Owner#reset RecordCursor.Owner.reset()}.
     */
    public void reset() {
        resetInternal();
    }

    /**
     * Returns position of this cursor. This value is only valid when
     * the cursor was acquired via one of the following {@link RecordBuffer} methods:
     * {@link RecordBuffer#next next},
     * {@link RecordBuffer#current current},
     * {@link RecordBuffer#add(DataRecord, int, String) add(record,cipher,symbol)},
     * {@link RecordBuffer#add(RecordCursor) add(cursor)},
     * {@link RecordBuffer#cursorAt cursorAt},
     * {@link RecordBuffer#writeCursorAt writeCursorAt}
     * @return position.
     */
    public long getPosition() {
        return makePosition(getIntPositionInternal(), getObjPositionInternal());
    }

    //----------------------- internal API (package-private) -----------------------

    static RecordCursor allocateInternal(DataRecord record, RecordMode mode) {
        RecordCursor cursor = new RecordCursor(false, mode);
        cursor.setRecordInternal(record, mode);
        int intCount = mode.extraIntCount + mode.intFieldCount(record);
        int objCount = mode.extraObjCount + mode.objFieldCount(record);
        cursor.setArraysInternal(
            intCount == 0 ? null : new int[intCount],
            objCount == 0 ? null : new Object[objCount]);
        cursor.setOffsetsInternal(mode.extraIntCount, mode.extraObjCount);
        return cursor;
    }

    RecordCursor(boolean readOnly) {
        this(readOnly, RecordMode.DATA);
    }

    RecordCursor(boolean readOnly, RecordMode mode) {
        this.readOnly = readOnly;
        this.mode = Objects.requireNonNull(mode);
    }

    void setReadOnlyInternal(boolean read_only) {
        this.readOnly = read_only;
    }

    void setRecordInternal(DataRecord record, RecordMode mode) {
        if (this.record != record || this.mode != mode) {
            this.record = Objects.requireNonNull(record);
            this.mode = Objects.requireNonNull(mode);
            this.intCount = mode.intFieldCount(record);
            this.objCount = mode.objFieldCount(record);
        }
    }

    void setSymbolInternal(int cipher, String symbol) {
        this.cipher = cipher;
        this.symbol = symbol;
    }

    void setArraysInternal(int[] intFlds, Object[] objFlds) {
        this.intFlds = intFlds;
        this.objFlds = objFlds;
    }

    void ensureCapacityInternal() {
        int isize = intFlds == null ? 0 : intFlds.length;
        if (intCount > isize)
            intFlds = new int[Math.max(intCount, isize * 2)];
        int osize = objFlds == null ? 0 : objFlds.length;
        if (objCount > osize)
            objFlds = new Object[Math.max(objCount, osize * 2)];
    }

    void setOffsetsInternal(int intOffset, int objOffset) {
        this.intOffset = intOffset;
        this.objOffset = objOffset;
    }

    void setAsInternal(RecordCursor other) {
        this.readOnly = other.readOnly;
        this.record = other.record;
        this.mode = other.mode;
        this.cipher = other.cipher;
        this.symbol = other.symbol;
        this.intFlds = other.intFlds;
        this.objFlds = other.objFlds;
        this.intOffset = other.intOffset;
        this.objOffset = other.objOffset;
        this.intCount = other.intCount;
        this.objCount = other.objCount;
        this.eventFlags = other.eventFlags;
        this.timeMark = other.timeMark;
        this.attachment = other.attachment;
    }

    void getIntsImpl(int iFrom, int[] to, int offset, int length) {
        System.arraycopy(intFlds, intOffset + iFrom, to, offset, length);
    }

    void getObjsImpl(int oFrom, Object[] to, int offset, int length) {
        System.arraycopy(objFlds, objOffset + oFrom, to, offset, length);
    }

    void clearDataInternalFrom(int iFrom, int oFrom) {
        for (int i = iFrom; i < intCount; i++)
            intFlds[intOffset + i] = 0;
        for (int i = oFrom; i < objCount; i++)
            objFlds[objOffset + i] = null;
    }

    void copyDataInternalTo(int iCount, int oCount, int[] toInts, int iOffset, Object[] toObjs, int oOffset) {
        if (iCount > 0)
            getIntsImpl(0, toInts, iOffset, iCount);
        if (oCount > 0)
            getObjsImpl(0, toObjs, oOffset, oCount);
    }

    void copyExtraInternalTo(RecordMode mode,
        int[] toInts, int iOffset, Object[] toObjs, int oOffset)
    {
        if (mode.eventFlagsOfs != 0)
            toInts[iOffset + mode.eventFlagsOfs] = getEventFlags();
        if (mode.timeMarkOfs != 0)
            toInts[iOffset + mode.timeMarkOfs] = getTimeMark();
        if (mode.eventTimeSequenceOfs != 0) {
            if (this.mode.eventTimeSequenceOfs != 0) {
                toInts[iOffset + mode.eventTimeSequenceOfs] = intFlds[intOffset + this.mode.eventTimeSequenceOfs];
                toInts[iOffset + mode.eventTimeSequenceOfs + 1] = intFlds[intOffset + this.mode.eventTimeSequenceOfs + 1];
            } else {
                toInts[iOffset + mode.eventTimeSequenceOfs] = 0;
                toInts[iOffset + mode.eventTimeSequenceOfs + 1] = 0;
            }
        }
        if (mode.attachmentOfs != 0)
            toObjs[oOffset + mode.attachmentOfs] = getAttachment();
    }

    // This method is used by deprecated methods only
    void rangeCheckInternal() {
        if (intCount > 0 && (intOffset | intFlds.length - intOffset - intCount) < 0)
            throw new IndexOutOfBoundsException("intFlds");
        if (objCount > 0 && (objOffset | objFlds.length - objOffset - objCount) < 0)
            throw new IndexOutOfBoundsException("objFlds");
    }

    // prevents further access to this cursor, while leaving all other values intact.
    void resetAccessInternal() {
        intOffset = objOffset = -2000000000;
    }

    // prevents further access to this cursor, while leaving all other values intact.
    void resetAccessInternal(int intLimit, int objLimit) {
        if (intOffset >= intLimit || objOffset >= objLimit)
            resetAccessInternal();
    }

    void resetInternal() {
        readOnly = false;
        record = null;
        cipher = 0;
        symbol = null;
        intFlds = null;
        objFlds = null;
        intOffset = 0;
        objOffset = 0;
        intCount = 0;
        objCount = 0;
        eventFlags = 0;
        timeMark = 0;
        attachment = null;
    }

    int getIntPositionInternal() {
        return intOffset - mode.intBufOffset;
    }

    int getObjPositionInternal() {
        return objOffset - mode.objBufOffset;
    }

    static long makePosition(int iPos, int oPos) {
        return ((long) oPos << 32) | iPos;
    }

    static void formatPosition(StringBuilder sb, int iPos, int oPos) {
        sb.append(Integer.toHexString(oPos));
        sb.append(':');
        sb.append(Integer.toHexString(iPos));
    }

    //----------------------- private helper methods -----------------------

    private long getLongImpl(int index) {
        int hi = intFlds[intOffset + index];
        int lo = intFlds[intOffset + index + 1];
        return (((long) hi) << 32) | ((long) lo & 0xFFFFFFFFL);
    }

    private void setLongImpl(int index, long value) {
        if (readOnly)
            Throws.throwReadOnly();
        intFlds[intOffset + index] = (int) (value >> 32);
        intFlds[intOffset + index + 1] = (int) value;
    }

    private boolean acceptField(DataField f) {
        return !(f instanceof VoidIntField);
    }
}
