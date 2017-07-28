/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import junit.framework.TestCase;

public class BadSubTest extends TestCase {
    private static final DataScheme SCHEME = new TestDataScheme(20070707);
    private static final DataScheme SCHEME2 = new TestDataScheme(20070707);

    public void testHistoryNoHistory() {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDAgent agent = history.agentBuilder().build();
        SubscriptionBuffer sb = new SubscriptionBuffer();
        sb.visitRecord(SCHEME.getRecord(0), SCHEME.getCodec().encode("IBM"), null);

        try {
            agent.setSubscription(sb);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        agent.close(); // should complete normally
    }

    public void testTickerWrongScheme() {
        checkWrongScheme(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    public void testStreamWrongScheme() {
        checkWrongScheme(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

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
        } catch (IllegalArgumentException e) {
            // expected
        }

        agent.close(); // should complete normally
    }
}
