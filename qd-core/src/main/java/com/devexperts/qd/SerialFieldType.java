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
package com.devexperts.qd;

import com.devexperts.qd.kit.ByteArrayField;
import com.devexperts.qd.kit.CompactCharField;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DateField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.LongField;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.PlainIntField;
import com.devexperts.qd.kit.SequenceField;
import com.devexperts.qd.kit.ShortStringField;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.kit.TimeMillisField;
import com.devexperts.qd.kit.TimeSecondsField;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.kit.WideDecimalField;

import static com.devexperts.qd.SerialFieldType.Bits.FLAG_CUSTOM_OBJECT;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_DATE;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_DECIMAL;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_LONG;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_SEQUENCE;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_SERIAL_OBJECT;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_SHORT_STRING;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_STRING;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_TIME_MILLIS;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_TIME_NANOS;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_TIME_SECONDS;
import static com.devexperts.qd.SerialFieldType.Bits.FLAG_WIDE_DECIMAL;
import static com.devexperts.qd.SerialFieldType.Bits.ID_BYTE;
import static com.devexperts.qd.SerialFieldType.Bits.ID_BYTE_ARRAY;
import static com.devexperts.qd.SerialFieldType.Bits.ID_COMPACT_INT;
import static com.devexperts.qd.SerialFieldType.Bits.ID_INT;
import static com.devexperts.qd.SerialFieldType.Bits.ID_SHORT;
import static com.devexperts.qd.SerialFieldType.Bits.ID_UTF_CHAR;
import static com.devexperts.qd.SerialFieldType.Bits.ID_UTF_CHAR_ARRAY;
import static com.devexperts.qd.SerialFieldType.Bits.ID_VOID;
import static com.devexperts.qd.SerialFieldType.Bits.REPRESENTATION_MASK;
import static com.devexperts.qd.SerialFieldType.Bits.SERIAL_TYPE_MASK;

/**
 * Describes serial type of {@link DataField data fields} that are transferred via QTP.
 * It describes the serialized form of the field in QTP protocol with
 * enough detail to skip the serialized value of the field if necessary.
 */
public final class SerialFieldType {
    // Standard instances
    public static final SerialFieldType VOID = new SerialFieldType(ID_VOID, "VOID");
    public static final SerialFieldType BYTE = new SerialFieldType(ID_BYTE, "BYTE");
    public static final SerialFieldType UTF_CHAR = new SerialFieldType(ID_UTF_CHAR, "UTF_CHAR");
    public static final SerialFieldType SHORT = new SerialFieldType(ID_SHORT, "SHORT");
    public static final SerialFieldType INT = new SerialFieldType(ID_INT, "INT");
    public static final SerialFieldType COMPACT_INT = new SerialFieldType(ID_COMPACT_INT, "COMPACT_INT");
    public static final SerialFieldType BYTE_ARRAY = new SerialFieldType(ID_BYTE_ARRAY, "BYTE_ARRAY");
    public static final SerialFieldType UTF_CHAR_ARRAY = new SerialFieldType(ID_UTF_CHAR_ARRAY, "UTF_CHAR_ARRAY");

    public static final SerialFieldType DECIMAL = new SerialFieldType(ID_COMPACT_INT | FLAG_DECIMAL, "DECIMAL");
    public static final SerialFieldType SHORT_STRING = new SerialFieldType(ID_COMPACT_INT | FLAG_SHORT_STRING, "SHORT_STRING");
    public static final SerialFieldType TIME_SECONDS = new SerialFieldType(ID_COMPACT_INT | FLAG_TIME_SECONDS, "TIME_SECONDS");
    public static final SerialFieldType TIME_MILLIS = new SerialFieldType(ID_COMPACT_INT | FLAG_TIME_MILLIS, "TIME_MILLIS");
    public static final SerialFieldType TIME_NANOS = new SerialFieldType(ID_COMPACT_INT | FLAG_TIME_NANOS, "TIME_NANOS"); // Reserved
    /** @deprecated Use {@link #TIME_SECONDS} instead. */
    @Deprecated
    public static final SerialFieldType TIME = TIME_SECONDS;
    public static final SerialFieldType SEQUENCE = new SerialFieldType(ID_COMPACT_INT | FLAG_SEQUENCE, "SEQUENCE");
    public static final SerialFieldType DATE = new SerialFieldType(ID_COMPACT_INT | FLAG_DATE, "DATE");
    public static final SerialFieldType LONG = new SerialFieldType(ID_COMPACT_INT | FLAG_LONG, "LONG");
    public static final SerialFieldType WIDE_DECIMAL = new SerialFieldType(ID_COMPACT_INT | FLAG_WIDE_DECIMAL, "WIDE_DECIMAL");
    public static final SerialFieldType STRING = new SerialFieldType(ID_BYTE_ARRAY | FLAG_STRING, "STRING");
    public static final SerialFieldType CUSTOM_OBJECT = new SerialFieldType(ID_BYTE_ARRAY | FLAG_CUSTOM_OBJECT, "CUSTOM_OBJECT");
    public static final SerialFieldType SERIAL_OBJECT = new SerialFieldType(ID_BYTE_ARRAY | FLAG_SERIAL_OBJECT, "SERIAL_OBJECT");

