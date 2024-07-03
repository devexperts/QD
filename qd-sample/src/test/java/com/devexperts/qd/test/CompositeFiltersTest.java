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
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.NotFilter;
import com.devexperts.qd.kit.PatternFilter;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.spi.QDFilterFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CompositeFiltersTest {
    // scheme with custom filter factory
    private static final DataScheme SCHEME = new TestDataScheme(20070517) {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getService(Class<T> serviceClass) {
            if (serviceClass == QDFilterFactory.class) {
                return (T) new QDFilterFactory(this) {
                    @Override
                    public QDFilter createFilter(String spec) {
                        if ("an".equals(spec))
                            return CompositeFilters.makeAnd(
                                    CompositeFilters.valueOf("[A-N]*", SCHEME),
                                    CompositeFilters.valueOf(":Record1", SCHEME));
                        if ("oz".equals(spec))
                            return CompositeFilters.makeAnd(
                                    CompositeFilters.valueOf("[O-Z]*", SCHEME),
                                    CompositeFilters.valueOf(":Record1", SCHEME));
                        if ("asymbol".equals(spec))
                            return PatternFilter.valueOf("A*", "asymbol", SCHEME);
                        return null;
                    }
                };
            }
            return super.getService(serviceClass);
        }
    };

    private static final String[] SYMBOLS = getSymbols();

    private static String[] getSymbols() {
        List<String> l = new ArrayList<>();
        l.add("");
        l.add(",");
        l.add("&");
        l.add("(");
        l.add(")");
        l.add("[");
        l.add("]");
        for (char c1 = 'A'; c1 <= 'Z'; c1++)
            l.add("" + c1);
        for (char c1 = 'A'; c1 <= 'Z'; c1++)
            for (char c2 = 'A'; c2 <= 'Z'; c2++)
                l.add("" + c1 + c2);
        for (char c1 = 'A'; c1 <= 'Z'; c1++)
            for (char c2 = 'A'; c2 <= 'Z'; c2++)
                for (char c3 = 'A'; c3 <= 'Z'; c3++)
                    l.add("" + c1 + c2 + c3);
        return l.toArray(new String[0]);
    }

    @Test
    public void testNothing() {
        checkSame("!*", QDFilter.NOTHING, true);
        checkSame("!*", CompositeFilters.makeNot(QDFilter.ANYTHING), true);
        assertSame(QDFilter.NOTHING, CompositeFilters.makeNot(QDFilter.ANYTHING));
        assertSame(QDFilter.ANYTHING, CompositeFilters.makeNot(QDFilter.NOTHING));
    }

    @Test
    public void testCustom() {
        assertEquals("an", CompositeFilters.valueOf("an", SCHEME).toString());
        assertEquals("an", CompositeFilters.valueOf("((an))", SCHEME).toString());
        assertEquals("oz", CompositeFilters.valueOf("oz", SCHEME).toString());
        assertEquals("an&oz", CompositeFilters.valueOf("an&oz", SCHEME).toString());
    }

    @Test
    public void testLogic() {
        boolean[] b = getResult("B*");
        checkNonTrivial(b);
        boolean[] notB = getResult("!B*");
        checkNonTrivial(notB);
        for (int i = 0; i < SYMBOLS.length; i++)
            assertEquals(SYMBOLS[i], !b[i], notB[i]);
        boolean[] cc = getResult("CC*");
        checkNonTrivial(cc);
        for (int i = 0; i < SYMBOLS.length; i++)
            assertFalse(SYMBOLS[i], b[i] && cc[i]);
        boolean[] bOrCc = getResult("B*,CC*");
        checkNonTrivial(bOrCc);
        for (int i = 0; i < SYMBOLS.length; i++)
            assertEquals(SYMBOLS[i], b[i] || cc[i], bOrCc[i]);
        boolean[] notBAndNotCc = getResult("!B*&!CC*");
        checkNonTrivial(notBAndNotCc);
        for (int i = 0; i < SYMBOLS.length; i++)
            assertEquals(SYMBOLS[i], !b[i] && !cc[i], notBAndNotCc[i]);
    }

    private void checkNonTrivial(boolean[] a) {
        int tc = 0;
        int fc = 0;
        for (int i = 0; i < SYMBOLS.length; i++)
            if (a[i])
                tc++;
            else
                fc++;
        assertTrue(tc != 0);
        assertTrue(fc != 0);
    }

    @Test
    public void testParse() {
        // null elimination
        checkSame("A*,*", null, false);
        checkSame("*,A*", null, false);
        checkSame("A*&*", CompositeFilters.valueOf("A*", SCHEME), false);
        checkSame("*&A*", CompositeFilters.valueOf("A*", SCHEME), false);

        // escaping
        checkSame("[&,()*]*", CompositeFilters.valueOf("[&,()*]*", SCHEME), true);
        checkSame("[&]*,[,]*,[(]*,[)]*,[*]*", CompositeFilters.valueOf("[&,()*]*", SCHEME), false);
        checkSame("[&]*,(((!![,]*,[(]*))),[)]*,!![*]*", CompositeFilters.valueOf("[&,()*]*", SCHEME), false);

        // more escaping
        checkSame("A or B", CompositeFilters.makeOr(
            CompositeFilters.valueOf("A", SCHEME), CompositeFilters.valueOf("B", SCHEME)), false);
        checkSame("[A] or [B]", CompositeFilters.makeOr(
            CompositeFilters.valueOf("A", SCHEME), CompositeFilters.valueOf("B", SCHEME)), false);
        checkSame("[A]or[B]", CompositeFilters.valueOf("[A]or[B]", SCHEME), true);

        // logic
        checkSame("A*,B*", CompositeFilters.makeOr(
            CompositeFilters.valueOf("A*", SCHEME), CompositeFilters.valueOf("B*", SCHEME)), true);
        checkSame("A*,B*,C*,D*",
            CompositeFilters.makeOr(
                CompositeFilters.makeOr(
                    CompositeFilters.valueOf("A*", SCHEME), CompositeFilters.valueOf("B*", SCHEME)),
                CompositeFilters.makeOr(
                    CompositeFilters.valueOf("C*", SCHEME), CompositeFilters.valueOf("D*", SCHEME))), true);
        checkSame("X*&Y*",
            CompositeFilters.makeAnd(
                CompositeFilters.valueOf("X*", SCHEME), CompositeFilters.valueOf("Y*", SCHEME)), true);
        checkSame("!E*",
            CompositeFilters.makeNot(CompositeFilters.valueOf("E*", SCHEME)), true);
        checkSame("F*,A*&!AB*&!AZ*&!AK*",
            CompositeFilters.makeOr(
                CompositeFilters.valueOf("F*", SCHEME),
                CompositeFilters.makeAnd(
                    CompositeFilters.valueOf("A*", SCHEME),
                    CompositeFilters.makeNot(
                        CompositeFilters.makeOr(
                            CompositeFilters.valueOf("AB*", SCHEME),
                            CompositeFilters.makeOr(
                                CompositeFilters.valueOf("AZ*", SCHEME),
                                CompositeFilters.valueOf("AK*", SCHEME)
                            )
                        )
                    ))), true);
        checkSame("!*&A", CompositeFilters.makeAnd(
            QDFilter.NOTHING,
            CompositeFilters.valueOf("A", SCHEME)
        ), false);
    }

    @Test
    public void testForRecords() {
        DataRecord r1 = SCHEME.getRecord(0);
        DataRecord r2 = SCHEME.getRecord(1);

        RecordOnlyFilter r1f = CompositeFilters.forRecords(Collections.singleton(r1));
        RecordOnlyFilter r2f = CompositeFilters.forRecords(Collections.singleton(r2));
        checkFilters(r1, r2, r1f, r2f);

        r1f = new RecordOnlyFilter(SCHEME) {
            @Override
            public boolean acceptRecord(DataRecord record) {
                return record == r1;
            }

            @Override
            public String getDefaultName() {
                return "r1";
            }
        };
        r2f = new RecordOnlyFilter(SCHEME) {
            @Override
            public boolean acceptRecord(DataRecord record) {
                return record == r2;
            }

            @Override
            public String getDefaultName() {
                return "r2";
            }
        };
        checkFilters(r1, r2, r1f, r2f);
    }

    private void checkFilters(DataRecord r1, DataRecord r2, RecordOnlyFilter r1f, RecordOnlyFilter r2f) {
        QDFilter r1OrR2 = CompositeFilters.makeOr(r1f, r2f);
        checkRecord(r1OrR2, r1, true);
        checkRecord(r1OrR2, r2, true);

        QDFilter r1AndR2 = CompositeFilters.makeAnd(r1f, r2f);
        checkRecord(r1AndR2, r1, false);
        checkRecord(r1AndR2, r2, false);

        QDFilter notR1 = CompositeFilters.makeNot(r1f);
        checkRecord(notR1, r1, false);
        checkRecord(notR1, r2, true);
    }

    private void checkRecord(QDFilter filter, DataRecord record, boolean expected) {
        assertEquals(expected, filter.accept(null, record, 0, null));
        assertEquals(expected, filter.accept(null, record, record.getScheme().getCodec().getWildcardCipher(), "*"));
        assertEquals(expected, filter.accept(null, record, 0, "TestTestTest"));
        assertEquals(expected, ((RecordOnlyFilter) filter).acceptRecord(record)); // also checks proper filter class
    }

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void testOptimize() {
        // record filters ordered first (add spaces to the so its longer vs default name)
        checkConversion("         IBM*&:Record1", ":Record1&IBM*");
        checkConversion("         :Record1&IBM*&:Record[12]", ":Record1&:Record[12]&IBM*");
        // even in complex subexpressions
        checkConversion("         (:Record1&IBM*)&(:Record[13]&I*)", ":Record1&:Record[13]&IBM*&I*");
        // proper parenthesis after optimization as needed
        checkConversion("         (:Record2,:Record3)&(.*,:Record4)", "(:Record2,:Record3)&(:Record4,.*)");
        checkConversion("         !(:Record2,MSFT,:Record3)", "!:Record2&!:Record3&!MSFT");
        checkConversion("         !(:Record2,:Record3,:Record4)", "!:Record2&!:Record3&!:Record4");
        // long chain for ands mixed with any
        checkConversion("         *&:Record1&*&*&MSFT*&:Record[123]&*&:Record[13]&*&IBM*&*",
            ":Record1&:Record[123]&:Record[13]&MSFT*&IBM*");
        // will optimize to anything
        assertTrue(CompositeFilters.valueOf("*,:Record1", SCHEME) == QDFilter.ANYTHING);
        assertTrue(CompositeFilters.valueOf(":Record1,*", SCHEME) == QDFilter.ANYTHING);
        // will optimize to nothing
        assertTrue(CompositeFilters.valueOf("!*&:Record1", SCHEME) == QDFilter.NOTHING);
        assertTrue(CompositeFilters.valueOf(":Record1&!*", SCHEME) == QDFilter.NOTHING);
        checkConversion("*&:Record1&*&*&MSFT&:Record[123]&*&:Record[13]&*&IBM*&*", "!*");
        checkConversion("*&:Record1&*&*&MSFT&:Record[123]&*&:Record[13]&*&IBM&*", "!*");
        // make sure that "all records effectively" is _not_ recognized as anything, but is kept
        // (important for string rep of filters)
        checkConversion(":*&*&IBM*&*&:*&:Record*&*", ":Record*&IBM*");
        // alternative operation names
        checkConversion("any", "*");
        checkConversion("A any", "A*");
        checkConversion("record Record1", ":Record1");
        checkConversion("record Record any", ":Record*");
        checkConversion("any and record Record1", ":Record1");
        checkConversion("A* and B*", "A*&B*");
        checkConversion("A* or B*", "A*,B*");
        checkConversion("not A*", "!A*");
        checkConversion("not A* or not B*", "!A*,!B*");
        checkConversion("not(A* or B*)", "!A*&!B*");
        checkConversion("not(A any or B any)", "!A*&!B*");
        // double negation
        checkConversion("!!A", "A");
        checkConversion("!!!A", "!A");
        checkConversion("!!(A,B)", "A,B");
        checkConversion("!!!(A,B)", "!A&!B");
        checkConversion("!!!(A),!!!(B)", "!A,!B");
        checkConversion("!!A*", "A*");
        checkConversion("!!!A*", "!A*");
        // custom pattern filter
        checkConversion("asymbol", "asymbol");
        checkConversion("!asymbol", "!asymbol");
        checkConversion("!!asymbol", "asymbol");
        checkConversion("!!!asymbol", "!asymbol");
    }

    @Test
    public void testEfficiency() {
        // symbol sets
        assertTrue(parse("A") instanceof SymbolSetFilter);
        assertTrue(parse("!A") instanceof SymbolSetFilter);
        assertTrue(parse("A,B,C") instanceof SymbolSetFilter);
        assertTrue(parse("!(A,B,C)") instanceof SymbolSetFilter);
        assertTrue(CompositeFilters.makeAnd(parse("A,B"), new NotFilter(parse("A*"))) instanceof SymbolSetFilter);
        // efficient not-pattern implementation
        assertTrue(parse("A*") instanceof PatternFilter);
        assertTrue(parse("!A*") instanceof PatternFilter);
    }

    @Test
    public void testOptimizedSymbolSets() {
        checkSame("[AB],C", SymbolSetFilter.valueOf("A,B,C", SCHEME), false);
        checkConversion("[AB],C", "[AB],C");
    }

    private void checkConversion(String s1, String s2) {
        QDFilter f = parse(s1);
        assertEquals(s2, f == null ? null : f.toString());
    }

    private boolean getResult(String symbol, SubscriptionFilter f) {
        int cipher = SCHEME.getCodec().encode(symbol);
        return f == null || f.acceptRecord(SCHEME.getRecord(0), cipher, symbol);
    }

    private boolean[] getResult(SubscriptionFilter f) {
        boolean[] result = new boolean[SYMBOLS.length];
        for (int i = 0; i < SYMBOLS.length; i++)
            result[i] = getResult(SYMBOLS[i], f);
        if (f != null)
            assertTrue("*", f.acceptRecord(SCHEME.getRecord(0), SCHEME.getCodec().getWildcardCipher(), null));
        return result;
    }

    private boolean[] getResult(String s) {
        SubscriptionFilter f = parse(s);
        return getResult(f);
    }

    private void checkSame(String symbol, SubscriptionFilter one, SubscriptionFilter two) {
        DataRecord record = SCHEME.getRecord(0);
        int cipher = SCHEME.getCodec().encode(symbol);
        assertEquals(symbol,
            one == null || one.acceptRecord(record, cipher, symbol),
            two == null || two.acceptRecord(record, cipher, symbol));
    }

    private void checkSame(String s, SubscriptionFilter two, boolean checkSame) {
        QDFilter one = parse(s);
        for (String symbol : SYMBOLS)
            checkSame(symbol, one, two);
        if (checkSame) {
            assertEquals(s, one.toString());
            assertEquals(s, two.toString());
        }
    }

    private QDFilter parse(String s) {
        QDFilter result = CompositeFilters.valueOf(s, SCHEME);
        assertTrue(result.isStable()); // and must be stable
        return result;
    }
}
