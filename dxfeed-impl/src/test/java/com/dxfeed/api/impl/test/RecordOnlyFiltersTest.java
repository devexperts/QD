/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.impl.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.kit.RecordOnlyFilter;
import junit.framework.TestCase;

public class RecordOnlyFiltersTest extends TestCase {
	private static final DataScheme SCHEME = QDFactory.getDefaultScheme();

	public void testQuote() {
		assertRecordOnlyIncludes("Quote", "Quote");
		assertRecordOnlyExcludes("Quote", "Trade", "Fundamental", "Summary", "Message");
	}

	public void testQuoteAndTrade() {
		assertRecordOnlyIncludes("Quote,Trade", "Quote", "Trade");
		assertRecordOnlyExcludes("Quote,Trade", "Fundamental", "Summary", "Message");
	}

	public void testNotQuote() {
		assertRecordOnlyIncludes("!Quote", "Trade", "Fundamental", "Summary", "Message");
		assertRecordOnlyExcludes("!Quote", "Quote");
	}

	public void testFeed() {
		assertRecordOnlyIncludes("feed", "Quote", "Trade", "Fundamental", "Summary");
		assertRecordOnlyExcludes("feed", "Message");
	}

	public void testMessage() {
		assertRecordOnlyIncludes("Message", "Message");
		assertRecordOnlyExcludes("Message", "Quote", "Trade", "Fundamental", "Summary");
	}

	public void assertRecordOnlyIncludes(String spec, String... includes) {
		RecordOnlyFilter filter = RecordOnlyFilter.valueOf(spec, SCHEME);
		for (String name : includes)
			assertTrue(filter.acceptRecord(SCHEME.findRecordByName(name)));
	}

	public void assertRecordOnlyExcludes(String spec, String... excludes) {
		RecordOnlyFilter filter = RecordOnlyFilter.valueOf(spec, SCHEME);
		for (String name : excludes)
			assertFalse(filter.acceptRecord(SCHEME.findRecordByName(name)));
	}
}
