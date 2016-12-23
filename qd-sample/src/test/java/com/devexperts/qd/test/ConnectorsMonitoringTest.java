/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import com.devexperts.qd.monitoring.ConnectorsMonitoringTask;
import com.devexperts.qd.monitoring.QDMonitoring;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.stats.QDStats;
import junit.framework.TestCase;

public class ConnectorsMonitoringTest extends TestCase {
	private static final long SEED = 20100928;
	private static final int REC_COUNT = 10;
	private static final DataScheme SCHEME = new TestDataScheme(SEED);

	public void testStreamStats() {
		ConnectorsMonitoringTask cmt = new ConnectorsMonitoringTask();
		QDStats stats = QDMonitoring.createRootStats("test", SCHEME);
		cmt.addStats(stats);
		QDStream stream = QDFactory.getDefaultFactory().createStream(SCHEME, stats.create(QDStats.SType.STREAM));

		QDAgent agent = stream.agentBuilder().build();
		RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION);
		new TestSubscriptionProvider(SCHEME, SEED, REC_COUNT, true).retrieve(sub);
		agent.addSubscription(sub);

		QDDistributor dist = stream.distributorBuilder().build();
		RecordBuffer buf = RecordBuffer.getInstance();
		new TestDataProvider(SCHEME, SEED, REC_COUNT).retrieve(buf);
		dist.processData(buf);

		String report = cmt.report();
		assertTrue(report, report.matches("^Subscription: 10; Storage: 0; Buffer: 10; Read: 0 Bps; Write: 0 Bps; CPU: .*"));
	}
}
