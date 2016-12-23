/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import java.util.*;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.spi.QDFilterFactory;
import junit.framework.TestCase;

public class CompositeFiltersTest extends TestCase {
	// scheme with custom filter factory
	private static final DataScheme SCHEME = new TestDataScheme(20070517) {
		@SuppressWarnings("unchecked")
		@Override
		public <T> T getService(Class<T> serviceClass) {
			if (serviceClass == QDFilterFactory.class) {
				return (T)new QDFilterFactory(this) {
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

	public void testNothing() {
		checkSame("!*", QDFilter.NOTHING, true);
		checkSame("!*", CompositeFilters.makeNot(QDFilter.ANYTHING), true);
		assertTrue(CompositeFilters.makeNot(QDFilter.ANYTHING) == QDFilter.NOTHING);
		assertTrue(CompositeFilters.makeNot(QDFilter.NOTHING) == QDFilter.ANYTHING);
	}

	public void testCustom() {
		assertEquals("an", CompositeFilters.valueOf("an", SCHEME).toString());
		assertEquals("an", CompositeFilters.valueOf("((an))", SCHEME).toString());
		assertEquals("oz", CompositeFilters.valueOf("oz", SCHEME).toString());
		assertEquals("an&oz", CompositeFilters.valueOf("an&oz", SCHEME).toString());
	}

	public void testLogic() {
		boolean[] b = getResult("B*");
		checkNonTrivial(b);
		boolean[] not_b = getResult("!B*");
		checkNonTrivial(not_b);
		for (int i = 0; i < SYMBOLS.length; i++)
			assertEquals(SYMBOLS[i], !b[i], not_b[i]);
		boolean[] cc = getResult("CC*");
		checkNonTrivial(cc);
		for (int i = 0; i < SYMBOLS.length; i++)
			assertFalse(SYMBOLS[i], b[i] && cc[i]);			
		boolean[] b_or_cc = getResult("B*,CC*");
		checkNonTrivial(b_or_cc);
		for (int i = 0; i < SYMBOLS.length; i++)
			assertEquals(SYMBOLS[i], b[i] || cc[i], b_or_cc[i]);
		boolean[] not_b_and_not_cc = getResult("!B*&!CC*");
		checkNonTrivial(not_b_and_not_cc);
		for (int i = 0; i < SYMBOLS.length; i++)
			assertEquals(SYMBOLS[i], !b[i] && !cc[i], not_b_and_not_cc[i]);
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

	public void testParse() {
		// null elimination
		checkSame("A*,*", null, false);
		checkSame("*,A*", null, false);
		checkSame("A*&*", PatternFilter.valueOf("A*", null), false);
		checkSame("*&A*", PatternFilter.valueOf("A*", null), false);

		// escaping
		checkSame("[&,()*]*", PatternFilter.valueOf("[&,()*]*", null), true);
		checkSame("[&]*,[,]*,[(]*,[)]*,[*]*", PatternFilter.valueOf("[&,()*]*", null), false);
		checkSame("[&]*,(((!![,]*,[(]*))),[)]*,!![*]*", PatternFilter.valueOf("[&,()*]*", null), false);

		// more escaping
		checkSame("A or B", CompositeFilters.makeOr(PatternFilter.valueOf("A", SCHEME), PatternFilter.valueOf("B", SCHEME)), false);
		checkSame("[A] or [B]", CompositeFilters.makeOr(PatternFilter.valueOf("A", SCHEME), PatternFilter.valueOf("B", SCHEME)), false);
		checkSame("[A]or[B]", PatternFilter.valueOf("[A]or[B]", SCHEME), true);

		// logic
		checkSame("A*,B*",
			CompositeFilters.makeOr(PatternFilter.valueOf("A*", null), PatternFilter.valueOf("B*", null)), true);
		checkSame("A*,B*,C*,D*",
			CompositeFilters.makeOr(
				CompositeFilters.makeOr(PatternFilter.valueOf("A*", null), PatternFilter.valueOf("B*", null)),
				CompositeFilters.makeOr(PatternFilter.valueOf("C*", null), PatternFilter.valueOf("D*", null))), true);
		checkSame("X*&Y*",
			CompositeFilters.makeAnd(PatternFilter.valueOf("X*", null), PatternFilter.valueOf("Y*", null)), true);
		checkSame("!E*",
			CompositeFilters.makeNot(PatternFilter.valueOf("E*", null)), true);
		checkSame("F*,A*&!AB*&!AZ*&!AK*",
			CompositeFilters.makeOr(
				PatternFilter.valueOf("F*", null),
				CompositeFilters.makeAnd(
					PatternFilter.valueOf("A*", null),
					CompositeFilters.makeNot(
						CompositeFilters.makeOr(
							PatternFilter.valueOf("AB*", null),
							CompositeFilters.makeOr(
								PatternFilter.valueOf("AZ*", null),
								PatternFilter.valueOf("AK*", null)
							)
						)
					))), true);
		checkSame("!*&A", CompositeFilters.makeAnd(
			QDFilter.NOTHING,
			PatternFilter.valueOf("A", null)
		), true);
	}

	public void testForRecords() {
		DataRecord r1 = SCHEME.getRecord(0);
		DataRecord r2 = SCHEME.getRecord(1);

		RecordOnlyFilter r1_or_r2 = (RecordOnlyFilter)CompositeFilters.makeOr(CompositeFilters.forRecords(Collections.singleton(r1)),
			CompositeFilters.forRecords(Collections.singleton(r2)));
		assertTrue(r1_or_r2.acceptRecord(r1));
		assertTrue(r1_or_r2.acceptRecord(r2));

		RecordOnlyFilter r1_and_r2 = (RecordOnlyFilter)CompositeFilters.makeAnd(CompositeFilters.forRecords(Collections.singleton(r1)),
			CompositeFilters.forRecords(Collections.singleton(r2)));
		assertFalse(r1_and_r2.acceptRecord(r1));
		assertFalse(r1_and_r2.acceptRecord(r2));

		RecordOnlyFilter not_r1 = (RecordOnlyFilter)CompositeFilters.makeNot(CompositeFilters.forRecords(Collections.singleton(r1)));
		assertFalse(not_r1.acceptRecord(r1));
		assertTrue(not_r1.acceptRecord(r2));
	}

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
		checkConversion("         *&:Record1&*&*&MSFT*&:Record[123]&*&:Record[13]&*&IBM*&*", ":Record1&:Record[123]&:Record[13]&MSFT*&IBM*");
		// wiil optimize to nothing
		checkConversion("*&:Record1&*&*&MSFT&:Record[123]&*&:Record[13]&*&IBM*&*", "!*");
		checkConversion("*&:Record1&*&*&MSFT&:Record[123]&*&:Record[13]&*&IBM&*", "!*");
		// make sure that "all records effectively" is _not_ recognized as anything, but is kept (important for string rep of filters)
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

	public void testEfficiency() {
		// symbol sets
		assertTrue(parse("A") instanceof SymbolSetFilter);
		assertTrue(parse("!A") instanceof SymbolSetFilter);
		assertTrue(parse("A,B,C") instanceof SymbolSetFilter);
		assertTrue(parse("!(A,B,C)") instanceof SymbolSetFilter);
		// efficient not-pattern implementation
		assertTrue(parse("A*") instanceof PatternFilter);
		assertTrue(parse("!A*") instanceof PatternFilter);
	}

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

	private void checkSame(String s, SubscriptionFilter two, boolean check_s) {
		QDFilter one = parse(s);
		for (String symbol : SYMBOLS)
			checkSame(symbol, one, two);
		if (check_s) {
			assertEquals(s, one.toString());
			assertEquals(s, two.toString());
		}
	}

	private QDFilter parse(String s) {
		QDFilter result = CompositeFilters.valueOf(s, SCHEME);
		assertTrue(result == null || result.isStable()); // and must be stable
		return result;
	}
}
