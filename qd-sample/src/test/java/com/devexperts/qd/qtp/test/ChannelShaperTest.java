/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.impl.TradeMapping;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests all types of channel shapers.
 */
public class ChannelShaperTest {
    @Rule
    public Timeout globalTimeout= new Timeout(60, TimeUnit.SECONDS);

    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    private static final DataRecord RECORD = SCHEME.findRecordByName("Trade");

    // ------------- symbols -------------

    private static final String FAST_0 = "FAST_0";
    private static final String FAST_THREAD = "FAST_THREAD";
    private static final String SLOW_THREAD = "SLOW_THREAD";
    private static final String SLOW_THREAD_2 = "SLOW_THREAD_2";
    private static final String KEEP_REJECTED = "KEEP_REJECTED";
    private static final String KEEP_REJECTED_2 = "KEEP_REJECTED_2";
    private static final String KEEP_INIT_REJECTED = "KEEP_INIT_REJECTED";

    private static final QDFilter FAST_0_FILTER = CompositeFilters.valueOf(FAST_0 + "*", SCHEME);
    private static final QDFilter FAST_THREAD_FILTER = CompositeFilters.valueOf(FAST_THREAD + "*", SCHEME);

    private static final QDFilter SLOW_THREAD_PRE_FILTER = CompositeFilters.valueOf(SLOW_THREAD + "*", SCHEME);
    private static final QDFilter SLOW_THREAD_FILTER = new QDFilter(SCHEME) {
        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return SLOW_THREAD_PRE_FILTER.accept(contract, record, cipher, symbol);
        }

