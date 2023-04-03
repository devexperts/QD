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
package com.devexperts.qd.impl.matrix;

import com.devexperts.logging.Logging;
import com.devexperts.logging.TraceLogging;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.stats.QDStats;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class HistorySnapshotMTStressTest {
    private static final Logging log = Logging.getLogging(HistorySnapshotMTStressTest.class);

    private static final int N_SECS = 5; // "production" mode -- just run for 5 seconds

    private static final int N_SYMBOLS = 3;
    private static final int N_AGENTS = 3;
    private static final int MAX_RECS = 5;
    private static final int MAX_PROGRESS_WAIT = 5;

    private static final int MAX_VALUE = 5;
    private static final int N_FIXED_TIMES = 6;

    private static final int VALUE_INDEX = 2;
    private static final int SUM_INDEX = 3;

    private static final long WARN_INCONSISTENT_INTERVAL = 500;

    private static final int TX_PENDING = EventFlag.TX_PENDING.flag();
    private static final int REMOVE_EVENT = EventFlag.REMOVE_EVENT.flag();
    private static final int SNAPSHOT_BEGIN = EventFlag.SNAPSHOT_BEGIN.flag();
    private static final int SNAPSHOT_END = EventFlag.SNAPSHOT_END.flag();
    private static final int SNAPSHOT_SNIP = EventFlag.SNAPSHOT_SNIP.flag();

    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] {
            new CompactIntField(0, "Test.1"),
            new CompactIntField(1, "Test.2"),
            new CompactIntField(VALUE_INDEX, "Test.Value"),
            new CompactIntField(SUM_INDEX, "Test.Sum")
        }, new DataObjField[0]);
    private static final PentaCodec CODEC = PentaCodec.INSTANCE;
    private static final DataScheme SCHEME = new DefaultScheme(CODEC, RECORD);

    private final boolean unconflated;

    private volatile boolean stopped;

    private DistributorThread distributorThread;
    private String[] symbols; // all have zero ciphers

    private volatile BlockingQueue<Throwable> uncaughtException;
    private List<Throwable> stackTraces;

    private List<Thread> threads;
    private List<AgentThread> agents;

    @Parameterized.Parameters(name = "unconflated={0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { false },
            { true },
        });
    }

    public HistorySnapshotMTStressTest(boolean unconflated) {
        this.unconflated = unconflated;
    }

    @Before
    public void setUp() throws Exception {
        History history = new History(QDFactory.getDefaultFactory().historyBuilder()
            .withScheme(SCHEME).withStats(QDStats.VOID).withHistoryFilter(new HSF()),
            new RecordOnlyFilter(SCHEME) {
                @Override
                public boolean acceptRecord(DataRecord record) {
                    return !unconflated;
                }
            });
        distributorThread = new DistributorThread(history.distributorBuilder().build());
        stopped = false;

        TraceLogging.restart();
        symbols = new String[N_SYMBOLS];
        for (int i = 0; i < N_SYMBOLS; i++) {
            symbols[i] = "SYMBOL_" + i;
            assertEquals(0, CODEC.encode(symbols[i]));
        }

        uncaughtException = new ArrayBlockingQueue<>(1);
        stackTraces = new ArrayList<>();

        threads = new ArrayList<>();
        agents = new ArrayList<>();

        threads.add(distributorThread);
        for (int i = 0; i < N_AGENTS; i++) {
            AgentThread agent = new AgentThread(i, history.agentBuilder()
                .withHistorySnapshot(true)
                .withKeyProperties("i=" + i)
                .build());
            threads.add(agent);
            agents.add(agent);
        }
        UEH ueh = new UEH();
        for (Thread thread : threads) {
            thread.setUncaughtExceptionHandler(ueh);
        }
    }

    @Test
    public void testStress() throws InterruptedException {
        for (Thread thread : threads) {
            thread.start();
        }
        Throwable ex = null;
        try {
            for (int sec = 1; sec <= N_SECS; sec++) {
                ex = uncaughtException.poll(1, TimeUnit.SECONDS);
                if (ex != null)
                    break;
                // Makes sure that no agent hangs in "txPending" state or stalls for any other reason
                for (AgentThread agent : agents) {
                    if (!agent.makesProgress) {
                        makeStackTrace(distributorThread);
                        makeStackTrace(agent);
                        logAndFail("agent does not make progress: " + agent);
                    }
                    agent.makesProgress = false;
                }
                System.err.print(".");
                if (sec % 100 == 0)
                    System.err.println(); // for very long-running stress-tests
            }
        } catch (Throwable e) {
            e.printStackTrace();
            ex = e;
        } finally {
            System.err.println(" stopping" + (ex == null ? "" : " because of " + ex));
            stopped = true;
            for (Thread thread : threads) {
                thread.interrupt();
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }
        if (ex == null)
            ex = uncaughtException.poll();
        if (ex != null) {
            TraceLogging.dump(System.err, HistorySnapshotMTStressTest.class.getSimpleName());
            fail(ex.toString());
        }
    }

    private void makeStackTrace(Thread thread) {
        Throwable t = new Throwable(thread.getName() + " stack trace");
        t.setStackTrace(thread.getStackTrace());
        stackTraces.add(t);
    }

    private class DistributorThread extends Thread implements RecordListener {
        private final QDDistributor dist;
        private final RecordProvider addedRecordProvider;
        private final RecordProvider removedRecordProvider;
        private volatile boolean addedSubAvailable;
        private volatile boolean removedSubAvailable;

        private final RecordBuffer buf = new RecordBuffer(RecordMode.FLAGGED_DATA);
        private final Map<String, Generator> generators = new HashMap<>();
        private final Random rnd = new Random(1);

        DistributorThread(QDDistributor dist) {
            super("Distributor");
            setPriority(MIN_PRIORITY);
            this.dist = dist;
            addedRecordProvider = dist.getAddedRecordProvider();
            removedRecordProvider = dist.getRemovedRecordProvider();
            addedRecordProvider.setRecordListener(this);
            removedRecordProvider.setRecordListener(this);
        }

        @Override
        public void run() {
            while (!stopped) {
                if (addedSubAvailable) {
                    addedSubAvailable = false;
                    RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
                    if (addedRecordProvider.retrieve(sub))
                        addedSubAvailable = true;
                    RecordCursor cur;
                    while ((cur = sub.next()) != null) {
                        processAddSub(cur);
                    }
                    sub.release();
                }
                if (removedSubAvailable) {
                    removedSubAvailable = false;
                    RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
                    if (removedRecordProvider.retrieve(sub))
                        removedSubAvailable = true;
                    RecordCursor cur;
                    while ((cur = sub.next()) != null) {
                        processRemoveSub(cur);
                    }
                    sub.release();
                }
                for (Generator generator : generators.values()) {
                    generator.sendData(buf);
                }
                process();
            }
        }

        private void processAddSub(RecordCursor cur) {
            assertEquals(0, cur.getCipher());
            log.trace("### addSub " + cur.getSymbol() + "@time=" + cur.getTime());
            Generator generator = generators.get(cur.getSymbol());
            if (generator == null)
                generators.put(cur.getSymbol(), generator = new Generator(cur.getSymbol(), rnd.nextLong()));
            // randomly have generator produce data as if the actual sub is from Long.MIN_VALUE + 1
            generator.subscribe(rnd.nextBoolean() ? cur.getTime() : Long.MIN_VALUE + 1);
        }

        private void processRemoveSub(RecordCursor cur) {
            assertEquals(0, cur.getCipher());
            log.trace("### removeSub " + cur.getSymbol());
            generators.remove(cur.getSymbol());
        }

        @Override
        public void recordsAvailable(RecordProvider provider) {
            if (provider == addedRecordProvider) {
                log.trace("### addSub available notification");
                addedSubAvailable = true;
            } else if (provider == removedRecordProvider) {
                log.trace("### removeSub available notification");
                removedSubAvailable = true;
            } else
                fail();
        }

        public void process() {
            if (buf.isEmpty())
                return;
            // 95% of time send just part of buffer
            int n = rnd.nextInt(20) == 0 ? buf.size() : rnd.nextInt(buf.size()) + 1;
            long pos = buf.getPosition();
            for (int i = 0; i < n; i++)
                buf.next();
            dist.process(buf.newSource(pos, buf.getPosition()));
            buf.compact();
        }
    }

    private class Generator {
        private final String symbol;
        private final List<DataItem> data = new ArrayList<>();
        private final List<DataItem> updated = new ArrayList<>();
        private long subTime;
        private boolean resendSnapshot;
        private long pausedAtTime;
        private Random rnd;

        Generator(String symbol, long seed) {
            this.symbol = symbol;
            rnd = new Random(seed);
            prepareData();
        }

        public void subscribe(long time) {
            subTime = time;
            resendSnapshot = true;
        }

        private void prepareData() {
            Set<Long> times = new HashSet<>();
            int nRecs = rnd.nextInt(MAX_RECS - 1) + 2; // at least two times in a snapshot
            for (int i = 0; i < nRecs; i++) {
                DataItem item = new DataItem(symbol);
                do {
                    item.time = rndTime(rnd);
                } while (!times.add(item.time));
                item.value = rnd.nextInt(MAX_VALUE);
                data.add(item);
            }
            Collections.sort(data);
            int sum = 0;
            for (DataItem item : data) {
                item.sum = sum;
                sum += item.value;
            }
        }

        private void prepareUpdate() {
            int a = rnd.nextInt(data.size() - 1);
            int b = rnd.nextInt(data.size() - a - 1) + a + 1;
            // update data at interval [a,b]
            assertTrue(a < b);
            int delta = rnd.nextInt(MAX_VALUE) + 1;
            data.get(a).value += delta;
            data.get(b).value -= delta;
            int sum = data.get(a).sum;
            for (int i = a; i <= b; i++) {
                DataItem item = data.get(i);
                item.sum = sum;
                sum += item.value;
                item.updated = true;
            }
        }

        public void sendData(RecordBuffer buf) {
            if (resendSnapshot) {
                if (rnd.nextBoolean()) // 50% chance to resend snapshot as is (no modification)
                    prepareUpdate();
                sendSnapshot(buf);
                resendSnapshot = false;
            } else {
                if (pausedAtTime != 0) {
                    int badAgent = checkAgentsWhenPaused();
                    if (badAgent >= 0) {
                        // not all sub'd agents have consistent view -- continue waiting
                        long now = System.currentTimeMillis();
                        if (now > pausedAtTime + WARN_INCONSISTENT_INTERVAL) {
                            log.trace("### On paused " + symbol + " agent " + badAgent + " is not consistent");
                            pausedAtTime = now;
                        }
                        return;
                    }
                    // all agents are consistent -- resume
                    log.trace("### Resume " + symbol);
                    pausedAtTime = 0; // all agents Ok -- resume
                }
                if (rnd.nextInt(1000) == 0) {
                    log.trace("### Pause " + symbol);
                    pausedAtTime = System.currentTimeMillis(); // pause 0.1% of time to check if agents are consistent
                } else {
                    prepareUpdate();
                    sendUpdate(buf);
                }
            }
        }

        // -1 when all agents are Ok, >= 0 index of non-consistent agent
        private int checkAgentsWhenPaused() {
            for (AgentThread agent : agents) {
                if (agent.symbol == symbol && !agent.consistentView)
                    return agent.index;
            }
            return -1;
        }

        private void sendSnapshot(RecordBuffer buf) {
            int n = data.size();
            long lastTime = Long.MAX_VALUE;
            log.trace("### sendSnapshot for " + symbol);
            for (int i = 0; i < n; i++) {
                DataItem item = data.get(i);
                if (item.time < subTime)
                    break;
                RecordCursor cur = buf.add(RECORD, 0, symbol);
                cur.setTime(item.time);
                cur.setInt(VALUE_INDEX, item.value);
                cur.setInt(SUM_INDEX, item.sum);
                if (i == 0)
                    cur.setEventFlags(SNAPSHOT_BEGIN);
                if (item.time == subTime)
                    cur.setEventFlags(cur.getEventFlags() | SNAPSHOT_END);
                lastTime = item.time;
                log.trace("### Snd " + item + withFlagsStr(cur.getEventFlags()));
            }
            if (lastTime > subTime) {
                RecordCursor cur = buf.add(RECORD, 0, symbol);
                cur.setTime(subTime);
                cur.setEventFlags(REMOVE_EVENT | SNAPSHOT_END);
                if (lastTime == Long.MAX_VALUE)
                    cur.setEventFlags(cur.getEventFlags() | SNAPSHOT_BEGIN);
                log.trace("### Snd " + symbol + "@" + subTime + withFlagsStr(cur.getEventFlags()));
            }
            for (DataItem item : data) {
                item.updated = false;
            }
        }

        private void sendUpdate(RecordBuffer buf) {
            for (DataItem item : data) {
                if (item.updated) {
                    updated.add(item);
                    item.updated = false;
                }
            }
            Collections.shuffle(updated, rnd);
            int n = updated.size();
            log.trace("### sendUpdate for " + symbol);
            for (int i = 0; i < n; i++) {
                DataItem item = updated.get(i);
                RecordCursor cur = buf.add(RECORD, 0, symbol);
                cur.setTime(item.time);
                cur.setInt(VALUE_INDEX, item.value);
                cur.setInt(SUM_INDEX, item.sum);
                if (i < n - 1)
                    cur.setEventFlags(TX_PENDING);
                if (item.time < subTime) // must not send actual items outside of sub time (will kill snapshot consistency)
                    cur.setEventFlags(cur.getEventFlags() | REMOVE_EVENT);
                log.trace("### Snd " + item + withFlagsStr(cur.getEventFlags()));
            }
            updated.clear();
        }
    }

    private class AgentThread extends Thread implements RecordListener {
        private final int index;
        private final QDAgent agent;
        private final Random rnd;
        private long subTime;

        private final Map<Long, DataItem> data = new TreeMap<>();
        private boolean seenSnapshot;
        private boolean inSnapshot;
        private boolean txPending;
        private boolean emptyView;
        private int progress;

        volatile String symbol;
        volatile boolean makesProgress;
        volatile boolean consistentView;
        volatile boolean hasData;

        private final RecordSink sink = new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                appendData(cursor);
            }
        };

        AgentThread(int index, QDAgent agent) {
            super("Agent-" + index);
            this.index = index;
            this.agent = agent;
            this.rnd = new Random(index);
            agent.setRecordListener(this);
        }

        @Override
        public void run() {
            while (!stopped) {
                makeSubRun();
            }
        }

        private void makeSubRun() {
            // reset state
            progress = 0;
            seenSnapshot = false;
            inSnapshot = false;
            txPending = false;
            emptyView = false;
            consistentView = false;
            data.clear();
            // go and subscribe
            symbol = symbols[rnd.nextInt(N_SYMBOLS)];
            subTime = rndTime(rnd);
            RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
            log.trace("[" + index + "] Subscribe " + symbol + "@time=" + subTime);
            sub.add(RECORD, 0, symbol).setTime(subTime);
            agent.setSubscription(sub);
            sub.release();
            // loop
            int progressWait = rnd.nextInt(MAX_PROGRESS_WAIT) + 1;
            while (!stopped && progress < progressWait && !emptyView) {
                while (!hasData && !stopped) {
                    log.trace("[" + index + "] Park for more data");
                    LockSupport.park();
                }
                hasData = false;
                if (agent.retrieve(sink))
                    hasData = true;
            }
            // no longer waiting
            log.trace("[" + index + "] Unsubscribe " + symbol + "@time=" + subTime);
            agent.setSubscription(RecordSource.VOID);
            symbol = null;
        }

        @Override
        public void recordsAvailable(RecordProvider provider) {
            hasData = true;
            log.trace("[" + index + "] recordsAvailable unpark");
            LockSupport.unpark(this);
        }

        private void appendData(RecordCursor cursor) {
            assertEquals(RECORD, cursor.getRecord());
            assertEquals(0, cursor.getCipher());
            long time = cursor.getTime();
            int eventFlags = cursor.getEventFlags();
            txPending = (eventFlags & TX_PENDING) != 0;
            if ((eventFlags & SNAPSHOT_BEGIN) != 0) {
                seenSnapshot = true;
                inSnapshot = true;
                data.clear();
            }
            if ((time <= subTime) && (eventFlags & SNAPSHOT_END) != 0 || (eventFlags & SNAPSHOT_SNIP) != 0)
                inSnapshot = false;
            if ((eventFlags & REMOVE_EVENT) == 0) {
                DataItem item = new DataItem(symbol);
                item.time = time;
                item.value = cursor.getInt(VALUE_INDEX);
                item.sum = cursor.getInt(SUM_INDEX);
                log.trace("[" + index + "] Rcv " + item + withFlagsStr(eventFlags));
                data.put(-time, item); // in reverse order by time
            } else {
                log.trace("[" + index + "] Rcv " + symbol + "@time=" + time + withFlagsStr(eventFlags));
                data.remove(-time);
            }
            // data should be consistent if seen snapshot, not in the snapshot and not in transaction currently
            consistentView = seenSnapshot && !inSnapshot && !txPending;
            if (consistentView) {
                validateData();
                progress++;
                makesProgress = true;
                emptyView = data.isEmpty(); // will make sure bail out of main loop if our sub time got us an empty view
            }
        }

        private void validateData() {
            int sum = 0;
            for (DataItem item : data.values()) {
                if (sum != item.sum) {
                    logAndFail("[" + index + "] !!! Inconsistent " + item + ", expected sum=" + sum);
                }
                sum += item.value;
            }
        }

        @Override
        public String toString() {
            return getName() + "{" +
                "symbol='" + symbol + '\'' +
                ", subTime=" + subTime +
                ", seenSnapshot=" + seenSnapshot +
                ", inSnapshot=" + inSnapshot +
                ", txPending=" + txPending +
                ", emptyView=" + emptyView +
                '}';
        }
    }

    private void logAndFail(String msg) {
        TraceLogging.logAndStop(HistorySnapshotMTStressTest.class, msg);
        System.err.println();
        for (Throwable stackTrace : stackTraces)
            stackTrace.printStackTrace(System.err);
        fail(msg);
    }

    @Nonnull
    private static String withFlagsStr(int eventFlags) {
        return (eventFlags == 0 ? "" : " with " + EventFlag.formatEventFlags(eventFlags, MessageType.HISTORY_DATA)) +
            ((eventFlags & TX_PENDING) == 0 ? " (txEnd)" : "");
    }

    // Deterministic, but symbol-depended min history sub time
    private static class HSF implements HistorySubscriptionFilter {
        @Override
        public long getMinHistoryTime(DataRecord record, int cipher, String symbol) {
            assertEquals(0, cipher);
            return symbol.hashCode();
        }

        @Override
        public int getMaxRecordCount(DataRecord record, int cipher, String symbol) {
            return Integer.MAX_VALUE;
        }
    }

    private class UEH implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable ex) {
            System.err.println("Exception in thread " + t + ": " + ex);
            ex.printStackTrace(System.err);
            uncaughtException.offer(ex);
        }
    }

    private static long rndTime(Random rnd) {
        long result;
        do {
            // 10% of times are fully random
            result = rnd.nextInt(10) == 0 ? rnd.nextLong() :
                rnd.nextInt(N_FIXED_TIMES) * 1_000_000_000_000L;
        } while (result == Long.MIN_VALUE || Math.abs(result) == Long.MAX_VALUE);
        return result;
    }

    private static class DataItem implements Comparable<DataItem> {
        String symbol;
        long time;
        int value;
        int sum;
        boolean updated;

        DataItem(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public int compareTo(@Nonnull DataItem o) {
            return Long.compare(o.time, time);
        }

        @Override
        public String toString() {
            return symbol + "@" + time + "," + value + "," + sum;
        }
    }
}
