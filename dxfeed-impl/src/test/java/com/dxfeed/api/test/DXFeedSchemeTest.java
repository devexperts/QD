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
package com.dxfeed.api.test;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.dxfeed.api.impl.DXFeedScheme;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFactory;
import com.dxfeed.api.impl.SchemeBuilder;
import com.dxfeed.api.impl.SchemeFieldTime;
import com.dxfeed.api.impl.SchemeProperties;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static com.devexperts.qd.SerialFieldType.COMPACT_INT;
import static com.devexperts.qd.SerialFieldType.CUSTOM_OBJECT;
import static com.devexperts.qd.SerialFieldType.DECIMAL;
import static com.devexperts.qd.SerialFieldType.INT;
import static com.devexperts.qd.SerialFieldType.SERIAL_OBJECT;
import static com.devexperts.qd.SerialFieldType.SHORT_STRING;
import static com.devexperts.qd.SerialFieldType.STRING;
import static com.devexperts.qd.SerialFieldType.VOID;
import static com.dxfeed.api.impl.SchemeFieldTime.COMMON_FIELD;
import static com.dxfeed.api.impl.SchemeFieldTime.FIRST_TIME_INT_FIELD;
import static com.dxfeed.api.impl.SchemeFieldTime.SECOND_TIME_INT_FIELD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DXFeedSchemeTest {

    private static class TestScheme extends DXFeedScheme {
        public TestScheme(Iterable<? extends EventDelegateFactory> eventDelegateFactories) {
            super(eventDelegateFactories, new SchemeProperties(new Properties()));
        }
    }

    private static class TestFactory extends EventDelegateFactory {
        private final List<Field> fields = new ArrayList<Field>();

        TestFactory() {}

        public void buildScheme(SchemeBuilder builder) {
            for (Field f : fields) {
                String[] s = f.name.split("\\.", 2);
                builder.addRequiredField(s[0], s[1], f.type, f.time);
            }
        }

        public TestFactory field(String name, SerialFieldType type, SchemeFieldTime time) {
            fields.add(new Field(name, type, time));
            return this;
        }

        public Collection<EventDelegate<?>> createDelegates(DataRecord record) {
            return Collections.emptyList();
        }
    }

    private static TestFactory factory() {
        return new TestFactory();
    }

    private static class Field {
        public final String name;
        public final SerialFieldType type;
        public final SchemeFieldTime time;

        public Field(String name, SerialFieldType type, SchemeFieldTime time) {
            this.name = name;
            this.type = type;
            this.time = time;
        }
    }

    private void checkFail(String errorMsg, TestFactory... factories) {
        try {
            new TestScheme(Arrays.asList(factories));
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().equals(errorMsg))
                fail();
            return;
        }
        fail();
    }

    private void check(String expected, TestFactory... factories) {
        assertEquals(expected, printScheme(new TestScheme(Arrays.asList(factories))));
    }

    private static String printScheme(DataScheme scheme) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scheme.getRecordCount(); i++) {
            DataRecord record = scheme.getRecord(i);
            if (sb.length() > 0)
                sb.append("; ");
            sb.append(record.getName());
            if (record.hasTime())
                sb.append("+T");
            sb.append("{");
            for (int j = 0; j < record.getIntFieldCount(); j++) {
                printField(record.getIntField(j), sb);
            }
            for (int j = 0; j < record.getObjFieldCount(); j++) {
                printField(record.getObjField(j), sb);
            }
            sb.append("}");
        }
        return sb.toString();
    }

    private static void printField(DataField field, StringBuilder sb) {
        if (sb.charAt(sb.length() - 1) != '{')
            sb.append(", ");
        sb.append(field.getLocalName()).append(":").append(field.getSerialType());
    }

    // ========== Tests ==========

    private final static TestFactory FACTORY_1 = factory()
        .field("Zzz.CompactIntField", COMPACT_INT, COMMON_FIELD)
        .field("Zzz.DecimalField", DECIMAL, COMMON_FIELD)
        .field("Zzz.StringField", STRING, COMMON_FIELD)
        .field("BlahBlahBlah.Foo", VOID, COMMON_FIELD)
        .field("TimedRec.T1", INT, FIRST_TIME_INT_FIELD)
        .field("TimedRec.T2", SHORT_STRING, SECOND_TIME_INT_FIELD);

    private final static TestFactory FACTORY_2 = factory()
        .field("Zzz.StringField", STRING, COMMON_FIELD)
        .field("Zzz.DecimalField", DECIMAL, COMMON_FIELD)
        .field("Zzz.ObjField", CUSTOM_OBJECT, COMMON_FIELD)
        .field("Zzz.VoidField", VOID, COMMON_FIELD)
        .field("TimedRec.T1", INT, FIRST_TIME_INT_FIELD);

    private final static TestFactory FACTORY_3 = factory()
        .field("TimedRec.T1", INT, COMMON_FIELD)
        .field("TimedRec.T2", SHORT_STRING, COMMON_FIELD)
        .field("TimedRec.Zzz", COMPACT_INT, COMMON_FIELD)
        .field("Xyz.Field", VOID, SECOND_TIME_INT_FIELD);

    @Test
    public void testSimpleScheme() {
        check("Zzz{CompactIntField:COMPACT_INT, DecimalField:DECIMAL, StringField:STRING}; " +
            "BlahBlahBlah{Foo:VOID}; " +
            "TimedRec+T{T1:INT, T2:SHORT_STRING}", FACTORY_1);
        check("Zzz{DecimalField:DECIMAL, VoidField:VOID, StringField:STRING, ObjField:BYTE_ARRAY}; " +
            "TimedRec+T{T1:INT, $VoidTimeField:VOID}", FACTORY_2);
        check("TimedRec{T1:INT, T2:SHORT_STRING, Zzz:COMPACT_INT}; Xyz+T{$VoidTimeField:VOID, Field:VOID}", FACTORY_3);

        check("Zzz{CompactIntField:COMPACT_INT, DecimalField:DECIMAL, VoidField:VOID, " +
            "StringField:STRING, ObjField:BYTE_ARRAY}; " +
            "BlahBlahBlah{Foo:VOID}; TimedRec+T{T1:INT, T2:SHORT_STRING}", FACTORY_1, FACTORY_2);
        check("Zzz{DecimalField:DECIMAL, VoidField:VOID, StringField:STRING, ObjField:BYTE_ARRAY}; " +
            "TimedRec+T{T1:INT, $VoidTimeField:VOID, T2:SHORT_STRING, Zzz:COMPACT_INT}; " +
            "Xyz+T{$VoidTimeField:VOID, Field:VOID}", FACTORY_2, FACTORY_3);
        check("TimedRec+T{T1:INT, T2:SHORT_STRING, Zzz:COMPACT_INT}; " +
            "Xyz+T{$VoidTimeField:VOID, Field:VOID}; " +
            "Zzz{CompactIntField:COMPACT_INT, DecimalField:DECIMAL, StringField:STRING}; " +
            "BlahBlahBlah{Foo:VOID}", FACTORY_3, FACTORY_1);

        check("Zzz{CompactIntField:COMPACT_INT, DecimalField:DECIMAL, VoidField:VOID, " +
            "StringField:STRING, ObjField:BYTE_ARRAY}; " +
            "BlahBlahBlah{Foo:VOID}; " +
            "TimedRec+T{T1:INT, T2:SHORT_STRING, Zzz:COMPACT_INT}; " +
            "Xyz+T{$VoidTimeField:VOID, Field:VOID}", FACTORY_1, FACTORY_2, FACTORY_3);
    }

    @Test
    public void testGoodTimedFields() {
        check("R+T{T1:SHORT_STRING, $VoidTimeField:VOID}", factory().field("R.T1", SHORT_STRING, FIRST_TIME_INT_FIELD));
        check("R+T{$VoidTimeField:VOID, T2:DECIMAL}", factory().field("R.T2", DECIMAL, SECOND_TIME_INT_FIELD));

        check("R+T{T1:SHORT_STRING, T2:DECIMAL, Int:COMPACT_INT, Obj:SERIAL_OBJECT}; Zzz{Haba:VOID}",
            factory()
                .field("R.T1", SHORT_STRING, FIRST_TIME_INT_FIELD)
                .field("R.T2", DECIMAL, SECOND_TIME_INT_FIELD)
                .field("R.Int", COMPACT_INT, COMMON_FIELD)
                .field("R.Obj", SERIAL_OBJECT, COMMON_FIELD),
            factory()
                .field("R.T2", DECIMAL, SECOND_TIME_INT_FIELD)
                .field("Zzz.Haba", VOID, COMMON_FIELD)
        );

        check("R+T{T:SHORT_STRING, $VoidTimeField:VOID}",
            factory().field("R.T", SHORT_STRING, FIRST_TIME_INT_FIELD),
            factory().field("R.T", SHORT_STRING, COMMON_FIELD)
        );
    }

    @Test
    public void testBadTimedFields() {
        checkFail("Failed to create default data-scheme: R.Obj time-field must have integer type",
            factory().field("R.Obj", SERIAL_OBJECT, FIRST_TIME_INT_FIELD)
        );
        checkFail("Failed to create default data-scheme: R.Str time-field must have integer type",
            factory().field("R.Str", STRING, SECOND_TIME_INT_FIELD)
        );
        checkFail("Failed to create default data-scheme: different time-fields proposed for R record",
            factory().field("R.a", COMPACT_INT, FIRST_TIME_INT_FIELD),
            factory().field("R.b", COMPACT_INT, FIRST_TIME_INT_FIELD)
        );
        checkFail("Failed to create default data-scheme: R.xxx field has several different types",
            factory().field("R.xxx", COMPACT_INT, FIRST_TIME_INT_FIELD),
            factory().field("R.xxx", SHORT_STRING, FIRST_TIME_INT_FIELD)
        );
    }
}