    // Named instances (use same ids as standard ones)
    // used by forNamedField method

    private static final SerialFieldType ID = LONG.withName("ID");
    private static final SerialFieldType MMID = SHORT_STRING.withName("MMID");
    private static final SerialFieldType EXCHANGE = UTF_CHAR.withName("EXCHANGE");
    private static final SerialFieldType PRICE = DECIMAL.withName("PRICE");
    private static final SerialFieldType SIZE = COMPACT_INT.withName("SIZE");
    private static final SerialFieldType VOLUME = DECIMAL.withName("VOLUME");
    private static final SerialFieldType COUNT = DECIMAL.withName("COUNT");
    private static final SerialFieldType VOLATILITY = DECIMAL.withName("VOLATILITY");
    private static final SerialFieldType OPEN_INTEREST = COMPACT_INT.withName("OPEN_INTEREST");
    private static final SerialFieldType BOOLEAN = COMPACT_INT.withName("BOOLEAN");
    private static final SerialFieldType TICK = COMPACT_INT.withName("TICK");
    private static final SerialFieldType SALE_CONDITIONS = SHORT_STRING.withName("SALE_CONDITIONS");
    private static final SerialFieldType SALE_FLAGS = COMPACT_INT.withName("SALE_FLAGS");
    private static final SerialFieldType PROFILE_FLAGS = COMPACT_INT.withName("PROFILE_FLAGS");

    private static final SerialFieldType BID_TIME = TIME_SECONDS.withName("BID_TIME");
    private static final SerialFieldType ASK_TIME = TIME_SECONDS.withName("ASK_TIME");
    private static final SerialFieldType TRADE_TIME = TIME_SECONDS.withName("TRADE_TIME");
    private static final SerialFieldType CANDLE_TIME = TIME_SECONDS.withName("CANDLE_TIME");

    private static final SerialFieldType QUOTE_PRICE = DECIMAL.withName("QUOTE_PRICE");
    private static final SerialFieldType TRADE_PRICE = DECIMAL.withName("TRADE_PRICE");
    private static final SerialFieldType SUMMARY_PRICE = DECIMAL.withName("SUMMARY_PRICE");
    private static final SerialFieldType CANDLE_PRICE = DECIMAL.withName("CANDLE_PRICE");

    private static final SerialFieldType CANDLE_OPEN_INTEREST = DECIMAL.withName("CANDLE_OPEN_INTEREST");

    // ----------- instance fields -----------

    private final String name;
    private final int id;
    private final boolean isObject;
    private final boolean isLong;

    private SerialFieldType(int id, String name) {
        this.id = id;
        this.name = name;
        this.isObject = (id & SERIAL_TYPE_MASK) == ID_BYTE_ARRAY || (id & SERIAL_TYPE_MASK) == ID_UTF_CHAR_ARRAY;
        this.isLong = (id & REPRESENTATION_MASK) == FLAG_WIDE_DECIMAL || (id & REPRESENTATION_MASK) == FLAG_LONG ||
            (id & REPRESENTATION_MASK) == FLAG_TIME_MILLIS;
        if (isLong && isObject)
            throw new IllegalArgumentException("conflicting type");
    }

    public boolean isDecimal() {
        return (id & REPRESENTATION_MASK) == FLAG_DECIMAL;
    }

