/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.devexperts.io.*;
import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.util.Decimal;
import com.devexperts.qd.util.TimeSequenceUtil;

import static com.devexperts.qd.SerialFieldType.Bits.*;

public class BinaryRecordDesc {
    /*
     * DESC integer bits layout
     *
     *    4 bits  4 bits       24 bits
     *   +-------+-------+-----------------+
     *   |  FLD  |  SER  |      index      |
     *   +-------+-------+-----------------+
     * 32        28      24     .....    0
     *
     *  FLD   -- defines the general operation and flips between fast/slow path
     *  SER   -- defines serialization format in stream.
     *  index -- the index of this field in record.
     *
     */

    protected static final int SER_SHIFT = 24;
    protected static final int SER_MASK = 0xf; // after shift

    protected static final int SER_OTHER = 0; // never appears in desc array, used for special DESC_VOID and DESC_INVALID

    protected static final int SER_BYTE = 1;  // not actually used, but still supported
    protected static final int SER_UTF_CHAR = 2;
    protected static final int SER_SHORT = 3;  // not actually used, but still supported
    protected static final int SER_INT = 4;  // not actually used, but still supported
    protected static final int SER_COMPACT_INT = 5;

    protected static final int SER_UTF_CHAR_ARRAY_STRING = 6;

    // below constants are all represented as "byte array" in stream, but on read produce different kinds of objects
    // they are also relevant on write, since they define "instanceof" chain that coerces object into byte stream
    protected static final int SER_BYTE_ARRAY = 7;
    protected static final int SER_UTF_STRING = 8;
    protected static final int SER_MARSHALLED = 9;
    protected static final int SER_PLAIN_OBJECT = 10; // deprecated, but still supported

    protected static final int FLD_SHIFT = 28;

    protected static final int FLD_SKIP = 0; // just skip the field that was read (never used for Write)
    protected static final int FLD_INT = 1; // regular int field (w/o conversion)
    protected static final int FLD_OBJ = 2; // regular obj field
    protected static final int FLD_DECIMAL_TO_INT = 3; // conversion decimal->int
    protected static final int FLD_INT_TO_DECIMAL = 4; // conversion int->decimal
    protected static final int FLD_EVENT_TIME = 5;     // RecordCursor.get/setEventTime (does not have index)
    protected static final int FLD_EVENT_SEQUENCE = 6; // RecordCursor.get/setEventSequence (does not have index)

    protected static final int INDEX_MASK = (1 << SER_SHIFT) - 1;

    // predefined desc values with dummy index (just in case somebody uses index, to make sure he gets IOOBE)
    protected static final int DESC_VOID = (-1 & INDEX_MASK) | (SER_OTHER << SER_SHIFT);
    protected static final int DESC_EVENT_TIME = (-2 & INDEX_MASK) | (SER_COMPACT_INT << SER_SHIFT) | (FLD_EVENT_TIME << FLD_SHIFT);
    protected static final int DESC_EVENT_SEQUENCE = (-3 & INDEX_MASK) | (SER_COMPACT_INT << SER_SHIFT) | (FLD_EVENT_SEQUENCE << FLD_SHIFT);

    // Conversion direction for change of representation.
    protected static final int DIR_NONE = 0;
    protected static final int DIR_READ = 1;
    protected static final int DIR_WRITE = 2;

    // --------------------------- protected fields ---------------------------

    // NOTE: All fields are finals, so this class is safe for unsafe publication

    protected final boolean empty;
    protected final DataRecord record;
    protected final String[] names;
    protected final int[] types; // original SerialFieldType.getId()
    protected final int[] desc; // packed DESC bits according to documentation above
    protected final int nDesc; // number of fields from desc array in a regular record
    protected final int nDescEventFields; // number of builtin EventXXX fields from desc array (used for events marked with REMOVE_EVENT flag)

    // --------------------------- constructors ---------------------------