        @Override
        public String getDefaultName() {
            return SLOW_THREAD_PRE_FILTER.toString();
        }
    };

    private static final QDFilter KEEP_REJECTED_FILTER = CompositeFilters.valueOf(KEEP_REJECTED + "*", SCHEME);
    private static final QDFilter KEEP_REJECTED_ONLY_FILTER = CompositeFilters.valueOf(KEEP_REJECTED, SCHEME);
    private static final QDFilter KEEP_REJECTED_WITH_INIT_FILTER = CompositeFilters.valueOf(
        KEEP_REJECTED + "*," + KEEP_INIT_REJECTED, SCHEME);

    // ------------- data source -------------

    private final QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
    private final QDDistributor distributor = ticker.distributorBuilder().build();
    private final ArrayBlockingQueue<String> addSubQueue = new ArrayBlockingQueue<>(100);
    private final ArrayBlockingQueue<String> remSubQueue = new ArrayBlockingQueue<>(100);
    private double lastPrice = 12.34;

    {
        distributor.getAddedRecordProvider().setRecordListener(provider -> provider.retrieve(
            new AbstractRecordSink() {
                @Override
                public void append(RecordCursor cursor) {
                    addSubQueue.add(cursor.getDecodedSymbol());
                }
            }
        ));
        distributor.getRemovedRecordProvider().setRecordListener(provider -> provider.retrieve(
            new AbstractRecordSink() {
                @Override
                public void append(RecordCursor cursor) {
                    remSubQueue.add(cursor.getDecodedSymbol());
                }
            }
        ));

    }

    private final ArrayBlockingQueue<Runnable> executorQueue = new ArrayBlockingQueue<>(100);

    private final Executor executor = executorQueue::add;

    // ------------- source connection -------------

    private final int port = (100 + new Random().nextInt(100)) * 100 + 54;
    private List<MessageConnector> connectors;

    // ------------- receiving data feed -------------

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXFeedSubscription<Trade> subscription = new DXFeedSubscription<>(Trade.class);

    private final ArrayBlockingQueue<Trade> incomingEventsQueue = new ArrayBlockingQueue<>(100);

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        connectors = MessageConnectors.createMessageConnectors(
            MessageConnectors.applicationConnectionFactory(new TestFactory()), ":" + port);
        MessageConnectors.startMessageConnectors(connectors);
        // use builder to overwrite dxfeed.properties if they are used
        endpoint = DXEndpoint.newBuilder()
            .withProperty(DXEndpoint.DXFEED_ADDRESS_PROPERTY, "localhost:" + port)
            .withProperty(DXEndpoint.DXFEED_AGGREGATION_PERIOD_PROPERTY, "0")
            .withProperty("dxfeed.qd.subscribe.ticker", "")
            .build();
        feed = endpoint.getFeed();
        feed.attachSubscription(subscription);
        subscription.addEventListener(incomingEventsQueue::addAll);
    }

    @After
    public void tearDown() {
        endpoint.close();
        MessageConnectors.stopMessageConnectors(connectors);
        ThreadCleanCheck.after();
    }

    @Test
    public void testChannelShaperAfterChangingFilterEventPasses() throws InterruptedException {
        subscription.addSymbols(KEEP_INIT_REJECTED, KEEP_REJECTED);
        assertSubAddOnly(KEEP_REJECTED);
        ensureEventGoesThroughOn(KEEP_REJECTED);
        keepRejectedShaper.setSubscriptionFilter(KEEP_REJECTED_WITH_INIT_FILTER);
        ensureEventGoesThroughOn(KEEP_INIT_REJECTED);
    }

    @Test
    public void testChannelShaperAfterChangingFilterEventPasses2() throws InterruptedException {
        Wrap wrap = new Wrap(KEEP_REJECTED_FILTER);
        keepRejectedShaper.setSubscriptionFilter(wrap);
        subscription.addSymbols(KEEP_INIT_REJECTED);
        assertNoSubAddRemove();
        // We have to wait for the subscription to be processed, because it will not reach the distributor
        // (the filter will not let it through). Subscriptions create agents and the filter must be updated
        // after the agent has been created for the update to apply. If the filter checks the subscription,
        // it is an indirect sign that the channel has already been created.
        wrap.await();
        keepRejectedShaper.setSubscriptionFilter(KEEP_REJECTED_WITH_INIT_FILTER);
        ensureEventGoesThroughOn(KEEP_INIT_REJECTED);
    }

    @Test
    public void testChannels() throws InterruptedException {
        // Subscribe to FAST_0 symbol that does not use executor and ensure it arrives
        subscription.addSymbols(FAST_0);
        assertEquals(FAST_0, addSubQueue.take());
        assertEquals(0, addSubQueue.size());
        // there should 1 filtering task for slowThread filter
        executeOne();
        assertNoTasks(); // that does not result in new tasks

        ensureEventGoesThroughOn(FAST_0);
        ensureEventGoesThroughOn(FAST_0); // sanity check -- fast0 symbol still works

        // Subscribe to FAST_THREAD symbol
        subscription.addSymbols(FAST_THREAD);
        // there should be 2 tasks -- process sub with fast filter in separate thread & slow thread filtering
        assertEquals(0, addSubQueue.size()); // nothing before task
        executeOne(); // processed fast thread task
        assertEquals(FAST_THREAD, addSubQueue.take());
        executeOne(); // processed slow filter task
        assertNoTasks(); // that does not result in new tasks

        ensureEventGoesThroughOn(FAST_THREAD);
        ensureEventGoesThroughOn(FAST_0); // sanity check -- fast0 symbol still works

        // Subscribe to SLOW_THREAD symbol
        subscription.addSymbols(SLOW_THREAD);
        // there should 1 filtering task for slowThread filter
        executeOne(); // that results in one more task
        assertEquals(0, addSubQueue.size());
        executeOne(); // that actually subscribed
        assertEquals(SLOW_THREAD, addSubQueue.take());
        assertNoTasks(); // and that's it

        ensureEventGoesThroughOn(SLOW_THREAD);
        ensureEventGoesThroughOn(FAST_0); // sanity check -- fast0 symbol still works

        assertNoSubAddRemove(); // sanity check

        // Subscribe to both KEEP_REJECTED symbols
        // Note, that initial filter does not accept KEEP_INIT_REJECTED
        subscription.addSymbols(KEEP_REJECTED, KEEP_REJECTED_2, KEEP_INIT_REJECTED);
        assertSubAddOnly(KEEP_REJECTED, KEEP_REJECTED_2);
        // there should 1 filtering task for slowThread filter
        executeOne();
        assertNoTasks(); // that does not result in new tasks

        ensureEventGoesThroughOn(KEEP_REJECTED);
        ensureEventGoesThroughOn(KEEP_REJECTED_2);

        // Reconfigure fast_0 channel
        fast0Shaper.setSubscriptionFilter(new Wrap(FAST_0_FILTER));
        ensureEventGoesThroughOn(FAST_0);
        assertNoTasks(); // it does not result in new tasks
        assertSubAddRemove(FAST_0); // it is reconfigure via close agent/ new agent, so add/rem sub here
        ensureEventGoesThroughOn(FAST_0); // and still works
        assertNoTasks(); // it does not result in new tasks

        // Reconfigure fast_thread channel
        fastThreadShaper.setSubscriptionFilter(new Wrap(FAST_THREAD_FILTER));
        ensureEventGoesThroughOn(FAST_THREAD);
        executeOne(); // it creates one reconfiguration task to get sub snapshot and refilter
        assertNoTasks(); // .. and it does not result in new tasks, since nothing changed
        assertNoSubAddRemove(); // it is reconfigure via setSub, so nothing changes
        ensureEventGoesThroughOn(FAST_THREAD); // and still works
        assertNoTasks(); // it does not result in new tasks

        // Reconfigure slow_thread channel
        slowThreadShaper.setSubscriptionFilter(new Wrap(SLOW_THREAD_FILTER));
        ensureEventGoesThroughOn(SLOW_THREAD);
        executeOne(); // it creates one get sub snapshot & filter task
        assertNoTasks(); // .. and it does not result in new tasks, since nothing changed
        assertNoSubAddRemove(); // it is reconfigure via setSub, so nothing changes
        ensureEventGoesThroughOn(SLOW_THREAD); // and still works
        assertNoTasks(); // and does not result in new tasks

        // Reconfigure slow_thread channel AGAIN
        slowThreadShaper.setSubscriptionFilter(new Wrap(SLOW_THREAD_FILTER));
        ensureEventGoesThroughOn(SLOW_THREAD);
        assertEquals(1, executorQueue.size()); // it creates get sub snapshot & filter task

        // Now subscribe to another symbol in slow thread filter
        subscription.addSymbols(SLOW_THREAD_2);
        // and wait until we have two pending tasks (refilter new sub and finish reconfigure)
        long timeout = System.currentTimeMillis() + 1000;
        while (executorQueue.size() < 2 && System.currentTimeMillis() < timeout)
            Thread.sleep(10);

        // Now execute both tasks
        executeOne(); // the first task finishes get snapshot & filter
        assertNoSubAddRemove(); // but nothing changes
        executeOne(); // and the second task shall filter subscribe to SLOW_THREAD_2
        assertNoSubAddRemove(); // but nothing yet changed
        executeOne(); // ... and create new task to actually subscribe
        assertEquals(SLOW_THREAD_2, addSubQueue.take()); // snapshot restored
        assertNoTasks(); // which does not result in new tasks
        assertNoSubAddRemove();

        ensureEventGoesThroughOn(SLOW_THREAD); // and still works
        ensureEventGoesThroughOn(SLOW_THREAD_2); // and still works and for new event too
        assertNoTasks(); // and does not result in new tasks

        // Now remove sub on SLOW_THREAD_2 symbol
        subscription.removeSymbols(SLOW_THREAD_2);
        // there should 1 filtering task for slowThread filter
        executeOne(); // that results in one more task
        assertEquals(0, addSubQueue.size());
        executeOne(); // that actually removes subscription
        assertEquals(SLOW_THREAD_2, remSubQueue.take());
        assertNoTasks(); // and that's it
        assertNoSubAddRemove();

        ensureEventGoesThroughOn(SLOW_THREAD); // but the other symbol still works

        // Now change keep rejected filter, so that KEEP_REJECT_2 is no longer accepted
        keepRejectedShaper.setSubscriptionFilter(KEEP_REJECTED_ONLY_FILTER);
        assertSubRemoveOnly(KEEP_REJECTED_2); // should remove sub on filtered out symbol
        assertNoTasks(); // and no tasks

        ensureEventGoesThroughOn(KEEP_REJECTED); // the other symbol still works

        // Now change again to filter so that KEEP_REJECT_2 is accepted again and KEEP_INIT_REJECTED is accepted, too
        keepRejectedShaper.setSubscriptionFilter(KEEP_REJECTED_WITH_INIT_FILTER);
        assertSubAddOnly(KEEP_REJECTED_2, KEEP_INIT_REJECTED); // should add sub on symbols
        assertNoTasks(); // and no tasks

        // and all symbols work
        ensureEventGoesThroughOn(KEEP_REJECTED);
        ensureEventGoesThroughOn(KEEP_REJECTED_2);
        ensureEventGoesThroughOn(KEEP_INIT_REJECTED);

        // Now test switching to null collector on fast_0
        fast0Shaper.setCollector(null);
        adapter.updateChannel(fast0Shaper);
        assertEquals(0, executorQueue.size()); // which does not result in new tasks
        assertSubRemoveOnly(FAST_0);

        // Reconfigure all channels with empty filters one by one

        // Kill fast_0
        fast0Shaper.setSubscriptionFilter(QDFilter.NOTHING);
        assertNoTasks(); // which does not result in new tasks
        assertNoSubAddRemove(); // it was already switched to null collector and had already unsubscribed

        // Kill fast_thread
        fastThreadShaper.setSubscriptionFilter(QDFilter.NOTHING);
        executeOne(); // it creates one reconfiguration task that takes sub snapshot
        executeOne(); // which creates one more task to set sub
        assertSubRemoveOnly(FAST_THREAD);
        assertNoTasks(); // which does not result in new tasks

        // Kill slow_thread
        slowThreadShaper.setSubscriptionFilter(QDFilter.NOTHING);
        executeOne(); // it creates one refilter task
        executeOne(); // and one actual subscription update task (recreated agent without sub)
        assertSubRemoveOnly(SLOW_THREAD);
        assertNoTasks(); // which does not result in new tasks

        // Kill keep_rejected
        keepRejectedShaper.setSubscriptionFilter(QDFilter.NOTHING);
        assertSubRemoveOnly(KEEP_REJECTED, KEEP_REJECTED_2, KEEP_INIT_REJECTED);
        assertNoTasks(); // which does not result in new tasks

        // That's it!
        Thread.sleep(100); // just in case... wait a bit
        assertNoSubAddRemove();
    }

    private void assertNoTasks() {
        assertEquals(0, executorQueue.size());
    }

    private void executeOne() throws InterruptedException {
        executorQueue.take().run();
    }

    private void ensureEventGoesThroughOn(String symbol) throws InterruptedException {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.DATA);
        RecordCursor cur = buf.add(RECORD, PentaCodec.INSTANCE.encode(symbol), symbol);
        TradeMapping m = RECORD.getMapping(TradeMapping.class);
        lastPrice += 1;
        m.setPrice(cur, lastPrice);
        distributor.process(buf);
        buf.release();

        Trade trade = incomingEventsQueue.take();
        assertTrue("has event", trade != null);
        assertEquals("symbol", symbol, trade.getEventSymbol());
        assertEquals("price", lastPrice, trade.getPrice(), 0.00001);
        assertEquals("no more event", 0, incomingEventsQueue.size());
    }

    private void assertSubAddRemove(String symbol) throws InterruptedException {
        assertEquals(symbol, addSubQueue.take());
        assertEquals(symbol, remSubQueue.take());
        assertNoSubAddRemove();
    }

    private void assertSubAddOnly(String... symbols) throws InterruptedException {
        Set<String> added = new HashSet<>();
        for (String symbol : symbols)
            added.add(addSubQueue.take());
        assertEquals(new HashSet<>(Arrays.asList(symbols)), added);
        assertNoSubAddRemove();
    }

    private void assertSubRemoveOnly(String... symbols) throws InterruptedException {
        Set<String> removed = new HashSet<>();
        for (String symbol : symbols)
            removed.add(remSubQueue.take());
        assertEquals(new HashSet<>(Arrays.asList(symbols)), removed);
        assertNoSubAddRemove();
    }

    private void assertNoSubAddRemove() {
        assertEquals(0, addSubQueue.size());
        assertEquals(0, remSubQueue.size());
    }

    private TestAdapter adapter;

    private class TestFactory implements MessageAdapter.Factory {
        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return adapter = new TestAdapter();
        }
    }

    // ------------- channel shapers -------------

    private final ChannelShaper fast0Shaper;
    private final ChannelShaper fastThreadShaper;
    private final ChannelShaper slowThreadShaper;
    private final ChannelShaper keepRejectedShaper;

    {
        fast0Shaper = new ChannelShaper(QDContract.TICKER, null);
        fast0Shaper.setCollector(ticker);
        fast0Shaper.setSubscriptionFilter(FAST_0_FILTER);

        fastThreadShaper = new ChannelShaper(QDContract.TICKER, executor);
        fastThreadShaper.setCollector(ticker);
        fastThreadShaper.setSubscriptionFilter(FAST_THREAD_FILTER);

        slowThreadShaper = new ChannelShaper(QDContract.TICKER, executor);
        slowThreadShaper.setCollector(ticker);
        slowThreadShaper.setSubscriptionFilter(SLOW_THREAD_FILTER);

        keepRejectedShaper = new ChannelShaper(QDContract.TICKER, null, true);
        keepRejectedShaper.setCollector(ticker);
        keepRejectedShaper.setSubscriptionFilter(KEEP_REJECTED_FILTER);
    }

    private class TestAdapter extends AgentAdapter {
        private TestAdapter() {
            super(SCHEME, QDStats.VOID);
            // three channels for 3 different modes with 3 different symbols as filters
            initialize(fast0Shaper, fastThreadShaper, slowThreadShaper, keepRejectedShaper);
        }
    }

    private static class Wrap extends QDFilter {
        private final QDFilter delegate;
        private final CountDownLatch latch = new CountDownLatch(1);

        private Wrap(QDFilter delegate) {
            super(SCHEME);
            this.delegate = delegate;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            latch.countDown();
            return delegate.accept(contract, record, cipher, symbol);
        }

        @Override
        public boolean isFast() {
            return delegate.isFast();
        }

        @Override
        public String getDefaultName() {
            return delegate.toString();
        }

        public void await() throws InterruptedException {
            latch.await();
        }
    }
}
