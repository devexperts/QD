/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
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
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import org.junit.Test;

import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class TickerSubscriptionTest {
    private static final SymbolCodec codec = PentaCodec.INSTANCE;
    private static final DataRecord record = new DefaultRecord(0, "Haba", false, null, null);
    private static final DataScheme scheme = new DefaultScheme(codec, record);

    private static final String symbol1 = "Symbol1";
    private static final String symbol2 = "Symbol2";

    @Test
    public void testResubscribeForReappearingSnapshot() {
        QDTicker ticker = QDFactory.getDefaultFactory().tickerBuilder().withScheme(scheme).build();
        QDDistributor distributor = ticker.distributorBuilder().build();
        QDAgent agent = ticker.agentBuilder().build();

        process(agent::addSubscription, symbol1);
        process(agent::addSubscription, symbol2);
        process(distributor::process, symbol1);
        process(distributor::process, symbol2);

        process(agent::removeSubscription, symbol2);
        process(agent::addSubscription, symbol2);
        checkRetrieve(agent, symbol1);

        process(distributor::process, symbol2);
        checkRetrieve(agent.getSnapshotProvider(), symbol2);
    }

    @Test
    public void testResubscribeForExistingSnapshotViaSnapshotQueue() {
        QDAgent agent = resubscribeForExistingSnapshot();
        checkRetrieve(agent.getSnapshotProvider(), symbol1, symbol2);
    }

    @Test
    public void testResubscribeForExistingSnapshotViaMainQueue() {
        QDAgent agent = resubscribeForExistingSnapshot();
        checkRetrieve(agent, symbol1, symbol2);
    }

    private static QDAgent resubscribeForExistingSnapshot() {
        QDTicker ticker = QDFactory.getDefaultFactory().tickerBuilder().withScheme(scheme).build();
        ticker.setStoreEverything(true);
        QDDistributor distributor = ticker.distributorBuilder().build();
        QDAgent agent = ticker.agentBuilder().build();
        process(distributor::process, symbol1);
        process(distributor::process, symbol2);

        process(agent::addSubscription, symbol1);
        process(agent::addSubscription, symbol2);
        process(agent::removeSubscription, symbol2);
        process(agent::addSubscription, symbol2);
        return agent;
    }

    private static void checkRetrieve(RecordProvider provider, String... symbols) {
        RecordBuffer sink = new RecordBuffer(RecordMode.DATA);
        provider.retrieve(sink);
        assertEquals(symbols.length, sink.size());
        for (String symbol : symbols)
            assertEquals(symbol, sink.next().getDecodedSymbol());
    }

    private static void process(Consumer<RecordBuffer> consumer, String symbol) {
        RecordBuffer buf = new RecordBuffer(RecordMode.DATA);
        buf.add(record, codec.encode(symbol), symbol);
        consumer.accept(buf);
    }
}