    protected BinaryRecordDesc(BinaryRecordDesc desc) {
        this.empty = desc.empty;
        this.record = desc.record;
        this.names = desc.names;
        this.types = desc.types;
        this.desc = desc.desc;
        this.nDesc = desc.nDesc;
        this.nDescEventFields = desc.nDescEventFields;
    }

    protected BinaryRecordDesc(DataRecord record, boolean eventTimeSequence) throws InvalidDescException {
        int iFlds = record.getIntFieldCount();
        int oFlds = record.getObjFieldCount();
        int nFlds = (eventTimeSequence ? 2 : 0) + iFlds + oFlds;
        String[] names = new String[nFlds];
        int[] types = new int[nFlds];
        int[] desc = new int[nFlds];
        int nDesc = 0;
        if (eventTimeSequence) {
            names[nDesc] = BuiltinFields.EVENT_TIME_FIELD_NAME;
            types[nDesc] = SerialFieldType.TIME.getId();
            desc[nDesc++] = DESC_EVENT_TIME;
            names[nDesc] = BuiltinFields.EVENT_SEQUENCE_FIELD_NAME;
            types[nDesc] = SerialFieldType.SEQUENCE.getId();
            desc[nDesc++] = DESC_EVENT_SEQUENCE;
        }
        for (int i = 0; i < iFlds; i++) {
            DataIntField f = record.getIntField(i);
            int d = field2Desc(names[i], f.getSerialType().getId(), f, DIR_NONE);
            if (d == DESC_VOID)
                continue; // don't write it do desc array
            names[nDesc] = f.getPropertyName();
            types[nDesc] = f.getSerialType().getId();
            desc[nDesc++] = d;
        }
        for (int i = 0; i < oFlds; i++) {
            DataObjField f = record.getObjField(i);
            int d = field2Desc(names[i], f.getSerialType().getId(), f, DIR_NONE);
            if (d == DESC_VOID)
                continue; // don't write it do desc array
            names[nDesc] = f.getPropertyName();
            types[nDesc] = f.getSerialType().getId();
            desc[nDesc++] = d;
        }
        // initialize
        this.empty = false;
        this.record = record;
        this.names = names;
        this.types = types;
        this.desc = desc;
        this.nDesc = nDesc;
        this.nDescEventFields = countEventFields();
    }

    // cnvRead == true  conversion flags are set for reading specified types and converting them from types[i] to record field serial type
    // cnvRead == false conversion flags are set for writing specified types and converting them from record field serial type to types[i]
    protected BinaryRecordDesc(DataRecord record, int nFld, String[] namesIn, int[] typesIn,
        boolean eventTimeSequence, int dir) throws InvalidDescException
    {
        String[] names = namesIn;
        int[] types = typesIn;
        boolean clonedNamesAndTypes = false;
        int[] desc = new int[nFld];

        // Support built-in EventTime & EventSequence fields if needed
        int nDesc = 0;
        int i = 0;
        if (eventTimeSequence && nFld >= 1 &&
            names[0].equals(BuiltinFields.EVENT_TIME_FIELD_NAME) &&
            types[0] == (ID_COMPACT_INT | FLAG_TIME))
        {
            i++;
            desc[nDesc++] = DESC_EVENT_TIME;
            if (nFld >= 2 &&
                names[1].equals(BuiltinFields.EVENT_SEQUENCE_FIELD_NAME) &&
                types[1] == (ID_COMPACT_INT | FLAG_SEQUENCE))
            {
                i++;
                desc[nDesc++] = DESC_EVENT_SEQUENCE;
            }
        }

        // The rest (regular) fields
        for (; i < nFld; i++) {
            int d = field2Desc(names[i], types[i], record == null ? null : record.findFieldByName(names[i]), dir);
            if (d == DESC_VOID)
                continue; // VOID fields are not written nor read
            if (dir == DIR_WRITE && (d >> FLD_SHIFT) == FLD_SKIP)
                continue; // don't write missing fields
            // Shift names and types arrays to account for skipped / void
            if (nDesc != i && !clonedNamesAndTypes) {
                // clone names and types array first time this is needed
                names = names.clone();
                types = types.clone();
                clonedNamesAndTypes = true;
            }
            names[nDesc] = names[i];
            types[nDesc] = types[i];
            desc[nDesc++] = d;
        }
        // initialize
        this.empty = nFld == 0;
        this.record = record;
        this.names = names;
        this.types = types;
        this.desc = desc;
        this.nDesc = nDesc;
        this.nDescEventFields = countEventFields();
    }

