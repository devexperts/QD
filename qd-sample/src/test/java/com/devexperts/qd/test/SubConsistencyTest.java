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
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.impl.hash.HashFactory;
import com.devexperts.qd.kit.CompositeFilters;
import org.junit.Test;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tests that subscription is consistently represented by a sequence of add/remove subscription
 * events despite the order of initialization of added/removed subscription providers.
 * It also installs agent and distributor filters in 50% of cases and checks that those filters
 * are properly applied to subscription.
 */
public class SubConsistencyTest {
    private static final Random RND = new Random(20101221);
    private static final DataScheme SCHEME = new TestDataScheme(RND.nextLong(), TestDataScheme.Type.HAS_TIME);

    @Test
    public void testHashTicker() {
        check(new HashFactory().createTicker(SCHEME));
    }

    @Test
    public void testTicker() {
        check(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testStream() {
        check(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

    @Test
    public void testHistory() {
        check(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void check(QDCollector collector) {
        check(collector, 10, 1, 1);
        check(collector, 20, 2, 2);
        check(collector, 1000, 2, 2);
        check(collector, 2000, 2, 2);
        check(collector, 20, 2, 2000); // rehash a lot
        check(collector, 10, 1, 1);

    }

    private void check(QDCollector collector, int coreSize, int decSize, int extraSize) {
        System.out.printf("%tF %<tT : coreSize=%d, decSize=%d, extraSize=%d%n", new Date(), coreSize, decSize, extraSize);
        // for all 6! = 720 orders of DistOps
        DistOp[] ops = DistOp.values();
        do {
            if (!goodTransposition(ops))
                continue;
            Runner runner = new Runner(collector, coreSize, decSize, extraSize, ops);
            runner.run();
            runner.close();
        } while (nextTransposition(ops));
    }

    private boolean goodTransposition(DistOp[] ops) {
        int a = 0;
        int r = 0;
        for (DistOp op : ops) {
            switch (op) {
                case GET_ADDED: a = 1; break;
                case LISTEN_ADDED: if (a != 1) return false; a = 2; break;
                case RETRIEVE_ADDED: if (a != 2) return false; break;
                case GET_REMOVED: r = 1; break;
                case LISTEN_REMOVED: if (r != 1) return false; r = 2; break;
                case RETRIEVE_REMOVED: if (r != 2) return false; break;
            }
        }
        return true;
    }

    private boolean nextTransposition(DistOp[] ops) {
        int i = ops.length - 1;
        while (i > 0 && ops[i].compareTo(ops[i - 1]) < 0)
            i--;
        if (i == 0)
            return false; // all descending -- done
        int k = ops.length - 1;
        while (ops[k].compareTo(ops[i - 1]) < 0)
            k--;
        swap(ops, i - 1, k);
        transpose(ops, i, ops.length - 1);
        return true;
    }

    private void transpose(DistOp[] ops, int i, int j) {
        while (i < j)
            swap(ops, i++, j--);
    }

    private void swap(DistOp[] ops, int i, int j) {
        DistOp t = ops[i];
        ops[i] = ops[j];
        ops[j] = t;
    }

    private static class Sub {
        private final DataRecord record;
        private final String symbol;

        private Sub(DataRecord record, String symbol) {
            this.record = record;
            this.symbol = symbol;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Sub sub = (Sub) o;
            return record.equals(sub.record) && symbol.equals(sub.symbol);
        }

        @Override
        public int hashCode() {
            return 31 * record.hashCode() + symbol.hashCode();
        }

        @Override
        public String toString() {
            return symbol + ":" + record.getName();
        }
    }

    private enum Type {
        ADD, REMOVE
    }

    private enum DistOp {
        GET_ADDED {
            public void invoke(Runner runner) {
                runner.distributor.getAddedSubscriptionProvider();
            }},
        LISTEN_ADDED {
            public void invoke(Runner runner) {
                runner.distributor.getAddedSubscriptionProvider().setSubscriptionListener(
                    new Listener(runner, Type.ADD));
            }},
        RETRIEVE_ADDED {
            public void invoke(Runner runner) {
                runner.retrieve(runner.distributor.getAddedSubscriptionProvider(), Type.ADD);
            }},
        GET_REMOVED {
            public void invoke(Runner runner) {
                runner.distributor.getRemovedSubscriptionProvider();
            }},
        LISTEN_REMOVED {
            public void invoke(Runner runner) {
                runner.distributor.getRemovedSubscriptionProvider().setSubscriptionListener(
                    new Listener(runner, Type.REMOVE));
            }},
        RETRIEVE_REMOVED {
            public void invoke(Runner runner) {
                runner.retrieve(runner.distributor.getRemovedSubscriptionProvider(), Type.REMOVE);
            }};

        public abstract void invoke(Runner runner);
    }

    private static class Listener implements SubscriptionListener {
        private final Runner runner;
        private final Type type;

        private Listener(Runner runner, Type type) {
            this.runner = runner;
            this.type = type;
        }

        public void subscriptionAvailable(SubscriptionProvider provider) {
            runner.available.add(type);
        }
    }

    private static class Runner {
        private final QDCollector collector;
        private final QDFilter agentFilter;
        private final QDFilter distFilter;
        private final QDFilter combinedFilter;
        private final DistOp[] ops;
        private final QDAgent agent;
        private final int coreSize;
        private final int decSize;
        private final int extraSize;

        private final Set<Sub> coreSub = new HashSet<Sub>();
        private final Set<Sub> extraSub = new HashSet<Sub>();

        private QDDistributor distributor;

        private EnumSet<Type> available = EnumSet.noneOf(Type.class);
        private Set<Sub> viewSub = new HashSet<Sub>();
        private SubscriptionBuffer buf = new SubscriptionBuffer();

        Runner(QDCollector collector, int coreSize, int decSize, int extraSize, DistOp[] ops) {
            this.collector = collector;
            this.agentFilter = randomFilter();
            this.distFilter = randomFilter();
            this.combinedFilter = CompositeFilters.makeAnd(agentFilter, distFilter);
            this.ops = ops;
            this.agent = collector.agentBuilder()
                .withFilter(QDFilter.fromFilter(agentFilter, collector.getScheme()))
                .build();
            this.coreSize = coreSize;
            this.decSize = decSize;
            this.extraSize = extraSize;
        }

        private QDFilter randomFilter() {
            return RND.nextBoolean() ? null : CompositeFilters.makeNot(
                CompositeFilters.valueOf("" + (char) ('A' + RND.nextInt('Z' - 'A')) + "*", SCHEME));
        }

        public void run() {
            addCoreSub();
            addExtraSub();
            unitChangeSub();
            distributor = collector.distributorBuilder()
                .withFilter(QDFilter.fromFilter(distFilter, collector.getScheme()))
                .build();
            for (DistOp op : ops) {
                unitChangeSub();
                op.invoke(this);
            }
            unitChangeSub();
            // retrieve remaining sub stuff
            while (!available.isEmpty()) {
                if (RND.nextBoolean())
                    DistOp.RETRIEVE_ADDED.invoke(this);
                else
                    DistOp.RETRIEVE_REMOVED.invoke(this);
            }
            // check sub view
            checkExamineSub();
            checkExpectedSub(viewSub, combinedFilter);
        }

        public void close() {
            distributor.close();
            agent.close();
        }

        private void checkExpectedSub(Set<Sub> sub, SubscriptionFilter filter) {
            Set<Sub> expectedSub = new HashSet<Sub>(coreSub);
            expectedSub.addAll(extraSub);
            if (filter != null) {
                for (Iterator<Sub> it = expectedSub.iterator(); it.hasNext();) {
                    Sub item = it.next();
                    if (!filter.acceptRecord(item.record, SCHEME.getCodec().encode(item.symbol), item.symbol))
                        it.remove();
                }
            }                
            assertEquals(expectedSub, sub);
        }

        private void addCoreSub() {
            Set<Sub> sub = new HashSet<Sub>();
            for (int i = 0; i < coreSize; i++)
                sub.add(newSub(sub));
            agent.addSubscription(toSub(sub));
            coreSub.addAll(sub);
        }

        private void addExtraSub() {
            Set<Sub> sub = new HashSet<Sub>();
            for (int i = 0; i < extraSize; i++)
                sub.add(newSub(sub));
            agent.addSubscription(toSub(sub));
            extraSub.addAll(sub);
        }

        private void unitChangeSub() {
            Set<Sub> decSub = new HashSet<Sub>();
            for (int i = 0; i < decSize; i++) {
                Iterator<Sub> it = coreSub.iterator();
                Sub item = null;
                for (int j = RND.nextInt(coreSub.size()) + 1; j > 0; j--)
                    item = it.next();
                if (!decSub.add(item))
                    i--; // try again
            }
            decSub.addAll(extraSub);
            if (RND.nextBoolean()) {
                agent.removeSubscription(toSub(decSub));
                addExtraSub();
            } else {
                addExtraSub();
                agent.removeSubscription(toSub(decSub));
            }
            coreSub.removeAll(decSub);
            extraSub.removeAll(decSub);
        }

        private void checkExamineSub() {
            buf.clear();
            while (agent.examineSubscription(buf)) /* lust loop */;
            Set<Sub> sub = new HashSet<Sub>();
            DataRecord record;
            while ((record = buf.nextRecord()) != null) {
                Sub item = new Sub(record, SCHEME.getCodec().decode(buf.getCipher(), buf.getSymbol()));
                sub.add(item);
            }
            checkExpectedSub(sub, agentFilter);
        }

        private Sub newSub(Set<Sub> sub) {
            Sub result;
            do {
                int len = 1 + RND.nextInt(7);
                StringBuilder sb = new StringBuilder(len);
                for (int i = 0; i < len; i++)
                    sb.append((char) ('A' + RND.nextInt('Z' - 'A')));
                result = new Sub(SCHEME.getRecord(RND.nextInt(SCHEME.getRecordCount())), sb.toString());
            } while (coreSub.contains(result) || extraSub.contains(result) || sub.contains(result));
            return result;
        }

        public void retrieve(SubscriptionProvider provider, Type type) {
            buf.clear();
            if (provider.retrieveSubscription(buf))
                available.add(type);
            else
                available.remove(type);
            DataRecord record;
            while ((record = buf.nextRecord()) != null) {
                Sub item = new Sub(record, SCHEME.getCodec().decode(buf.getCipher(), buf.getSymbol()));
                if (type == Type.ADD)
                    viewSub.add(item);
                else
                    viewSub.remove(item);
            }
        }

        private SubscriptionBuffer toSub(Set<Sub> set) {
            buf.clear();
            for (Sub item : set) {
                String symbol = item.symbol;
                int cipher = SCHEME.getCodec().encode(symbol);
                if (cipher != 0 && RND.nextBoolean())
                    symbol = null; // clear string on some encoded symbols
                buf.visitRecord(item.record, cipher, symbol);
            }
            return buf;
        }

    }
}
