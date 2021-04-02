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
package com.devexperts.qd.test;

import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.LongField;
import com.devexperts.qd.kit.PlainIntField;
import com.devexperts.qd.kit.TimeMillisField;
import com.devexperts.qd.kit.TimeSecondsField;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.kit.WideDecimalField;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.BinaryQTPComposer;
import com.devexperts.qd.qtp.BinaryQTPParser;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.util.TimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class FieldAdaptationTest {
    static final String RECORD = "Test";
    static final String SYMBOL = "TEST";

    // Variable names are fitted into 4 characters for better alignment
    static final BiFunction<Integer, String, DataIntField> PINT = PlainIntField::new;
    static final BiFunction<Integer, String, DataIntField> CINT = CompactIntField::new;
    static final BiFunction<Integer, String, DataIntField> TINY = DecimalField::new;
    static final BiFunction<Integer, String, DataIntField> WIDE = WideDecimalField::new;
    static final BiFunction<Integer, String, DataIntField> LONG = LongField::new;
    static final BiFunction<Integer, String, DataIntField> SECS = TimeSecondsField::new;
    static final BiFunction<Integer, String, DataIntField> MILS = TimeMillisField::new;

    static final String TIME_SECONDS = TimeFormat.DEFAULT.withTimeZone().format(1_000);
    static final String TIME_MILLIS = TimeFormat.DEFAULT.withTimeZone().withMillis().format(1_000);

    private final DataIntField fromField;
    private final String fromValue;
    private final DataIntField toField;
    private final String toValue;

    @Parameterized.Parameters(name="({index}) {4},from={2},to={3}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            { PINT, PINT, 1, 1, "int->int" },
            { PINT, CINT, 1, 1, "int->compact int" },
            { PINT, TINY, 1, 1, "int->decimal" },
            { PINT, WIDE, 1, 1, "int->wide decimal" },
            { PINT, LONG, 1, 1, "int->long" },

            { PINT, PINT, -1, -1, "int->int" },
            { PINT, CINT, -1, -1, "int->compact int" },
            { PINT, TINY, -1, -1, "int->decimal" },
            { PINT, WIDE, -1, -1, "int->wide decimal" },
            { PINT, LONG, -1, -1, "int->long" },

            { CINT, PINT, 123456, 123456, "compact int->int" },
            { CINT, CINT, 123456, 123456, "compact int->compact int" },
            { CINT, TINY, 123456, 123456, "compact int->decimal" },
            { CINT, WIDE, 123456, 123456, "compact int->wide decimal" },
            { CINT, LONG, 123456, 123456, "compact int->long" },

            { TINY, PINT, "1", 1, "decimal->int" },
            { TINY, CINT, "1", 1, "decimal->compact int" },
            { TINY, TINY, "1", "1", "decimal->decimal" },
            { TINY, WIDE, "1", "1", "decimal->wide decimal" },
            { TINY, LONG, "1", 1L, "decimal->long" },

            { TINY, PINT, "-1", -1, "decimal->int" },
            { TINY, CINT, "-1", -1, "decimal->compact int" },
            { TINY, TINY, "-1", "-1", "decimal->decimal" },
            { TINY, WIDE, "-1", "-1", "decimal->wide decimal" },
            { TINY, LONG, "-1", -1L, "decimal->long" },

            { TINY, PINT, "NaN", 0, "decimal->int" },
            { TINY, CINT, "NaN", 0, "decimal->compact int" },
            { TINY, TINY, "NaN", "NaN", "decimal->decimal" },
            { TINY, WIDE, "NaN", "NaN", "decimal->wide decimal" },
            { TINY, LONG, "NaN", 0L, "decimal->long" },

            // Tiny Decimal Infinity conversion
            { TINY, PINT, "Infinity", Integer.MAX_VALUE, "decimal->int" },
            { TINY, CINT, "Infinity", Integer.MAX_VALUE, "decimal->compact int" },
            { TINY, TINY, "Infinity", "Infinity", "decimal->decimal" },
            { TINY, WIDE, "Infinity", "Infinity", "decimal->wide decimal" },
            { TINY, LONG, "Infinity", Long.MAX_VALUE, "decimal->long" },

            // Tiny Decimal -Infinity conversion
            { TINY, PINT, "-Infinity", Integer.MIN_VALUE, "decimal->int" },
            { TINY, CINT, "-Infinity", Integer.MIN_VALUE, "decimal->compact int" },
            { TINY, TINY, "-Infinity", "-Infinity", "decimal->decimal" },
            { TINY, WIDE, "-Infinity", "-Infinity", "decimal->wide decimal" },
            { TINY, LONG, "-Infinity", Long.MIN_VALUE, "decimal->long" },

            { WIDE, PINT, "1", "1", "wide decimal->int" },
            { WIDE, CINT, "1", "1", "wide decimal->compact int" },
            { WIDE, TINY, "1", "1", "wide decimal->decimal" },
            { WIDE, WIDE, "1", "1", "wide decimal->wide decimal" },
            { WIDE, LONG, "1", "1", "wide decimal->long" },

            { WIDE, PINT, "-1", "-1", "wide decimal->int" },
            { WIDE, CINT, "-1", "-1", "wide decimal->compact int" },
            { WIDE, TINY, "-1", "-1", "wide decimal->decimal" },
            { WIDE, WIDE, "-1", "-1", "wide decimal->wide decimal" },
            { WIDE, LONG, "-1", "-1", "wide decimal->long" },

            { WIDE, PINT, "NaN", 0, "wide decimal->int" },
            { WIDE, CINT, "NaN", 0, "wide decimal->compact int" },
            { WIDE, TINY, "NaN", "NaN", "wide decimal->decimal" },
            { WIDE, WIDE, "NaN", "NaN", "wide decimal->wide decimal" },
            { WIDE, LONG, "NaN", 0L, "wide decimal->long" },

            // Wide Decimal Infinity conversion
            // Note that (int) Long.MAX_VALUE == -1
            { WIDE, PINT, "Infinity", (int) Long.MAX_VALUE, "wide decimal->int" },
            { WIDE, CINT, "Infinity", (int) Long.MAX_VALUE, "wide decimal->compact int" },
            { WIDE, TINY, "Infinity", "Infinity", "wide decimal->decimal" },
            { WIDE, WIDE, "Infinity", "Infinity", "wide decimal->wide decimal" },
            { WIDE, LONG, "Infinity", Long.toString(Long.MAX_VALUE), "wide decimal->long" },

            // Wide Decimal -Infinity conversion
            // Note that (int) Long.MIN_VALUE == 0
            { WIDE, PINT, "-Infinity", (int) Long.MIN_VALUE, "wide decimal->int" },
            { WIDE, CINT, "-Infinity", (int) Long.MIN_VALUE, "wide decimal->compact int" },
            { WIDE, TINY, "-Infinity", "-Infinity", "wide decimal->decimal" },
            { WIDE, WIDE, "-Infinity", "-Infinity", "wide decimal->wide decimal" },
            { WIDE, LONG, "-Infinity", Long.MIN_VALUE, "wide decimal->long" },

            // Truncation & Precision Loss
            { WIDE, PINT, "1.23456789", "1", "wide decimal->int" },
            { WIDE, CINT, "1.23456789", "1", "wide decimal->compact int" },
            { WIDE, TINY, "1.23456789", "1.2345679", "wide decimal->decimal" },
            { WIDE, WIDE, "1.23456789", "1.23456789", "wide decimal->wide decimal" },
            { WIDE, LONG, "1.23456789", "1", "wide decimal->long" },

            { LONG, PINT, 1L, 1, "long->int" },
            { LONG, CINT, 1L, 1, "long->compact int" },
            { LONG, TINY, 1L, "1", "long->decimal" },
            { LONG, WIDE, 1L, "1", "long->wide decimal" },
            { LONG, LONG, 1L, 1L, "long->long" },

            { LONG, PINT, -1L, -1, "long->int" },
            { LONG, CINT, -1L, -1, "long->compact int" },
            { LONG, TINY, -1L, "-1", "long->decimal" },
            { LONG, WIDE, -1L, "-1", "long->wide decimal" },
            { LONG, LONG, -1L, -1L, "long->long" },

            // Truncation & Precision Loss
            { LONG, PINT, Integer.MAX_VALUE + 1L, Integer.MIN_VALUE, "long->int" },
            { LONG, CINT, Integer.MAX_VALUE + 1L, Integer.MIN_VALUE, "long->compact int" },
            { LONG, TINY, Integer.MAX_VALUE + 1L, "2147483600", "long->decimal" },
            { LONG, WIDE, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 1L, "long->wide decimal" },
            { LONG, LONG, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 1L, "long->long" },

            // Time
            { SECS, PINT, "0", "0", "time seconds->int" },
            { SECS, LONG, "0", "0", "time seconds->long" },
            { SECS, PINT, "19700101-000001+0000", "1", "time seconds->int" },
            { SECS, LONG, "19700101-000001+0000", "1", "time seconds->long" },
            { LONG, SECS, 0L, "0", "long->time seconds" },
            { LONG, SECS, 1L, TIME_SECONDS, "long->time seconds" },

            { MILS, PINT, "0", "0", "time millis->long" },
            { MILS, LONG, "0", "0", "time millis->long" },
            { MILS, PINT, "19700101-000001.000+0000", "1000", "time millis->int" },
            { MILS, LONG, "19700101-000001.000+0000", "1000", "time millis->long" },
            { LONG, MILS, 0L, "0", "long->time millis" },
            { LONG, MILS, 1000L, TIME_MILLIS, "long->time millis" },

            { SECS, MILS, "0", "0", "time seconds->time millis" },
            { SECS, MILS, TIME_SECONDS, TIME_MILLIS, "time seconds->time millis" },
            { MILS, SECS, TIME_MILLIS, TIME_SECONDS, "time millis->time seconds" },
            { MILS, SECS, TIME_MILLIS, TIME_SECONDS, "time millis->time seconds" },
        });
    }

    public FieldAdaptationTest(
        BiFunction<Integer, String, DataIntField> fromField, BiFunction<Integer, String, DataIntField> toField,
        Object fromValue, Object toValue, @SuppressWarnings("unused") String description)
    {
        this.fromField = field(fromField);
        this.toField = field(toField);
        this.fromValue = fromValue.toString();
        this.toValue = toValue.toString();
    }

    @Test
    public void testFieldConversion() {
        DefaultRecord fromRecord = new DefaultRecord(0, RECORD, false, new DataIntField[] {
            fromField, new VoidIntField(1, RECORD + ".void") }, null);  // use extra void field for long values
        DataScheme fromScheme = new TestDataScheme(1, 0, TestDataScheme.Type.SIMPLE, fromRecord);

        DefaultRecord toRecord = new DefaultRecord(0, RECORD, false, new DataIntField[] {
            toField, new VoidIntField(1, RECORD + ".void")}, null); // use extra void field for long values
        DataScheme toScheme = new TestDataScheme(1, 0, TestDataScheme.Type.SIMPLE, toRecord);

        RecordBuffer fromBuffer = new RecordBuffer();
        RecordCursor fromCursor = fromBuffer.add(fromRecord, fromScheme.getCodec().encode(SYMBOL), SYMBOL);
        fromField.setString(fromCursor, fromValue);

        ChunkedOutput output = new ChunkedOutput();
        BinaryQTPComposer composer = new BinaryQTPComposer(fromScheme, true);
        composer.setOutput(output);
        assertFalse(composer.visitData(fromBuffer, MessageType.STREAM_DATA));

        // copy composed chunks to chunked input
        ChunkedInput input = new ChunkedInput();
        input.addAllToInput(output.getOutput(this), this);

        // parser from this input
        BinaryQTPParser parser = new BinaryQTPParser(toScheme);
        parser.setInput(input);

        parser.parse(new MessageConsumerAdapter() {
            @Override
            public void handleCorruptedStream() {
                fail("handleCorruptedStream");
            }

            @Override
            public void handleCorruptedMessage(int messageTypeId) {
                fail("handleCorruptedMessage " + messageTypeId);
            }

            @Override
            public void handleUnknownMessage(int messageTypeId) {
                fail("handleUnknownMessage " + messageTypeId);
            }

            @Override
            public void processStreamData(DataIterator iterator) {
                RecordBuffer toBuffer = (RecordBuffer) iterator;
                RecordCursor toCursor = toBuffer.next();
                assertNotNull("no data", toCursor);
                assertEquals("wrong record", toRecord, toCursor.getRecord());
                assertEquals("wrong symbol", SYMBOL, toScheme.getCodec().decode(toCursor.getCipher(), toCursor.getSymbol()));
                assertEquals("wrong value", toValue, toField.getString(toCursor));
                assertNull("extra data", toBuffer.next());
            }
        });
    }

    private static DataIntField field(BiFunction<Integer, String, DataIntField> constructor) {
        return constructor.apply(0, RECORD + ".field");
    }
}