    private int countEventFields() {
        int i = 0;
        while (i < nDesc && names[i].startsWith(BuiltinFields.EVENT_FIELDS_PREFIX))
            i++;
        return i;
    }

    // --------------------------- instance methods - general ---------------------------

    /**
     * Returns actual DataRecord that shall be used for subscription.
     * It returns <code>null</code> if this description does not refer to any record.
     */
    public DataRecord getRecord() {
        return record;
    }

    /**
     * Returns true when this descriptor was originally empty (did not contain any fields) as opposed to
     * having an empty intersection with fields in the scheme. For subscription, this is a special signal
     * to send all fields known in the scheme (when the receiver on the other side is schema-less).
     */
    public boolean isEmpty() {
        return empty;
    }

    // --------------------------- instance methods - reading ---------------------------

    /**
     * Reads record from the specified input and adds it to the specified cursor.
     * @return the cursor to the record that was just read or {@code null} if nothing was read (the record was skipped).
     */
    public RecordCursor readRecord(BufferedInput msg, RecordBuffer buffer, int cipher, String symbol, int eventFlags) throws IOException {
        RecordCursor cur = null;
        if (record != null) {
            cur = buffer.add(record, cipher, symbol);
            cur.setEventFlags(eventFlags);
        }
        if (EventFlag.REMOVE_EVENT.in(eventFlags)) {
            readRemoveEventFields(msg, cur);
        } else
            readFields(msg, cur, nDesc);
        return cur;
    }

    // Read only event fields and time
    private void readRemoveEventFields(BufferedInput msg, RecordCursor cur) throws IOException {
        readFields(msg, cur, nDescEventFields);
        long time = readTime(msg);
        if (cur != null)
            cur.setTime(time);
    }

    protected long readTime(BufferedInput msg) throws IOException {
        return msg.readCompactLong();
    }

    protected void readFields(BufferedInput msg, RecordCursor cur, int nDesc) throws IOException {
        for (int i = 0; i < nDesc; i++) {
            beforeField(msg);
            int iVal = 0;
            Object oVal = null;
            int d = desc[i];
            switch ((d >> SER_SHIFT) & SER_MASK) {
            case SER_BYTE: // not actually used, but still supported
                iVal = msg.readByte();
                break;
            case SER_UTF_CHAR:
                iVal = msg.readUTFChar();
                break;
            case SER_SHORT: // not actually used, but still supported
                iVal = msg.readShort();
                break;
            case SER_INT: // not actually used, but still supported
                iVal = msg.readInt();
                break;
            case SER_COMPACT_INT:
                iVal = msg.readCompactInt();
                break;
            case SER_UTF_CHAR_ARRAY_STRING: // deprecated, but still supported
                oVal = IOUtil.readCharArrayString(msg);
                break;
            case SER_BYTE_ARRAY:
                oVal = msg.readByteArray();
                break;
            case SER_UTF_STRING:
                oVal = msg.readUTFString();
                break;
            case SER_MARSHALLED:
                oVal = msg.readMarshalled(Marshaller.SERIALIZATION);
                break;
            case SER_PLAIN_OBJECT: // deprecated, but still supported
                oVal = msg.readObject();
                break;
            default:
                throw new AssertionError();
            }
            // figure out what to do
            switch (d >>> FLD_SHIFT) {
            case FLD_SKIP:
                break; // just skip
            case FLD_INT:
                setIntValue(cur, d & INDEX_MASK, iVal, msg);
                break;
            case FLD_OBJ: // regular obj field without conversion
                setObjValue(cur, d & INDEX_MASK, oVal, msg);
                break;
            case FLD_DECIMAL_TO_INT:
                setIntValue(cur, d & INDEX_MASK, (int) Decimal.toDouble(iVal), msg);
                break;
            case FLD_INT_TO_DECIMAL:
                setIntValue(cur, d & INDEX_MASK, Decimal.compose(iVal), msg);
                break;
            case FLD_EVENT_TIME:
                cur.setEventTimeSeconds(iVal);
                break;
            case FLD_EVENT_SEQUENCE:
                cur.setEventSequence(iVal);
                break;
            default:
                throw new AssertionError();
            }
        }
    }

