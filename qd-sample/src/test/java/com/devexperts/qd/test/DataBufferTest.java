/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import java.util.Random;

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataScheme;
import junit.framework.TestCase;

/**
 * Unit test for {@link DataBuffer} class.
 */
public class DataBufferTest extends TestCase {
	private Random rnd = new Random();

	private static final int REPEAT = 100;

	public DataBufferTest(String s) {
		super(s);
	}

	public void testVisitIterate() {
		long provider_seed = rnd.nextLong();
		DataBuffer buffer = new DataBuffer();
		DataScheme scheme = new TestDataScheme(rnd.nextLong());
		TestDataProvider provider1 = new TestDataProvider(scheme, provider_seed);
		TestDataProvider provider2 = new TestDataProvider(scheme, provider_seed);
		for (int repeat = 0; repeat < REPEAT; repeat++) {
			provider1.retrieveData(buffer);
			ComparingDataVisitor.compare(provider2, buffer);
		}
	}
}