    public boolean isTime() {
        int repr = id & REPRESENTATION_MASK;
        return repr == FLAG_TIME_SECONDS || repr == FLAG_TIME_MILLIS || repr == FLAG_TIME_NANOS;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SerialFieldType))
            return false;
        SerialFieldType other = (SerialFieldType) o;
        return id == other.id && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + id;
    }

    public boolean isLong() {
        return isLong;
    }

    public boolean isObject() {
        return isObject;
    }

    public boolean hasSameSerialTypeAs(SerialFieldType other) {
        return (other.id & SERIAL_TYPE_MASK) == (this.id & SERIAL_TYPE_MASK);
    }

    public boolean hasSameRepresentationAs(SerialFieldType other) {
        return (other.id & REPRESENTATION_MASK) == (this.id & REPRESENTATION_MASK);
    }

    /**
     * Creates the same type as this one, but with a different name.
     */
    public SerialFieldType withName(String name) {
        return new SerialFieldType(id, name);
    }

    /**
     * Returns a more specific serial type that shall be used for the field with the specified name.
     * For {@link #COMPACT_INT}, {@link #DECIMAL}, {@link #SHORT_STRING}, and {@link #TIME_SECONDS} base types this
     * method looks at the suffix of the name, for other base types just the base type itself is returned.
     */
    public SerialFieldType forNamedField(String name) {
        SerialFieldType type = forNamedFieldImpl(name);
        if (!hasSameSerialTypeAs(type))
            throw new RuntimeException("Invalid type " + type + " for named field " + name + " with base type " + this);
        return type;
    }

    private SerialFieldType forNamedFieldImpl(String name) {
        // ----------- time fields -----------
        if (this == COMPACT_INT || this == TIME_SECONDS) {
            if (name.endsWith("Bid.Time"))
                return BID_TIME;
            if (name.endsWith("Ask.Time"))
                return ASK_TIME;
            if (name.endsWith("Last.Time") || name.endsWith("TradeHistory.Time") || name.endsWith("TimeAndSale.Time"))
                return TRADE_TIME;
            if ((name.startsWith("Trade.") || name.startsWith("Candle.")) && name.endsWith(".Time"))
                return CANDLE_TIME;
        }
        if (this == COMPACT_INT && name.endsWith("Time"))
            return TIME_SECONDS;
        // ----------- price fields -----------
        if (this == DECIMAL)  {
            if (name.endsWith("Bid.Price") || name.endsWith("Ask.Price"))
                return QUOTE_PRICE;
            if (name.endsWith("Last.Price") ||
                name.endsWith("TradeHistory.Price") || name.endsWith("TradeHistory.Bid") || name.endsWith("TradeHistory.Ask") ||
                name.endsWith("TimeAndSale.Price") || name.endsWith("TimeAndSale.Bid") || name.endsWith("TimeAndSale.Ask"))
            {
                return TRADE_PRICE;
            }
            if (name.endsWith("High.Price") || name.endsWith("Low.Price") || name.endsWith("Open.Price") || name.endsWith("Close.Price"))
                return SUMMARY_PRICE;
            if ((name.startsWith("Trade.") || name.startsWith("Candle.")) &&
                (name.endsWith("Open") || name.endsWith("High") || name.endsWith("Low") || name.endsWith("Close") || name.endsWith("VWAP")))
            {
                return CANDLE_PRICE;
            }
            if (name.endsWith("Price") || name.endsWith("Bid") || name.endsWith("Ask") || name.endsWith("VWAP") ||
                name.endsWith("Open") || name.endsWith("High") || name.endsWith("Low") || name.endsWith("Close"))
            {
                return PRICE;
            }
        }
        // ----------- other -----------
        if (this == COMPACT_INT && (name.endsWith("Date") || name.endsWith("DayId")))
            return DATE;
        if (this == COMPACT_INT && name.endsWith("Sequence"))
            return SEQUENCE;
        if (this == LONG && name.endsWith("Id")) // Except DayId which is handled above
            return ID;
        if (this == SHORT_STRING && name.endsWith("MMID"))
            return MMID;
        if (this == UTF_CHAR && name.endsWith("Exchange"))
            return EXCHANGE;
        if (this == COMPACT_INT && name.endsWith("Size"))
            return SIZE;
        if (this == DECIMAL && name.endsWith("Volume"))
            return VOLUME;
        if (this == DECIMAL && name.endsWith("Count"))
            return COUNT;
        if (this == DECIMAL && name.endsWith("Volatility"))
            return VOLATILITY;
        if (this == COMPACT_INT && name.endsWith("OpenInterest"))
            return OPEN_INTEREST;
        if (this == DECIMAL && name.startsWith("Trade.") && name.endsWith("OpenInterest"))
            return CANDLE_OPEN_INTEREST;
        if (this == SHORT_STRING && name.endsWith("SaleConditions"))
            return SALE_CONDITIONS;
        if (this == COMPACT_INT && (name.endsWith("IsIndex") || name.endsWith("IsEuropean") || name.endsWith("IsMarginable")))
            return BOOLEAN;
        if (this == COMPACT_INT && name.endsWith("Tick"))
            return TICK;
        if (this == COMPACT_INT && name.endsWith("Sale.Flags"))
            return SALE_FLAGS;
        if (this == COMPACT_INT && name.endsWith("Profile.Flags"))
            return PROFILE_FLAGS;
        return this;
    }

    public DataIntField createDefaultIntInstance(int index, String name) {
        // try to find corresponding serial type ignoring any unknown flags.
        switch (id & SERIAL_TYPE_MASK) {
        case ID_VOID:
            return new VoidIntField(index, name);
        case ID_UTF_CHAR:
            return new CompactCharField(index, name, this);
        case ID_INT:
            return new PlainIntField(index, name, this);
        case ID_COMPACT_INT:
            switch (id & REPRESENTATION_MASK) {
            case FLAG_DECIMAL:
                return new DecimalField(index, name, this);
            case FLAG_SHORT_STRING:
                return new ShortStringField(index, name, this);
            case FLAG_TIME_SECONDS:
                return new TimeSecondsField(index, name);
            case FLAG_TIME_MILLIS:
                return new TimeMillisField(index, name);
            case FLAG_SEQUENCE:
                return new SequenceField(index, name);
            case FLAG_DATE:
                return new DateField(index, name);
            case FLAG_LONG:
                return new LongField(index, name);
            case FLAG_WIDE_DECIMAL:
                return new WideDecimalField(index, name);
            default:
                return new CompactIntField(index, name, this);
            }
        default:
            return null;
        }
    }

    public DataObjField createDefaultObjInstance(int index, String name) {
        // try to find corresponding serial type ignoring any unknown flags.
        switch (id & SERIAL_TYPE_MASK) {
        case ID_BYTE_ARRAY:
            switch (id & REPRESENTATION_MASK) {
            case FLAG_STRING:
                return new StringField(index, name, true);
            case FLAG_SERIAL_OBJECT:
                return new MarshalledObjField(index, name);
            default:
                return new ByteArrayField(index, name);
            }
        case ID_UTF_CHAR_ARRAY:
            return new StringField(index, name);
        default:
            return null;
        }
    }

    /**
     * Bit patterns for serial field type ids.
     */
    public static class Bits {
        private Bits() {} // do not create

        /**
         * Masks representation bits of type id (encodes details of representation of the field value in the code).
         */
        public static final int REPRESENTATION_MASK = 0x0f0;

        /**
         * This mask should be used to check if two types are equal in terms of their serial representations.
         * 4 bits in {@link #REPRESENTATION_MASK} are used for representation information.
         */
        public static final int SERIAL_TYPE_MASK = ~REPRESENTATION_MASK;

        /**
         * Min type id that is supported now for plain field types.
         * Type ids beyond {@code MIN_TYPE_ID} to {@link #MAX_TYPE_ID} can have additional information attached
         * to them in the future, and the code should not attempt to parse their descriptions without knowing them.
         */
        public static final int MIN_TYPE_ID = 0x000;

        /**
         * Max type id that is supported now for plain field types.
         * Type ids beyond {@link #MIN_TYPE_ID} to {@code MAX_TYPE_ID} can have additional information attached
         * to them in the future, and the code should not attempt to parse their descriptions without knowing them.
         */
        public static final int MAX_TYPE_ID = 0x0ff;

        public static final int ID_VOID = 0;
        public static final int ID_BYTE = 1;
        public static final int ID_UTF_CHAR = 2;
        public static final int ID_SHORT = 3;
        public static final int ID_INT = 4;
        // ids 5,6,7 are reserved for future use
        public static final int ID_COMPACT_INT = 8;
        public static final int ID_BYTE_ARRAY = 9;
        public static final int ID_UTF_CHAR_ARRAY = 10;
        // ids 11-15 are reserved for future use as array of short, array of int, etc

        public static final int FLAG_INT = 0x00; // plain int as int field
        public static final int FLAG_DECIMAL = 0x10; // decimal representation as int field
        public static final int FLAG_SHORT_STRING = 0x20; // short (up to 4-character) string representation as int field
        public static final int FLAG_TIME_SECONDS = 0x30; // time in seconds as integer field
        @Deprecated
        public static final int FLAG_TIME = FLAG_TIME_SECONDS;
        public static final int FLAG_SEQUENCE = 0x40; // sequence in this integer fields (with top 10 bits representing millis)
        public static final int FLAG_DATE = 0x50; // day id in this integer field
        public static final int FLAG_LONG = 0x60; // plain long as two int fields
        public static final int FLAG_WIDE_DECIMAL = 0x70; // WideDecimal representation as long field
        public static final int FLAG_STRING = 0x80; // String representation as byte array (for ID_BYTE_ARRAY)
        public static final int FLAG_TIME_MILLIS = 0x90; // time in millis as long field
        public static final int FLAG_TIME_NANOS = 0xa0; // Reserved for future use: time in nanos as long field
        public static final int FLAG_CUSTOM_OBJECT = 0xe0; // custom serialized object as byte array (for ID_BYTE_ARRAY)
        public static final int FLAG_SERIAL_OBJECT = 0xf0; // serialized object as byte array (for ID_BYTE_ARRAY)
    }
}
