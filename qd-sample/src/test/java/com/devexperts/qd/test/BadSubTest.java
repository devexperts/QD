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
package com.devexperts.qd.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionBuffer;
import org.junit.Test;

import static org.junit.Assert.fail;

public class BadSubTest {
    private static final DataScheme SCHEME = new TestDataScheme(20070707);
    private static final DataScheme SCHEME2 = new TestDataScheme(20070707);

    @Test
    public void testHistoryNoHistory() {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDAgent agent = history.agentBuilder().build();
        SubscriptionBuffer sb = new SubscriptionBuffer();
        sb.visitRecord(SCHEME.getRecord(0), SCHEME.getCodec().encode("IBM"), null);

        try {
            agent.setSubscription(sb);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        agent.close(); // should complete normally
    }

    @Test
    public void testTickerWrongScheme() {
        checkWrongScheme(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testStreamWrongScheme() {
        checkWrongScheme(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

    @Test
    public void testHistoryWrongScheme() {
        checkWrongScheme(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void checkWrongScheme(QDCollector collector) {
        QDAgent agent = collector.agentBuilder().build();
        SubscriptionBuffer sb = new SubscriptionBuffer();
        sb.visitRecord(SCHEME2.getRecord(0), SCHEME.getCodec().encode("IBM"), null);

        try {
            agent.setSubscription(sb);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        agent.close(); // should complete normally
    }
}