    protected void beforeField(BufferedInput msg) {}

    protected void setIntValue(RecordCursor cur, int index, int value, BufferedInput msg) {
        cur.setInt(index, value);
    }

    protected void setObjValue(RecordCursor cur, int index, Object value, BufferedInput msg) {
        cur.setObj(index, value);
    }

    // --------------------------- instance methods - writing ---------------------------

    /**
     * Writers record to the specified output  from the specified cursor.
     * Note, that specified eventFlags are used, not the flags from the cursor.
     */
    public void writeRecord(BufferedOutput msg, RecordCursor cur, int eventFlags, long eventTimeSequence) throws IOException {
        if (EventFlag.REMOVE_EVENT.in(eventFlags)) {
            // Write only special fields and time
            writeFields(msg, cur, nDescEventFields, eventTimeSequence);
            writeTime(msg, cur.getTime());
        } else
            writeFields(msg, cur, nDesc, eventTimeSequence);
    }

    private void writeTime(BufferedOutput msg, long time) throws IOException {
        msg.writeCompactLong(time);
    }

    private void writeFields(BufferedOutput msg, RecordCursor cur, int nDesc, long eventTimeSequence) throws IOException {
        for (int i = 0; i < nDesc; i++) {
            int d = desc[i];
            int iVal = 0;
            Object oVal = null;
            switch (d >>> FLD_SHIFT) {
            case FLD_INT:
                iVal = cur.getInt(d & INDEX_MASK);
                break;
            case FLD_OBJ: // regular obj field without conversion
                oVal = cur.getObj(d & INDEX_MASK);
                break;
            case FLD_DECIMAL_TO_INT:
                iVal = (int) Decimal.toDouble(cur.getInt(d & INDEX_MASK));
                break;
            case FLD_INT_TO_DECIMAL:
                iVal = Decimal.compose(cur.getInt(d & INDEX_MASK));
                break;
            case FLD_EVENT_TIME:
                iVal = TimeSequenceUtil.getTimeSecondsFromTimeSequence(eventTimeSequence);
                break;
            case FLD_EVENT_SEQUENCE:
                iVal = TimeSequenceUtil.getSequenceFromTimeSequence(eventTimeSequence);
                break;
            default:
                throw new AssertionError();
            }
            switch ((d >> SER_SHIFT) & SER_MASK) {
            case SER_BYTE:
                msg.writeByte(iVal);
                break;
            case SER_UTF_CHAR:
                msg.writeUTFChar(iVal);
                break;
            case SER_SHORT:
                msg.writeShort(iVal);
                break;
            case SER_INT:
                msg.writeInt(iVal);
                break;
            case SER_COMPACT_INT:
                msg.writeCompactInt(iVal);
                break;
            case SER_UTF_CHAR_ARRAY_STRING: // deprecated, but still supported
                // See StringField.writeObj
                if (oVal == null || oVal instanceof String)
                    IOUtil.writeCharArray(msg, (String) oVal);
                else if (oVal instanceof char[])
                    IOUtil.writeCharArray(msg, (char[]) oVal);
                else if (oVal instanceof byte[])
                    IOUtil.writeCharArray(msg, new String((byte[]) oVal, StandardCharsets.UTF_8));
                else
                    IOUtil.writeCharArray(msg, oVal.toString());
                break;
            case SER_BYTE_ARRAY:
                // ByteArrayField.writeObj (toByteArray)
                if (oVal == null || oVal instanceof byte[])
                    msg.writeByteArray((byte[]) oVal);
                else if (oVal instanceof String)
                    msg.writeUTFString((String) oVal);
                else if (oVal instanceof char[])
                    msg.writeUTFString(new String((char[]) oVal));
                else
                    msg.writeObject(oVal);
                break;
            case SER_UTF_STRING:
                // See StringField.writeObj
                if (oVal == null || oVal instanceof String)
                    msg.writeUTFString((String) oVal);
                else if (oVal instanceof char[])
                    msg.writeUTFString(new String((char[]) oVal));
                else if (oVal instanceof byte[])
                    msg.writeByteArray((byte[]) oVal);
                else
                    msg.writeUTFString(oVal.toString());
                break;
            case SER_MARSHALLED: // writeObject method understands marshalled object
            case SER_PLAIN_OBJECT: // deprecated, but still supported
                msg.writeObject(oVal);
                break;
            default:
                throw new AssertionError();
            }
        }
    }

