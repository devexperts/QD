/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import java.util.BitSet;
import java.util.Random;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import junit.framework.TestCase;

/**
 * Large subscription performance unit test.
 */
public class LargeSubscriptionTest extends TestCase {
	public LargeSubscriptionTest(String s) {
		super(s);
	}

	private static final SymbolCodec codec = new PentaCodec();
	private static final DataRecord record = new DefaultRecord(0, "Haba", false, null, null);
	private static final DataScheme scheme = new DefaultScheme(codec, new DataRecord[] {record});

	private static SubscriptionIterator sub(int size, boolean encodeable) {
		Random rnd = new Random();
		BitSet sub = new BitSet(1 << 24);
		char[] c = new char[6];
		SubscriptionBuffer sb = new SubscriptionBuffer();
		while (sb.size() < size) {
			int r = rnd.nextInt() & 0xFFFFFF;
			if (sub.get(r))
				continue;
			sub.set(r);
			for (int i = 0; i < c.length; i++)
				c[c.length - 1 - i] = (char)((encodeable ? 'A' : 'a') + ((r >> (i << 2)) & 0x0F));
			int cipher = codec.encode(c, 0, c.length);
			String symbol = cipher == 0 ? new String(c) : null;
			sb.visitRecord(record, cipher, symbol);
		}
		return sb;
	}

	public void testLargeSubscription() {
		int size = 40000;
		int threshold = 4;

		QDTicker ticker = QDFactory.getDefaultFactory().createTicker(scheme);
		QDDistributor distributor = ticker.distributorBuilder().build();
		QDAgent agent = ticker.agentBuilder().build();
		SubscriptionIterator sub = sub(size, true);
		long time1 = System.currentTimeMillis();
		agent.setSubscription(sub);
		long time2 = System.currentTimeMillis();
		agent.close();
		long time3 = System.currentTimeMillis();
		System.out.println("Size " + size + ", subscribe " + (time2 - time1) + ", close " + (time3 - time2));
		assertTrue("square", (time3 - time2) / (time2 - time1) < threshold);
	}
}
