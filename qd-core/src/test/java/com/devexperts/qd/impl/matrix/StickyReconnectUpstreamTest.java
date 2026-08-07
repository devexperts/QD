/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.TimeMillisField;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.util.TimePeriod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class StickyReconnectUpstreamTest {

    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] {
            new TimeMillisField(0, "Test.Time"),
            new VoidIntField(1, "Test.Time.void"),
            new CompactIntField(2, "Test.Index"),
            new CompactIntField(3, "Test.Value")
        }, null);
    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);
    private static final SymbolCodec CODEC = SCHEME.getCodec();

    private static final String SYMBOL = "TEST_SYMBOL";
    private static final int SUB_TIME = 10;

    @Parameter
    public QDContract contract;

    @Parameters(name = "{0}")
    public static Object[] data() {
        return QDContract.values();
    }

    @Test
    public void testResubscribeWithinStickyAfterReconnectHasNoUpstreamSubscription() {
        Collector collector = (Collector) QDFactory.getDefaultFactory().collectorBuilder(contract)
            .withScheme(SCHEME)
            .withStickySubscriptionPeriod(TimePeriod.valueOf(TimeUnit.MINUTES.toMillis(1)))
            .build();

        QDDistributor distributor1 = collector.distributorBuilder().build();
        QDAgent agent1 = collector.agentBuilder().build();

        agent1.addSubscription(subscription());
        assertEquals("subscription must reach the uplink", 1, drain(distributor1.getAddedRecordProvider()));
        assertTrue("entry is a live subscription while a client is attached", isSubscribed(collector));

        agent1.close();

        assertEquals("entry is held by sticky (NEXT_AGENT == -2)",
            Collector.NO_NEXT_AGENT_STICKY_DELAY, nextAgent(collector));
        assertTrue("sticky entry is still payload", isPayload(collector));
        assertTrue("sticky entry is not reported as subscribed", isSubscribed(collector));
        assertTrue("public isSubscribed agrees", collector.isSubscribed(RECORD, CODEC.encode(SYMBOL), SYMBOL, SUB_TIME));
        assertEquals("sticky must NOT retract the subscription upstream added", 0, drain(distributor1.getAddedRecordProvider()));
        assertEquals("sticky must NOT retract the subscription upstream removed", 0, drain(distributor1.getRemovedRecordProvider()));

        distributor1.close();
        QDDistributor distributor2 = collector.distributorBuilder().build();

        assertEquals("reconnect send the sticky subscription upstream", 1, drain(distributor2.getAddedRecordProvider()));

        QDAgent agent2 = collector.agentBuilder().build();
        agent2.addSubscription(subscription());

        assertTrue("entry is live again after re-subscribe", nextAgent(collector) > 0);
        assertTrue("collector reports it as subscribed", isSubscribed(collector));
        assertTrue("public isSubscribed agrees", collector.isSubscribed(RECORD, CODEC.encode(SYMBOL), SYMBOL, SUB_TIME));

        assertEquals("live client but nothing was ever requested upstream added", 0, drain(distributor2.getAddedRecordProvider()));
        assertEquals("live client but nothing was ever requested upstream removed", 0, drain(distributor1.getRemovedRecordProvider()));

        agent2.close();
        distributor2.close();
        collector.close();
    }

    private RecordBuffer subscription() {
        RecordBuffer sub = new RecordBuffer(
            contract == QDContract.HISTORY ? RecordMode.HISTORY_SUBSCRIPTION : RecordMode.SUBSCRIPTION);
        RecordCursor cur = sub.add(RECORD, CODEC.encode(SYMBOL), SYMBOL);
        if (cur.hasTime())
            cur.setTime(SUB_TIME);
        return sub;
    }

    private int drain(RecordProvider provider) {
        RecordBuffer buf = new RecordBuffer(
            contract == QDContract.HISTORY ? RecordMode.HISTORY_SUBSCRIPTION : RecordMode.SUBSCRIPTION);
        //noinspection StatementWithEmptyBody
        while (provider.retrieve(buf)) {}
        if (contract == QDContract.HISTORY) {
            buf.rewind();
            for (RecordCursor cur; (cur = buf.next()) != null; ) {
                assertEquals(SUB_TIME, cur.getTime());
            }
        }
        return buf.size();
    }

    private static int totalIndex(Collector collector) {
        SubMatrix tsub = collector.total.sub;
        int found = -1;
        for (int index = tsub.step; index < tsub.matrix.length; index += tsub.step) {
            if (tsub.getInt(index + Collector.KEY) != 0) {
                assertEquals("expected exactly one total.sub entry", -1, found);
                found = index;
            }
        }
        assertTrue("total.sub entry must exist", found > 0);
        return found;
    }

    private static int nextAgent(Collector collector) {
        return collector.total.sub.getInt(totalIndex(collector) + Collector.NEXT_AGENT);
    }

    private static boolean isPayload(Collector collector) {
        return collector.total.sub.isPayload(totalIndex(collector));
    }

    private static boolean isSubscribed(Collector collector) {
        return collector.total.sub.isSubscribed(totalIndex(collector));
    }
}