    // --------------------------- static ---------------------------

    static class InvalidDescException extends Exception {
        InvalidDescException(String name, int type) {
            super("Unsupported type 0x" + Integer.toHexString(type) + " for field '" + name + "'");
        }
    }

    private static int field2Desc(String name, int type, DataField f, int dir) throws InvalidDescException {
        int ser;
        switch (type & SERIAL_TYPE_MASK) {
        case ID_VOID:
            return DESC_VOID; // Void fields are not serialized at all
        case ID_BYTE:
            ser = SER_BYTE;
            break;
        case ID_UTF_CHAR:
            ser = SER_UTF_CHAR;
            break;
        case ID_SHORT:
            ser = SER_SHORT;
            break;
        case ID_INT:
            ser = SER_INT;
            break;
        case ID_COMPACT_INT:
            ser = SER_COMPACT_INT;
            break;
        case ID_BYTE_ARRAY:
            // let's see what type of the field we have
            if (f instanceof ByteArrayField) {
                ser = SER_BYTE_ARRAY;
            } else if (f instanceof StringField) {
                ser = SER_UTF_STRING;
            } else if (f instanceof PlainObjField) {
                ser = SER_PLAIN_OBJECT; // deprecated, though
            } else if (f instanceof MarshalledObjField) {
                ser = SER_MARSHALLED;
            } else {
                // f == null or f != null, but we don't support this field type -- we will skip this field
                return (SER_BYTE_ARRAY << SER_SHIFT) | (FLD_SKIP << FLD_SHIFT) | (-1 & INDEX_MASK);
            }
            break;
        case ID_UTF_CHAR_ARRAY:
            ser = SER_UTF_CHAR_ARRAY_STRING;
            break;
        default:
            throw new InvalidDescException(name, type); // unsupported serial field type
        }
        int fld;
        if (f instanceof DataIntField) {
            // integer fields might need conversion after read or before write
            switch (dir) {
            case DIR_READ:
                fld = getIntConverterType(type, f.getSerialType().getId());
                break;
            case DIR_WRITE:
                fld = getIntConverterType(f.getSerialType().getId(), type);
                break;
            default:
                fld = FLD_INT;
                assert getIntConverterType(f.getSerialType().getId(), type) == FLD_INT;
                break;
            }
        } else if (f instanceof DataObjField)
            fld = FLD_OBJ;
        else
            return (ser << SER_SHIFT) | (FLD_SKIP << FLD_SHIFT) | (-1 & INDEX_MASK); // skip missing / bad field
        return (ser << SER_SHIFT) | (fld << FLD_SHIFT) | f.getIndex();
    }

    private static int getIntConverterType(int fromId, int toId) {
        if ((fromId & REPRESENTATION_MASK) == FLAG_DECIMAL && (toId & REPRESENTATION_MASK) == 0)
            return FLD_DECIMAL_TO_INT;
        if ((fromId & REPRESENTATION_MASK) == 0 && (toId & REPRESENTATION_MASK) == FLAG_DECIMAL)
            return FLD_INT_TO_DECIMAL;
        return FLD_INT;
    }

}
