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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import org.junit.Test;

public class TickerAdditionalTest {
    private static final DataScheme SCHEME = new TestDataScheme(20081107);
    private static final DataRecord RECORD = SCHEME.getRecord(0);

    @Test
    public void testTickerAddRemoveLong() {
        checkAddRemove(QDFactory.getDefaultFactory().createTicker(SCHEME), "Long symbol");
    }

    private void checkAddRemove(QDCollector collector, String symbol) {
        RecordBuffer buf = new RecordBuffer();
        QDAgent agent = collector.agentBuilder().build();
        QDDistributor distributor = collector.distributorBuilder().build();
        // create big static subscription & data
        for (int i = 0; i < SCHEME.getRecordCount() * 100; i++) {
            String s = Integer.toString(i);
            int cipher = SCHEME.getCodec().encode(s);
            buf.add(RECORD, cipher, s);
        }
        agent.addSubscription(buf);
        buf.rewind();
        distributor.processData(buf);
        buf.clear();

        // add remove one element
        int cipher = SCHEME.getCodec().encode(symbol);
        buf.add(RECORD, cipher, symbol);
        for (int i = 0; i < 50000; i++) {
            agent.addSubscription(buf);
            buf.rewind();
            distributor.processData(buf);
            buf.rewind();
            agent.removeSubscription(buf);
            buf.rewind();
        }
    }
}
