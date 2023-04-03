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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A suite of small single-agent single-distributor tests of {@link QDHistory} snapshot,
 * update, and transaction logic.
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class HistoryTxTest {
    private static final int TX_PENDING = EventFlag.TX_PENDING.flag();
    private static final int REMOVE_EVENT = EventFlag.REMOVE_EVENT.flag();
    private static final int SNAPSHOT_BEGIN = EventFlag.SNAPSHOT_BEGIN.flag();
    private static final int SNAPSHOT_END = EventFlag.SNAPSHOT_END.flag();
    private static final int SNAPSHOT_SNIP = EventFlag.SNAPSHOT_SNIP.flag();
    private static final int SNAPSHOT_MODE = EventFlag.SNAPSHOT_MODE.flag();

    private static final int VALUE_INDEX = 2;
    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] {
            new CompactIntField(0, "Test.1"),
            new CompactIntField(1, "Test.2"),
            new CompactIntField(VALUE_INDEX, "Test.Value")
        }, new DataObjField[0]);
    private static final PentaCodec CODEC = PentaCodec.INSTANCE;
    private static final int CIPHER = CODEC.encode("TST");
    private static final DataScheme SCHEME = new DefaultScheme(CODEC, RECORD);

    @Parameterized.Parameters(name= "blocking={0}, unconflated={1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { false, false },
            { true, false },
            { false, true },
            { true, true },
        });
    }

    private final boolean blocking;
    private final boolean unconflated;

    private HistoryImpl history;
    private QDDistributor distributor;
    private QDAgent agent;
    private QDAgent agent2;
    private boolean available;

    RecordProvider provider;
    // Blocking mode related fields
    RecordProvider blockingProvider;
    RecordListener blockingListener;

    RecordBuffer retrieveBuf = new RecordBuffer(RecordMode.FLAGGED_DATA);
    RecordBuffer distributeBuf = new RecordBuffer(RecordMode.FLAGGED_DATA);
    boolean distributeBatch;
    Runnable betweenProcessPhases;

    public HistoryTxTest(boolean blocking, boolean unconflated) {
        this.blocking = blocking;
        this.unconflated = unconflated;
    }

    @Before
    public void setUp() throws Exception {
        history = new HistoryImpl(QDFactory.getDefaultFactory().historyBuilder()
            .withScheme(SCHEME)
            .withStats(QDStats.VOID)
            .withHistoryFilter(new HSF()));
        distributor = history.distributorBuilder().build();

        history.setErrorHandler(new QDErrorHandler() {
            @Override
            public void handleDataError(DataProvider provider, Throwable t) {
                throw new AssertionError(t);
            }

            @Override
            public void handleSubscriptionError(SubscriptionProvider provider, Throwable t) {
                throw new AssertionError(t);
            }
        });
    }

    @Test
    public void testLegacyToSnapshotUpdate() {
        createAgent(0, true);
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now distribute snapshot with just two (different) items
        distribute(3, 13, SNAPSHOT_BEGIN);
        distribute(1, 14, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        // ---
        // History snapshot supporting agent just receives fresh snapshot and also removes legacy data.
        if (blocking) {
            // in blocking mode all of them are TX_PENDING (starts retrieval from non-complete HB)
            expectMore(3, 13, TX_PENDING | SNAPSHOT_BEGIN); // now there is a snapshot(!)
            expectMore(1, 14, TX_PENDING);
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        } else {
            expectMore(3, 13, SNAPSHOT_BEGIN); // now there is a snapshot(!)
            expectMore(1, 14, 0);
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        }
    }

    @Test
    public void testLegacyToSnapshotUpdateDirty() {
        createAgent(0, true);
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now distribute snapshot with just two (different) items and (dirty) receive after every distribute
        distribute(3, 13, SNAPSHOT_BEGIN);
        expectJust(3, 13, TX_PENDING | SNAPSHOT_BEGIN); // now there is a snapshot(!), but dirty
        //
        distribute(1, 14, 0);
        expectJust(1, 14, TX_PENDING);
        //
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT);
    }

    @Test
    public void testLegacyToSnapshotUpdateLegacyAgent() {
        createAgent(0, false); // legacy agent (does not support history tx/snapshot protocol)
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now distribute snapshot with just two (different) items
        distribute(3, 13, SNAPSHOT_BEGIN);
        distribute(1, 14, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        // ---
        // Legacy agent receive updated data items and removals for old data items (no flags for legacy agent)
        expectMore(3, 13, 0);
        expectMore(2, 0, 0); // this was removed
        expectJust(1, 14, 0);
    }

    @Test
    public void testLegacyCleanupWithSnapshot() {
        createAgent(0, true);
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now distribute empty snapshot
        distribute(0, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // --
        // Expect that the snapshot is just delivered downstream (will remove old data)
        expectJust(0, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
    }

    @Test
    public void testLegacyCleanupWithSnapshotLegacyAgent() {
        createAgent(0, false); // legacy agent (does not support history tx/snapshot protocol)
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now distribute empty snapshot
        distribute(0, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // --
        // Expect that the data is removed for legacy agent (not flags -- just zero data)
        expectMore(3, 0, 0);
        expectMore(2, 0, 0);
        expectJust(1, 0, 0);
    }

    @Test
    public void testLegacyCleanupWithSnapshotBelowSub() {
        createAgent(2, true); // sub only time >= 2 (with no item at sub)
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectJust(2, 11, 0); // only subscribed items received
        // now distribute empty snapshot below subscribed time
        distribute(0, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // ---
        // Expect that the snapshot is just delivered downstream (will remove old data)
        // .. however, time goes up to sub time only
        expectJust(2, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
    }

    @Test
    public void testLegacyCleanupWithSnapshotBelowSubLegacyAgent() {
        createAgent(2, false); // legacy agent, sub only time >= 2 (with no item at sub)
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectJust(2, 11, 0); // only subscribed items received
        // now distribute empty snapshot below subscribed time
        distribute(0, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // ---
        // Expect that the sub'd data is removed for legacy agent (not flags -- just zero data)
        expectMore(3, 0, 0);
        expectJust(2, 0, 0);
    }

    @Test
    public void testLegacyPartialRetrieveReplaceWithSnapshot() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        // retrieve only time==3
        expectMore(3, 10, 0);
        // now distribute the same data with flags
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // --
        // Expect that the full snapshot is delivered downstream
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
    }

    @Test
    public void testLegacyPartialRetrieveReplaceWithSnapshotLegacyAgent() {
        createAgent(0, false); // legacy agent
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        // retrieve only time==3
        expectMore(3, 10, 0);
        // now distribute the same data with flags
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // --
        // Expect only new data items to be distributed to the legacy agent
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
    }

    @Test
    public void testSimpleSnapshotUpdate() {
        distribute(4, 10, 0); // will get lost -- no sub
        createAgent(0, true);
        // send data in legacy mode (not marked with flags)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0); // receive w/o SNAPSHOT_BEGIN and SNAPSHOT_END (legacy)
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // ---
        distribute(0, 0, REMOVE_EVENT);
        expectNothing(); // removed does need to be reported in legacy mode
        // confirm snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN); // expect snapshot to be fully resent with SNAPSHOT_BEGIN
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // update snapshot
        distribute(4, 13, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(4, 13, TX_PENDING | SNAPSHOT_BEGIN);
        } else {
            expectJust(4, 13, TX_PENDING);
        }
        // ---
        distribute(3, 14, 0);
        // ---
        expectJust(3, 14, TX_PENDING);
        // ---
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        if (unconflated) {
            expectMore(2, 11, TX_PENDING);
            expectJust(1, 12, TX_PENDING);
        } else {
            expectNothing();
        }
        // ---
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // gets augmented with snapshot end (it goes to the sub time)
    }

    @Test
    public void testSnapshotSweepRemoveClean() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // send full snapshot with only 1 items left (confirm it)
        distribute(1, 13, SNAPSHOT_BEGIN);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        if (unconflated) {
            expectMore(1, 13, (blocking ? TX_PENDING : 0) | SNAPSHOT_BEGIN);
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(2, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(0, 0, REMOVE_EVENT);
        }
    }

    @Test
    public void testSnapshotSweepRemoveExamine() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // send full snapshot with only 1 items left (confirm it)
        distribute(1, 13, SNAPSHOT_BEGIN);
        // now examine -- it shall be in transaction
        examineAll();
        expectMore(1, 13, SNAPSHOT_BEGIN | TX_PENDING);
        expectJust(0, 14, SNAPSHOT_END | TX_PENDING);
        // send snapshot end
        distribute(0, 14, SNAPSHOT_END);
        // now examine -- it shall be consistent
        examineAll();
        expectMore(1, 13, SNAPSHOT_BEGIN);
        expectJust(0, 14, SNAPSHOT_END);
    }

    @Test
    public void testSnapshotSweepRemoveDirty() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // send snapshot with only 1 items left (confirm it), but don't end it
        distribute(1, 13, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(1, 13, TX_PENDING | SNAPSHOT_BEGIN);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(2, 0, REMOVE_EVENT | TX_PENDING);
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        if (unconflated) {
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        } else {
            expectJust(0, 0, REMOVE_EVENT);
        }
    }

    @Test
    public void testSnapshotSweepRemoveDirtyLegacyAgent() {
        createAgent(0, false); // legacy agent
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, 0);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, 0);
        // send snapshot with only 1 items left (confirm it)
        distribute(1, 13, SNAPSHOT_BEGIN);
        // ---
        expectMore(4, 0, 0);
        expectMore(3, 0, 0);
        expectJust(2, 0, 0);
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        expectJust(0, 0, 0);
    }

    @Test
    public void testSnapshotSweepRemovePartSub1() {
        createAgent(2, true); // sub only time >= 2
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0); // no sub
        distribute(0, 14, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END);
        // send snapshot with only 1 items left (confirm it), but outside of sub (!)
        distribute(1, 13, SNAPSHOT_BEGIN); // also implicit snapshot end for timeSub=2
        // ---
        if (unconflated) {
            expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_END);
        } else if (blocking) {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(2, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(2, 0, REMOVE_EVENT);
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END); // finish snapshot (outside of sub)
        expectNothing();
    }

    @Test
    public void testSnapshotSweepRemovePartSub2() {
        createAgent(2, true); // sub only time >= 2 (with no item at sub)
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(1, 13, 0); // no sub
        distribute(0, 14, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_END);
        // send snapshot with only 1 items left (confirm it), but outside of sub (!)
        distribute(1, 13, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_END);
        } else if (blocking) {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(3, 0, REMOVE_EVENT); // end if TX was optimized to the last buffered event
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END); // finish snapshot (outside of sub)
        expectNothing();
    }

    @Test
    public void testSnapshotSweepRemoveUpdate() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // send snapshot with only 1 items left (update it!)
        distribute(1, 15, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(1, 15, SNAPSHOT_BEGIN | TX_PENDING);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(2, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(1, 15, TX_PENDING);
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        if (unconflated) {
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        } else {
            expectJust(0, 0, REMOVE_EVENT);
        }
    }

    @Test
    public void testSnapshotSweepRemoveUpdatePartSub() {
        createAgent(2, true); // sub only time >= 2
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0); // no sub
        distribute(0, 14, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END);
        // send snapshot with only 1 items left (update it!)
        distribute(1, 15, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_END);
        } else if (blocking) {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(2, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(2, 0, REMOVE_EVENT);
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        expectNothing();
    }

    @Test
    public void testSnapshotSweepClearTx() {
        // [QD-1434] Sweeping snapshot in History causes endless TX_PENDING
        history.setStoreEverything(true);

        // Send initial snapshot
        distribute(2, 1, SNAPSHOT_BEGIN);
        distribute(1, 1, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END | SNAPSHOT_SNIP);

        // Send change in the snapshot, thus causing sweep
        distribute(2, 1, SNAPSHOT_BEGIN);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END | SNAPSHOT_SNIP);

        createAgent(0, true);

        // TX_PENDING should not be present
        expectMore(2, 1, SNAPSHOT_BEGIN);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
    }

    @Test
    public void testSnapshotInsert() {
        createAgent(0, true);
        // send snapshot (3 even items)
        distribute(6, 10, SNAPSHOT_BEGIN);
        distribute(4, 11, 0);
        distribute(2, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectMore(6, 10, SNAPSHOT_BEGIN);
        expectMore(4, 11, 0);
        expectMore(2, 12, 0);
        expectJust(0, 13, SNAPSHOT_END);
        // send snapshot with 5 items (add two more)
        distribute(6, 10, SNAPSHOT_BEGIN);
        distribute(4, 11, 0);
        distribute(3, 14, 0);
        distribute(2, 12, 0);
        distribute(1, 15, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        if (unconflated) {
            // Snapshot repeats previously retrieved data
            expectMore(6, 10, SNAPSHOT_BEGIN);
            expectMore(4, 11, 0);
            // In blocking mode updated event cause TX_PENDING flag to be set
            expectMore(3, 14, blocking ? TX_PENDING : 0);
            expectMore(2, 12, blocking ? TX_PENDING : 0);
            expectMore(1, 15, blocking ? TX_PENDING : 0);
            expectJust(0, 13, SNAPSHOT_END); // explicit TX_END
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(3, 14, TX_PENDING);
            expectMore(1, 15, TX_PENDING); // still pending
            expectJust(0, 13, 0); // explicit TX_END
        } else {
            expectMore(3, 14, TX_PENDING);
            expectJust(1, 15, 0); // optimized end of transaction
        }
    }

    @Test
    public void testSnapshotInsertPartSub1() {
        createAgent(2, true); // sub only time >= 2
        // send snapshot (3 even items)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(0, 12, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectJust(2, 11, SNAPSHOT_END);

        // send snapshot with 5 items (add two more)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 13, 0);
        distribute(2, 11, 0);
        distribute(1, 14, 0);
        distribute(0, 12, SNAPSHOT_END);
        // ---
        if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 13, blocking ? TX_PENDING : 0);
            expectJust(2, 11, SNAPSHOT_END);
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(3, 13, TX_PENDING); // still pending
            expectJust(2, 11, 0); // this event delivered to end tx, even though it is not updated and outside sub
        } else {
            expectJust(3, 13, 0); // optimized TX_END here, because that item at time=2 did not update
        }
    }

    @Test
    public void testSnapshotInsertPartSub2() {
        createAgent(3, true); // sub only time >= 3
        // send snapshot (3 even items)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(0, 12, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectJust(3, 0, REMOVE_EVENT | SNAPSHOT_END); // virtual SnapshotEnd
        // send snapshot with 5 items (add two more)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 13, 0);
        distribute(2, 11, 0);
        distribute(1, 14, 0);
        distribute(0, 12, SNAPSHOT_END);
        // ---
        if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectJust(3, 13, SNAPSHOT_END);
        } else {
            expectJust(3, 13, 0); // only one items updates in sub range (no TX)
        }
    }

    @Test
    public void testSnapshotInsertPartSub3() {
        createAgent(1, true); // sub only time >= 1
        // send snapshot (3 even items)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(0, 12, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 0, REMOVE_EVENT | SNAPSHOT_END); // virtual SnapshotEnd
        // send snapshot with 5 items (add two more)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 13, 0);
        distribute(2, 11, 0);
        distribute(1, 14, 0);
        distribute(0, 12, SNAPSHOT_END);
        // ---
        if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 13, blocking ? TX_PENDING : 0);
            expectMore(2, 11, blocking ? TX_PENDING : 0);
            expectJust(1, 14, SNAPSHOT_END);
        } else {
            expectMore(3, 13, TX_PENDING);
            expectJust(1, 14, 0);
        }
    }

    @Test
    public void testSnapshotInsertPartSub4() {
        createAgent(3, true); // sub only time >= 3
        // send snapshot (3 items with time == 0 mod 4)
        distribute(8, 10, SNAPSHOT_BEGIN);
        distribute(4, 11, 0);
        distribute(0, 12, SNAPSHOT_END); // no sub
        // ---
        expectMore(8, 10, SNAPSHOT_BEGIN);
        expectMore(4, 11, 0);
        expectJust(3, 0, REMOVE_EVENT | SNAPSHOT_END); // virtual SnapshotEnd
        // send snapshot with 5 items (add two more -- all even times now)
        distribute(8, 10, SNAPSHOT_BEGIN); // confirm
        distribute(6, 13, 0); // new
        distribute(4, 11, 0); // confirm
        distribute(2, 14, 0); // new, no sub
        distribute(0, 12, SNAPSHOT_END); // confirm, no sub
        // ---
        if (unconflated) {
            expectMore(8, 10, SNAPSHOT_BEGIN);
            expectMore(6, 13, blocking ? TX_PENDING : 0);
            expectMore(4, 11, blocking ? TX_PENDING : 0);
            expectJust(3, 0, SNAPSHOT_END | REMOVE_EVENT);
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(6, 13, TX_PENDING); // still pending
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT); // this event ends tx even though it is below sub
        } else {
            expectJust(6, 13, 0); // optimized tx end
        }
    }

    @Test
    public void testSnapshotInsertPartSub5() {
        createAgent(1, true); // sub only time >= 1
        // send snapshot (3 items with time == 0 mod 4)
        distribute(8, 10, SNAPSHOT_BEGIN);
        distribute(4, 11, 0);
        distribute(0, 12, SNAPSHOT_END); // no sub
        // ---
        expectMore(8, 10, SNAPSHOT_BEGIN);
        expectMore(4, 11, 0);
        expectJust(1, 0, REMOVE_EVENT | SNAPSHOT_END); // virtual SnapshotEnd
        // send snapshot with 5 items (add two more -- all even times now)
        distribute(8, 10, SNAPSHOT_BEGIN); // confirm
        distribute(6, 13, 0); // new
        distribute(4, 11, 0); // confirm
        distribute(2, 14, 0); // new
        distribute(0, 12, SNAPSHOT_END); // confirm, no sub
        // ---
        if (unconflated) {
            expectMore(8, 10, SNAPSHOT_BEGIN);
            expectMore(6, 13, blocking ? TX_PENDING : 0);
            expectMore(4, 11, blocking ? TX_PENDING : 0);
            expectMore(2, 14, blocking ? TX_PENDING : 0);
            expectJust(1, 0, SNAPSHOT_END | REMOVE_EVENT);
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(6, 13, TX_PENDING);
            expectMore(2, 14, TX_PENDING); // still pending
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT); // tx end, even though it is below sub AND did not update anything
        } else {
            expectMore(6, 13, TX_PENDING);
            expectJust(2, 14, 0); // optimized tx end
        }
    }

    @Test
    public void testTxSimple() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // update two items in tx
        distribute(3, 15, TX_PENDING);
        distribute(2, 16, 0);
        // ---
        expectMore(3, 15, TX_PENDING);
        expectJust(2, 16, 0);
        // update two more items in tx
        distribute(0, 17, TX_PENDING);
        distribute(3, 18, 0);
        // ---
        expectMore(0, 17, TX_PENDING);
        expectJust(3, 18, 0);
    }

    @Test
    public void testTxConflate() {
        createAgent(0, true);
        // send small snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(0, 12, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(0, 12, SNAPSHOT_END);
        // send tx update and conflate on the same event
        distribute(4, 13, TX_PENDING);
        distribute(4, 14, 0);
        //--
        if (unconflated) {
            expectMore(4, 13, TX_PENDING);
            expectJust(4, 14, 0);
        } else {
            expectJust(4, 14, 0);
        }
    }

    @Test
    public void testTxDirty1() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN); // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING); // here and lower -- all pending
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_END);
        // continue transaction at time=1
        distribute(1, 16, TX_PENDING);
        expectJust(1, 16, TX_PENDING);
        // finish transaction at time=3
        distribute(3, 17, 0);
        expectJust(3, 17, 0);
    }

    @Test
    public void testTxDirty1a() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN); // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_END);
        // continue transaction at time=1 and finish at time=3
        distribute(1, 16, TX_PENDING);
        distribute(3, 17, 0);
        // check both
        expectMore(1, 16, TX_PENDING);
        expectJust(3, 17, 0);
    }

    @Test
    public void testTxDirty1b() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN);  // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_END);
        // continue transaction at time=1 and finish at time=3 (but here are no actual updates!)
        distribute(1, 13, TX_PENDING); // no changes
        distribute(3, 11, 0);          // no changes
        // check
        expectJust(3, 11, 0);    // <- only last TX_END comes in
    }

    // the same as above, but retrieve everything in one batch
    @Test
    public void testTxDirty1bBatchRetrieve() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        retrieveBatch(5); // BATCH RETRIEVE
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN); // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING); // here and lower -- all pending
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_END);
        // continue transaction at time=1 and finish at time=3 (but here are no actual updates!)
        distribute(1, 13, TX_PENDING); // no changes
        distribute(3, 11, 0);          // no changes
        // check
        expectJust(3, 11, 0);    // <- only last TX_END comes in
    }

    @Test
    public void testTxNonDirtyUpdates1() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // Retrieve all
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // do some updates
        distribute(3, 15, 0);
        distribute(2, 16, 0);
        // do couple transactions
        distribute(3, 17, TX_PENDING);
        distribute(2, 18, 0);
        distribute(3, 19, TX_PENDING);
        distribute(2, 20, 0);
        // retrieve all updates and transactions
        expectMore(3, 15, 0);
        expectMore(2, 16, 0);
        expectMore(3, 17, TX_PENDING);
        expectMore(2, 18, 0);
        expectMore(3, 19, TX_PENDING);
        expectJust(2, 20, 0);
    }

    @Test
    public void testTxNonDirtyUpdates2() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // Retrieve snapshot just up to time == 2
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        // do some updates below knowTime (non dirty!!!)
        distribute(1, 15, 0);
        distribute(0, 16, 0);
        // do couple transactions below knowTime (non dirty!!!)
        distribute(1, 17, TX_PENDING);
        distribute(0, 18, 0);
        distribute(1, 19, TX_PENDING);
        distribute(0, 20, 0);
        // retrieve remaining part of snapshot (non dirty -- incorporates latest TX result)
        expectMore(1, 19, 0);
        expectJust(0, 20, SNAPSHOT_END);
    }

    @Test
    public void testTxDirtyUpdates1() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // Retrieve snapshot just up to time == 2
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        // do some updates
        distribute(3, 15, 0);
        distribute(2, 16, 0);
        // do couple transactions
        distribute(3, 17, TX_PENDING);
        distribute(2, 18, 0);
        distribute(3, 19, TX_PENDING);
        distribute(2, 20, 0);
        // retrieve remaining part of snapshot (DIRTY)
        expectMore(1, 13, TX_PENDING);
        expectMore(0, 14, TX_PENDING | SNAPSHOT_END);
        // retrieve all updates and transactions (all DIRTY, last buffered ends TX)
        expectMore(3, 15, TX_PENDING);
        expectMore(2, 16, TX_PENDING);
        expectMore(3, 17, TX_PENDING);
        expectMore(2, 18, TX_PENDING);
        expectMore(3, 19, TX_PENDING);
        expectJust(2, 20, 0);
    }

    @Test
    public void testTxDirtyUpdates2() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // Retrieve snapshot just up to time == 2
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        // do some updates
        distribute(3, 15, 0);
        distribute(2, 16, 0);
        // do couple transactions
        distribute(3, 17, TX_PENDING);
        distribute(2, 18, 0);
        distribute(3, 19, TX_PENDING);
        distribute(2, 20, 0);
        // retrieve remaining part of snapshot (DIRTY)
        expectMore(1, 13, TX_PENDING);
        expectMore(0, 14, TX_PENDING | SNAPSHOT_END);
        // retrieve all updates and transactions (all DIRTY, last buffered ends TX)
        expectMore(3, 15, TX_PENDING);
        expectMore(2, 16, TX_PENDING);
        expectMore(3, 17, TX_PENDING);
        expectMore(2, 18, TX_PENDING);
        expectMore(3, 19, TX_PENDING);
        expectJust(2, 20, 0);
    }

    @Test
    public void testTxSnapshotTxEndDeliveryBelowTimeSub() {
        createAgent(2, true); // subscribe for >= 2 (!)
        distribute(4, 10, TX_PENDING | SNAPSHOT_BEGIN);
        distribute(3, 11, TX_PENDING);
        distribute(2, 12, TX_PENDING);
        distribute(1, 13, TX_PENDING); // no sub
        distribute(0, 14, TX_PENDING | SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(3, 11, TX_PENDING);
        expectJust(2, 12, TX_PENDING | SNAPSHOT_END); // here and lower (last sub-d item!)
        // finish continue transaction below this agent's sub
        distribute(1, 16, TX_PENDING);
        distribute(0, 17, 0);
        expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT); // expect to receive virtual REMOVE_EVENT because of TX_END
    }

    @Test
    public void testTxDirtyTxEndDeliveryBelowTimeSub() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(2, true); // subscribe for >= 2 (!)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0); // no sub
        distribute(0, 14, SNAPSHOT_END); // no sub
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN); // dirty!
        expectMore(3, 11, TX_PENDING);
        expectJust(2, 15, TX_PENDING | SNAPSHOT_END);
        // finish continue transaction below this agent's sub
        distribute(1, 16, TX_PENDING);
        distribute(0, 17, 0);
        expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT); // expect to receive virtual REMOVE_EVENT because of TX_END
    }

    @Test
    public void testSnapshotUpdateTxEndDeliveryBelowTimeSub1() {
        createAgent(2, true); // subscribe for >= 2 (!)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0); // no sub
        distribute(0, 14, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END); // last sub-d item
        // update snapshot (drop all items)
        distribute(0, 0, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_END);
        // --
        if (unconflated) {
            expectJust(2, 0, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_END);
        } else if (blocking) {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            // will be split into two events
            expectMore(2, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            // tx end will collapse into one event
            expectJust(2, 0, REMOVE_EVENT); // no more dirty at this item
        }
    }

    @Test
    public void testSnapshotUpdateTxEndDeliveryBelowTimeSub2() {
        createAgent(2, true); // subscribe for >= 2, but there will be no actual items at time == 2
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(1, 12, 0); // no sub
        distribute(0, 13, SNAPSHOT_END); // no sub
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_END); // just a virtual SnapshotEnd event
        // update snapshot (drop all items)
        distribute(0, 0, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_END);
        // --
        if (unconflated) {
            expectJust(2, 0, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_END);
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING); // still pending
            expectJust(Long.MAX_VALUE, 0, REMOVE_EVENT); // event to end tx
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(3, 0, REMOVE_EVENT); // optimized tx end here
        }
    }

    @Test
    public void testFlagsOnLastEventConflationWithRebase() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // now update two events in transaction
        distribute(2, 14, TX_PENDING);
        distribute(1, 15, 0);
        // but don't retrieve them just yet (these two updates are in the agent buffer)
        // next snapshot with 1 level update (to the same event and time==1)
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 14, 0);
        distribute(1, 16, 0); // update this
        history.forceRebase(agent);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // now retrieve updates
        if (unconflated && blocking) {
            expectMore(2, 14, TX_PENDING);
            expectMore(1, 15, 0);
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 11, 0);
            expectMore(2, 14, 0);
            expectMore(1, 16, TX_PENDING);
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        } else if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 11, 0);
            expectMore(2, 14, 0);
            expectMore(1, 16, 0);
            expectMore(0, 0, SNAPSHOT_END | REMOVE_EVENT);
            // tricky case, where "available" is true, but nothing retrieves, because events were unlinked
            expectNothingRetrieves();
        } else if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
            expectMore(2, 14, TX_PENDING); // this was in TX
            expectMore(1, 16, TX_PENDING); // still pending
            expectJust(0, 0, REMOVE_EVENT); // explicit remove event to end tx
        } else {
            expectMore(2, 14, TX_PENDING); // this was in TX
            expectJust(1, 16, 0); // and this in TX (conflated to a single update and optimized to end tx)
        }
    }

    // REGRESSION: Reproduces unlinked records handling error in History.rebuildLastRecordAndRebase (see QD-1391)
    @Test
    public void testUnlinkedReturnAfterRebase() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);

        distribute(2, 14, TX_PENDING);
        distribute(1, 15, 0);
        setSubTime(0); // marks 2 events as UNLINKED and re-sets timeKnown = Long.MAX_VALUE
        history.forceRebase(agent);
        setSubTime(0); // checks UNLINKED and fails if they returned (in RecordBuffer.unlinkFrom)
    }

    // REGRESSION: Reproduces unlinked records handling error in History.rebuildLastRecordAndRebase (see QD-1391)
    @Test
    public void testUnlinkedReturnAfterRebase2() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end

        // now update two events in transaction
        distribute(2, 14, TX_PENDING);
        distribute(1, 15, 0);

        setSubTime(0); // marks 2 events as UNLINKED and re-sets timeKnown = Long.MAX_VALUE
        history.forceRebase(agent);

        distribute(4, 10, SNAPSHOT_BEGIN); // checks UNLINKED and fails if they returned (in RecordBuffer.unlinkFrom)
    }

    // REGRESSION: Reproduces unlinked records handling error in History.rebuildLastRecordAndRebase (see QD-1391)
    @Test
    public void testUnlinkedReturnAfterRebase3() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end

        // now update two events in transaction
        distribute(2, 14, TX_PENDING);
        distribute(1, 15, 0);

        setSubTime(0); // marks 2 events as UNLINKED and re-sets timeKnown = Long.MAX_VALUE
        history.forceRebase(agent);

        if (blocking) {
            expectMore(2, 14, TX_PENDING);
            expectMore(1, 15, 0);
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 11, 0);  // <- go up to 3
        } else {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 11, 0);   // <- go up to 3
        }
        distribute(4, 10, TX_PENDING);
        retrieveBatch(1); // first retrieval attempt fails in RecordBuffer.flagFrom invoked via makeAgentTxDirty
    }

    @Test
    public void testComplexTxUpdateSequence() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // now update two events in transaction
        distribute(2, 14, TX_PENDING);
        distribute(1, 15, 0);
        // ---
        expectMore(2, 14, TX_PENDING);
        expectJust(1, 15, 0);
        // next snapshot with 2 level update
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 17, 0);  // update this
        distribute(2, 16, 0);  // and this
        distribute(1, 15, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // and one more update separately on the last updated time
        distribute(2, 17, 0);
        // ---
        if (unconflated && blocking) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 17, TX_PENDING);
            expectMore(2, 16, TX_PENDING);
            expectMore(1, 15, TX_PENDING);
            expectMore(0, 0, REMOVE_EVENT | SNAPSHOT_END);
            expectJust(2, 17, 0);
        } else if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 17, 0);
            expectMore(2, 17, 0);
            expectMore(1, 15, 0);
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        } else {
            expectMore(3, 17, TX_PENDING);  // update this
            if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
                expectMore(2, 16, TX_PENDING);  // pending
                expectMore(0, 0, REMOVE_EVENT); // explicit tx end with remove event
                expectJust(2, 17, 0);  // one more update
            } else {
                expectJust(2, 17, 0);  // and this ends transaction (optimization and conflation)
            }
            // do transaction that if finished by remove event
            distribute(2, 18, TX_PENDING);
            distribute(0, 0, REMOVE_EVENT);
            // ---
            if (blocking) { // cannot optimize tx end in blocking mode (just one record in buffer)
                expectMore(2, 18, TX_PENDING); // pending
                expectJust(0, 0, REMOVE_EVENT); // delivered tx end
            } else {
                expectJust(2, 18, 0); // optimized end of transaction
            }
        }
    }

    @Test
    public void testTxUnSubPart() {
        createAgent(0, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // now change subTime to 2
        setSubTime(2);
        // shall receive snapshot to time 2
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END);
        // and again change subTime 0
        setSubTime(0);
        // shall receive data to time 2 (only) because snapshot to time 0 was lost
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, 0); // <-- there is no SNAPSHOT_END here -- wait until upstream fills it in
        // now upstream data provider confirms snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        // ---
        if (unconflated) {
            expectMore(4, 10, SNAPSHOT_BEGIN);
            expectMore(3, 11, 0);
            expectMore(2, 12, 0);
            expectMore(1, 13, 0);
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        } else {
            expectMore(1, 13, 0);
            expectJust(0, 0, SNAPSHOT_END | REMOVE_EVENT); // virtual snapshot end
        }
    }

    @Test
    public void testStoreEverything() {
        history.setStoreEverything(true);
        distribute(1, 10, 0); // will not get lost storeEverything (sic!)
        createAgent(0, true);
        expectJust(1, 10, 0);
        // distribute more
        distribute(2, 11, 0);
        expectJust(2, 11, 0);
        // reopen agent and check that data is still there
        closeAgent();
        createAgent(0, true);
        // ---
        expectMore(2, 11, 0);
        expectJust(1, 10, 0);
        // close agent, do remove, create again
        closeAgent();
        removeData();
        createAgent(0, true);
        // ---
        expectNothing();
    }

    @Test
    public void testStoreEverything2() {
        history.setStoreEverything(true);
        distribute(1, 10, 0); // will not get lost storeEverything (sic!)
        distribute(2, 11, 0);
        // create agent
        createAgent(0, true);
        // expect stored data
        expectMore(2, 11, 0);
        expectJust(1, 10, 0);
    }

    @Test
    public void testStoreEverythingSnipThenSnapshot() {
        history.setStoreEverything(true);
        // distribute without flags first
        distribute(7, 10, 0);
        distribute(6, 11, 0);
        distribute(5, 12, 0);
        // ---
        examineAll();
        expectMore(7, 10, 0);
        expectMore(6, 11, 0);
        expectJust(5, 12, 0);
        // remove event with time=6
        distribute(6, 0, REMOVE_EVENT);
        // ---
        examineAll();
        expectMore(7, 10, 0);
        expectJust(5, 12, 0);
        // now snip at time=6
        distribute(6, 0, SNAPSHOT_SNIP);
        // --- snapshot mode was NOT turned on yet (no SNAPSHOT_BEGIN/ENABLE seen)
        examineAll();
        expectMore(7, 10, 0);
        expectJust(6, 0, SNAPSHOT_SNIP); // but snapshot snip is recorded in HB
        // now distribute snapshot with 10 events
        distribute(9, 19, SNAPSHOT_BEGIN);
        for (int i = 8; i >= 1; i--)
            distribute(i, 10 + i, 0);
        distribute(0, 20, SNAPSHOT_END);
        // ---
        examineAll();
        expectMore(9, 19, SNAPSHOT_BEGIN);
        for (int i = 8; i >= 1; i--)
            expectMore(i, 10 + i, 0);
        expectJust(0, 20, SNAPSHOT_END);
    }

    @Test
    public void testSnapshotThenEnable() {
        createAgent(0, true);
        // distribute legacy
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // retrieve full
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // then enable snapshot with SNAPSHOT_MODE
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_MODE);
        // should retrieve fully formed snapshot
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
    }

    @Test
    public void testSnapshotSnipThenEnable() {
        createAgent(-1, true);
        // distribute legacy
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // retrieve full
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // then snip with SNAPSHOT_SNIP
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        // receive only SNAPSHOT_SNIP
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        // then enable snapshot with SNAPSHOT_MODE
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_MODE);
        // should retrieve fully formed snapshot
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
    }

    @Test
    public void testSnapshotMode() {
        createAgent(1, true);
        // send "snapshot mode" incantation
        distribute(Long.MAX_VALUE, 0, REMOVE_EVENT | SNAPSHOT_MODE);
        // nothing goes downstream
        expectNothing();
        // distribute rest of stuff
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // receive in snapshot mode
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 12, SNAPSHOT_END);
    }

    @Test
    public void testSnapshotModeThenSnip() {
        createAgent(-1, true);
        // send "snapshot mode" incantation
        distribute(Long.MAX_VALUE, 0, REMOVE_EVENT | SNAPSHOT_MODE);
        // nothing goes downstream
        expectNothing();
        // distribute rest of stuff
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // send "snapshot snip" to signal snapshot complete for any subscriber
        distribute(0, 0, SNAPSHOT_SNIP);
        // receive in snapshot mode
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, SNAPSHOT_SNIP);
    }

    // test that HB resize and buffer refilter logic all works correctly
    @Test
    public void testBigSnapshot() {
        if (unconflated)
            return;

        createAgent(0, true);
        // submit big snapshot
        int n = 100;
        for (int i = n; i >= 0; i--)
            distribute(i, i, i == n ? SNAPSHOT_BEGIN : i == 0 ? SNAPSHOT_END : 0);
        // check that it arrives
        for (int i = n; i >= 0; i--)
            expect(i, i, i == n ? SNAPSHOT_BEGIN : i == 0 ? SNAPSHOT_END : 0);
        expectNothing();
        // repeat this big snapshot (send again)
        for (int i = n; i >= 0; i--)
            distribute(i, i, i == n ? SNAPSHOT_BEGIN : i == 0 ? SNAPSHOT_END : 0);
        // nothing happens
        expectNothing();
        // update this big snapshot (change values)
        for (int i = n; i >= 0; i--)
            distribute(i, i + n, i == n ? SNAPSHOT_BEGIN : i == 0 ? SNAPSHOT_END : 0);
        // check that changes arrive
        for (int i = n; i >= 0; i--)
            expect(i, i + n, i == 0 ? 0 : TX_PENDING);
        expectNothing();
    }

    @Test
    public void testExamineLegacy() {
        createAgent(0, true);
        // send snapshot (legacy)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // ---
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine all data
        examineAll();
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine by subscription
        examineBySubscription(0);
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine by subscription (time 2)
        examineBySubscription(2);
        expectMore(3, 10, 0);
        expectJust(2, 11, 0);
        // now examine data range LTR (all)
        examineRange(0, Long.MAX_VALUE);
        expectMore(1, 12, 0);
        expectMore(2, 11, 0);
        expectJust(3, 10, 0);
        // now examine data range 0->0
        examineRange(0, 0);
        expectNothing();
        // now examine data range LTR 1->2
        examineRange(1, 2);
        expectMore(1, 12, 0);
        expectJust(2, 11, 0);
        // now examine data range LTR 2->3
        examineRange(2, 3);
        expectMore(2, 11, 0);
        expectJust(3, 10, 0);
        // now examine data range RTL (all)
        examineRange(Long.MAX_VALUE, 0);
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine data range RTL 2->1
        examineRange(2, 1);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine data range RTL 2->0
        examineRange(2, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine data range RTL 3->2
        examineRange(3, 2);
        expectMore(3, 10, 0);
        expectJust(2, 11, 0);
        // close agent and examine what was there
        closeAgentAndExamine();
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
    }

    @Test
    public void testExamineSnapshot() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // now examine all data
        examineAll();
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // now examine by subscription
        examineBySubscription(0);
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // now examine by subscription (time 2)
        examineBySubscription(2);
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectJust(2, 11, SNAPSHOT_END);
        // now examine data range 0->0
        examineRange(0, 0);
        expectJust(0, 0, REMOVE_EVENT);
        // now examine data range LTR (all)
        examineRange(0, Long.MAX_VALUE);
        expectMore(0, 0, REMOVE_EVENT);
        expectMore(1, 12, 0);
        expectMore(2, 11, 0);
        expectJust(3, 10, 0);
        // now examine data range LTR 1->2
        examineRange(1, 2);
        expectMore(1, 12, 0);
        expectJust(2, 11, 0);
        // now examine data range LTR 2->3
        examineRange(2, 3);
        expectMore(2, 11, 0);
        expectJust(3, 10, 0);
        // now examine data range RTL (all)
        examineRange(Long.MAX_VALUE, 0);
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT);
        // now examine data range RTL 2->1
        examineRange(2, 1);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // now examine data range RTL 2->0
        examineRange(2, 0);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT);
        // now examine data range RTL 3->2
        examineRange(3, 2);
        expectMore(3, 10, 0);
        expectJust(2, 11, 0);
        // close agent and examine what was there
        closeAgentAndExamine();
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
    }

    @Test
    public void testExamineTxPending() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // start transaction at time==1
        distribute(1, 13, TX_PENDING);
        expectJust(1, 13, TX_PENDING);
        // now examine all data
        examineAll();
        expectMore(3, 10, TX_PENDING| SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END | TX_PENDING);
        // now examine by subscription
        examineBySubscription(0);
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END | TX_PENDING);
        // now examine by subscription (time 2)
        examineBySubscription(2);
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectJust(2, 11, TX_PENDING | SNAPSHOT_END);
        // now examine data range 0->0
        examineRange(0, 0);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);
        // now examine data range LTR (all)
        examineRange(0, Long.MAX_VALUE);
        expectMore(0, 0, REMOVE_EVENT | TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectMore(2, 11, TX_PENDING);
        expectJust(3, 10, TX_PENDING);
        // now examine data range LTR 1->2
        examineRange(1, 2);
        expectMore(1, 13, TX_PENDING);
        expectJust(2, 11, TX_PENDING);
        // now examine data range LTR 2->3
        examineRange(2, 3);
        expectMore(2, 11, TX_PENDING);
        expectJust(3, 10, TX_PENDING);
        // now examine data range RTL (all)
        examineRange(Long.MAX_VALUE, 0);
        expectMore(3, 10, TX_PENDING);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);
        // now examine data range RTL 2->1
        examineRange(2, 1);
        expectMore(2, 11, TX_PENDING);
        expectJust(1, 13, TX_PENDING);
        // now examine data range RTL 2->0
        examineRange(2, 0);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);
        // now examine data range RTL 3->2
        examineRange(3, 2);
        expectMore(3, 10, TX_PENDING);
        expectJust(2, 11, TX_PENDING);
        // close agent and examine what was there
        closeAgentAndExamine();
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END | TX_PENDING);
    }

    @Test
    public void testExamineDuringSnapshotUpdate() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // start snapshot update with change at time==1 only
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 13, 0);
        // ---
        if (unconflated) {
            // Snapshot is not yet completed - TX_PENDING flag is set (similar to examineAll() below)
            expectMore(3, 10, (blocking ? 0 : TX_PENDING) | SNAPSHOT_BEGIN);
            expectMore(2, 11, (blocking ? 0 : TX_PENDING));
            expectJust(1, 13, TX_PENDING);
        } else {
            expectJust(1, 13, TX_PENDING);
        }

        // now examine all data
        examineAll();
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, TX_PENDING | REMOVE_EVENT | SNAPSHOT_END);

        // now examine by subscription
        examineBySubscription(0);
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, TX_PENDING | REMOVE_EVENT | SNAPSHOT_END);

        // now examine by subscription (time 2)
        examineBySubscription(2);
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectJust(2, 11, TX_PENDING | SNAPSHOT_END);

        // now examine data range 0->0
        examineRange(0, 0);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);

        // now examine data range LTR (all)
        examineRange(0, Long.MAX_VALUE);
        expectMore(0, 0, REMOVE_EVENT | TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectMore(2, 11, TX_PENDING);
        expectJust(3, 10, TX_PENDING);

        // now examine data range LTR 1->2
        examineRange(1, 2);
        expectMore(1, 13, TX_PENDING);
        expectJust(2, 11, TX_PENDING);

        // now examine data range LTR 2->3
        examineRange(2, 3);
        expectMore(2, 11, TX_PENDING);
        expectJust(3, 10, TX_PENDING);

        // now examine data range RTL (all)
        examineRange(Long.MAX_VALUE, 0);
        expectMore(3, 10, TX_PENDING);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);

        // now examine data range RTL 2->1
        examineRange(2, 1);
        expectMore(2, 11, TX_PENDING);
        expectJust(1, 13, TX_PENDING);

        // now examine data range RTL 2->0
        examineRange(2, 0);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, REMOVE_EVENT | TX_PENDING);

        // now examine data range RTL 3->2
        examineRange(3, 2);
        expectMore(3, 10, TX_PENDING);
        expectJust(2, 11, TX_PENDING);

        // close agent and examine what was there
        closeAgentAndExamine();
        expectMore(3, 10, TX_PENDING | SNAPSHOT_BEGIN);
        expectMore(2, 11, TX_PENDING);
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 0, TX_PENDING | REMOVE_EVENT | SNAPSHOT_END);
    }

    @Test
    public void testSnapshotThenSnip() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);

        // resend previous snapshot, but snip
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP);
        // ---
        if (unconflated) {
            expectMore(3, 10, SNAPSHOT_BEGIN);
            expectMore(2, 11, 0);
            expectJust(1, 12, SNAPSHOT_SNIP);
        } else {
            expectJust(1, 12, SNAPSHOT_SNIP);
        }

        // snip even more
        distribute(3, 10, SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        // ---
        if (unconflated) {
            expectJust(3, 10, SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        } else {
            expectJust(3, 10, SNAPSHOT_SNIP);
        }
        // then increase snapshot again (send more data after snip)
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP); // should be treated as snapshot_end (terminate snapshot update tx)
        // ---
        if (unconflated) {
            expectMore(3, 10, (blocking ? SNAPSHOT_SNIP : 0) | SNAPSHOT_BEGIN);
            expectMore(2, 11, 0);
            expectJust(1, 12, SNAPSHOT_SNIP);
        } else {
            expectMore(2, 11, 0); // extending previous snapshot (no tx needed)
            expectJust(1, 12, SNAPSHOT_SNIP); // snapshot snip makes it consistent
        }
        // increase snapshot to sub time (no longer snip)
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectJust(0, 13, 0); // does not delivery snapshot_end, because there was no snapshot begin
    }

    // test that events generated by testSnapshotThenSnip are correctly interpreted and forwarded further
    @Test
    public void testSnapshotThenSnip2ndOrder() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // just snip it
        distribute(1, 12, SNAPSHOT_SNIP);
        // ---
        expectJust(1, 12, SNAPSHOT_SNIP);
        // snip even more
        distribute(3, 10, SNAPSHOT_SNIP);
        // ---
        expectJust(3, 10, SNAPSHOT_SNIP);
        // now extend this snapshot (no tx!)
        distribute(2, 11, 0); // extending previous snapshot (no tx needed)
        distribute(1, 12, SNAPSHOT_SNIP); // snapshot snip makes it consistent
        // ---
        expectMore(2, 11, 0); // extending previous snapshot (no tx needed)
        expectJust(1, 12, SNAPSHOT_SNIP); // snapshot snip makes it consistent
        // add one more event (non consistent!)
        distribute(0, 13, 0);
        // ---
        expectJust(0, 13, 0); // does not delivery snapshot_end, because there was no snapshot begin
    }

    @Test
    public void testSnapshotThenSnipLegacyAgent() {
        createAgent(0, false);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // note, that legacy agent does not receive virtual snapshotEnd
        // resend previous snapshot, but snip
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP);
        // ---
        expectNothing(); // there are no changes from the point of view of legacy agent (data is the same)
        // snip even more
        distribute(3, 10, SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        // ---
        expectMore(2, 0, 0); // snip event at time=2
        expectJust(1, 0, 0); // snip event at time=1
        // then increase snapshot again (send more data after snip)
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP); // should be treated as snapshot_end (terminate snapshot update tx)
        // ---
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // increase snapshot to sub time (no longer snip)
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectJust(0, 13, 0);
    }

    @Test
    public void testCleanupWithSnip() {
        createAgent(0, true);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // snip the whole snapshot (remove all events)
        distribute(4, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        // ---
        expectJust(4, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
    }

    @Test
    public void testCleanupWithSnipLegacyAgent() {
        createAgent(0, false);
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // snip the whole snapshot (remove all events)
        distribute(4, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        // ---
        expectMore(3, 0, 0);
        expectMore(2, 0, 0);
        expectJust(1, 0, 0);
    }

    @Test
    public void testSnipAndUpdate() {
        createAgent(0, true);
        // send snipped snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 12, SNAPSHOT_SNIP);
        // update all events (no more flags!)
        distribute(3, 15, 0);
        distribute(2, 14, 0);
        distribute(1, 13, 0);
        // ---
        expectMore(3, 15, 0);
        expectMore(2, 14, 0);
        expectJust(1, 13, 0);
        // then send new event above and snip one below (don't update)
        distribute(4, 16, 0);
        distribute(2, 14, SNAPSHOT_SNIP);
        // ---
        expectMore(4, 16, 0);
        expectJust(2, 14, SNAPSHOT_SNIP); // must be delivered, even when data is not updated
        // Examine all and check that data was really snipped
        examineAll();
        expectMore(4, 16, SNAPSHOT_BEGIN);
        expectMore(3, 15, 0);
        expectJust(2, 14, SNAPSHOT_SNIP); // must be marked with snapshot snip
    }

    @Test
    public void testSnipAndUpdateLegacyAgent() {
        createAgent(0, false);
        // send snipped snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP);
        // ---
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // update all events (no more flags!)
        distribute(3, 15, 0);
        distribute(2, 14, 0);
        distribute(1, 13, 0);
        // ---
        expectMore(3, 15, 0);
        expectMore(2, 14, 0);
        expectJust(1, 13, 0);
        // then send new event above and snip one below (don't update)
        distribute(4, 16, 0);
        distribute(2, 14, SNAPSHOT_SNIP);
        // ---
        expectMore(4, 16, 0);
        expectJust(1, 0, 0); // snip event at time=1
        // Examine all and check that data was really snipped
        examineAll();
        expectMore(4, 16, SNAPSHOT_BEGIN);
        expectMore(3, 15, 0);
        expectJust(2, 14, SNAPSHOT_SNIP); // must be marked with snapshot snip
    }

    @Test
    public void testSnipTimeKnown() {
        createAgent(0, true);
        // send snipped snapshot for times 6-4
        distribute(6, 10, SNAPSHOT_BEGIN);
        distribute(5, 11, 0);
        distribute(4, 12, SNAPSHOT_SNIP); // must be treated as snapshotEnd, setting timeKnown == timeSub
        // ---
        expectMore(6, 10, SNAPSHOT_BEGIN);
        expectMore(5, 11, 0);
        expectJust(4, 12, SNAPSHOT_SNIP);
        // send completely different 3-0 snapshot (up to 0 with a regular snapshot end)
        distribute(3, 13, SNAPSHOT_BEGIN);
        distribute(2, 14, 0);
        distribute(1, 15, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        if (unconflated) {
            expectMore(3, 13, (blocking ? TX_PENDING : 0) | SNAPSHOT_BEGIN);
            expectMore(2, 14, (blocking ? TX_PENDING : 0));
            expectMore(1, 15, (blocking ? TX_PENDING : 0));
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        } else {
            // it must be received from local buffer as transactional update to the current snapshot
            expectMore(6, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(5, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 13, TX_PENDING);
            expectMore(2, 14, TX_PENDING);
            if (blocking) {
                expectMore(1, 15, TX_PENDING);
                expectJust(0, 0, REMOVE_EVENT);
            } else {
                expectJust(1, 15, 0); // last event ends transaction when non-blocking retrieve (from HB)
            }
        }
    }

    @Test
    public void testSnipWithPartialRetrieve() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // send snipped snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, SNAPSHOT_SNIP);
        // retrieve up to timeKnown=2
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        // send different snapshot, up to time=4
        distribute(6, 13, SNAPSHOT_BEGIN);
        distribute(5, 14, 0);
        distribute(4, 15, SNAPSHOT_SNIP); // snip to time > timeKnown must advance timeKnown to timeSub
        // ---
        if (unconflated) {
            expectMore(6, 13, SNAPSHOT_BEGIN);
            expectMore(5, 14, 0);
            expectJust(4, 15, SNAPSHOT_SNIP); // must end transaction
        } else {
            expectMore(6, 13, TX_PENDING);
            expectMore(5, 14, TX_PENDING);
            expectJust(4, 15, SNAPSHOT_SNIP); // must end transaction
        }
    }

    @Test
    public void testSnipSubByHistorySubscriptionFilter() {
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot to zero
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP); // SNAPSHOT_SNIP instead of SNAPSHOT_END
    }

    @Test
    public void testSnipSubSnapshotSweepRemove() {
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_SNIP); // SNAPSHOT_SNIP instead of SNAPSHOT_END
        // send snapshot with only 1 items left (confirm it)
        distribute(1, 13, SNAPSHOT_BEGIN);
        // ---
        if (unconflated) {
            expectJust(1, 13, TX_PENDING | SNAPSHOT_BEGIN);
        } else {
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(2, 0, REMOVE_EVENT | TX_PENDING);
        }
        // ---
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END);
        if (unconflated) {
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        } else {
            expectJust(0, 0, REMOVE_EVENT);
        }
    }

    @Test
    public void testSnipSubTxDirty1a() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN); // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING); // here and lower -- all pending
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_SNIP); // SNAPSHOT_SNIP instead of SNAPSHOT_END
        // continue transaction at time=1 and finish at time=3
        distribute(1, 16, TX_PENDING);
        distribute(3, 17, 0);
        // check both
        expectMore(1, 16, TX_PENDING);
        expectJust(3, 17, 0);
    }

    @Test
    public void testSnipSubTxDirty1b() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // start transaction at time=2 (not yet started to retrieve!)
        distribute(2, 15, TX_PENDING);
        // ---
        expectMore(4, 10, TX_PENDING | SNAPSHOT_BEGIN);  // all pending, because dirty
        expectMore(3, 11, TX_PENDING);
        expectMore(2, 15, TX_PENDING); // here and lower -- all pending
        expectMore(1, 13, TX_PENDING);
        expectJust(0, 14, TX_PENDING | SNAPSHOT_SNIP); // SNAPSHOT_SNIP instead of SNAPSHOT_END
        // continue transaction at time=1 and finish at time=3 (but here are no actual updates!)
        distribute(1, 13, TX_PENDING); // no changes
        distribute(3, 11, 0);          // no changes
        // check
        expectJust(3, 11, 0);    // <- only last TX_END comes in
    }

    @Test
    public void testSnipAndSweep() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
        // now sweep-remove and snip in the middle
        distribute(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_SNIP);

        if (unconflated) {
            expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        } else {
            // remove old events in transaction and snip
            // todo: can be further optimized
            expectMore(4, 0, REMOVE_EVENT | TX_PENDING);
            expectMore(3, 0, REMOVE_EVENT | TX_PENDING);
            expectJust(2, 0, REMOVE_EVENT | SNAPSHOT_SNIP);
        }
    }

    @Test
    public void testSnipAndSweepLegacy() {
        createAgent(0, false); // legacy agent
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(4, 10, 0);
        expectMore(3, 11, 0);
        expectMore(2, 12, 0);
        expectMore(1, 13, 0);
        expectJust(0, 14, 0);
        // now sweep-remove and snip in the middle
        distribute(2, 0, REMOVE_EVENT | SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        // remove all old events for legacy agent (should go in reserve time order)
        expectMore(4, 0, 0);
        expectMore(3, 0, 0);
        expectMore(2, 0, 0);
        expectMore(1, 0, 0);
        expectJust(0, 0, 0);
    }

    @Test
    public void testReceivePart() {
        createAgent(0, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        // start receiving before snapshot is over
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, 0);
        // finish snapshot
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(1, 13, 0);
        expectJust(0, 14, SNAPSHOT_END);
    }

    @Test
    public void testLegacyReceivePart() {
        createAgent(0, false); // legacy agent
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, 0);
        // start receiving before snapshot is over
        expectMore(4, 10, 0);
        expectMore(3, 11, 0);
        expectJust(2, 12, 0);
        // finish snapshot
        distribute(1, 13, 0);
        distribute(0, 14, SNAPSHOT_END);
        // ---
        expectMore(1, 13, 0);
        expectJust(0, 14, 0);
    }

    @Test
    public void testPartialRetrieveUpdateInconsistency() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // distribute snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // retrieve partially (time >= 2)
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        // now make two non-tx updates
        distribute(2, 14, 0); // first in the already retrieved time
        distribute(1, 15, 0); // then in the not-yet-retrieved time
        // retrieve the rest of snapshot from HB
        expectMore(1, 15, TX_PENDING); // the second update will be receive first
        expectMore(0, 13, TX_PENDING | SNAPSHOT_END); // this is non-consistent snapshot
        expectJust(2, 14, 0); // then the first update
    }

    // same as above, but legacy source -- no transactions even for tx-supporting agents
    @Test
    public void testPartialRetrieveUpdateLegacySource() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // distribute snapshot (legacy)
        distribute(3, 10, 0);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, 0);
        // retrieve partially (time >= 2)
        expectMore(3, 10, 0);
        expectMore(2, 11, 0);
        // now make two non-tx updates
        distribute(2, 14, 0); // first in the already retrieved time
        distribute(1, 15, 0); // then in the not-yet-retrieved time
        // retrieve the rest of snapshot from HB
        expectMore(1, 15, 0); // the second update will be receive first
        expectMore(0, 13, 0); // this is non-consistent snapshot
        expectJust(2, 14, 0); // then the first update
    }

    // This is a simplified example of the problem in testLostTxPending4
    @Test
    public void testInconsistentSnapshotRetrieveMT1() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // start distributing snapshot for that it is available
        distribute(3, 9, SNAPSHOT_BEGIN);
        // distribute the rest of snapshot and then a pair of non-transactional updates in a batch
        startBatch();
        distribute(2, 10, 0);
        distribute(1, 11, 0);
        distribute(0, 12, SNAPSHOT_END);
        distribute(2, 13, 0);
        distribute(1, 14, 0);
        // the retrieve in between the phases  -- it will retrieve updated snapshot from HB
        betweenProcessPhases = () -> {
            // this kind of snapshot is always marked as txPending as a safety masure
            expectMore(3, 9, TX_PENDING | SNAPSHOT_BEGIN);
            expectMore(2, 13, TX_PENDING);
            expectMore(1, 14, TX_PENDING);
            expectJust(0, 12, TX_PENDING | SNAPSHOT_END);
        };
        processBatch();
        // followed by updates from the agent buffer
        expectMore(2, 10, TX_PENDING); // this update actually makes the snapshot appear inconsistent
        expectMore(1, 11, TX_PENDING);
        expectMore(0, 12, TX_PENDING);
        expectMore(2, 13, TX_PENDING);
        expectJust(1, 14, 0); // it is consistent after all updates were processed
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending1() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot (start)
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        // receive events from HB
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 12, 0);
        // send snapshot end & start tx update (there will be two transactions)
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END); // data provider sees sub at 0 and send snapshot end (snip for agent)
        distribute(3, 13, TX_PENDING);
        betweenProcessPhases = () -> {
            // receive updated top item on next retrieve (from agent buffer)
            history.forceRetrieveUpdate = true;
            expectMore(3, 13, TX_PENDING);
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP | TX_PENDING);
        };
        distribute(2, 14, 0);
        // will receive this event and end transaction
        expectJust(2, 14, 0);
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending2() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send snapshot start
        distribute(2, 10, SNAPSHOT_BEGIN);
        distribute(1, 11, 0);
        // and receive it
        expectMore(2, 10, SNAPSHOT_BEGIN);
        expectJust(1, 11, 0);
        // send snapshot and start updating it
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END); // data provider sees sub at 0 and send snapshot end (snip for agent)
        distribute(1, 12, TX_PENDING); // update item at time=1 -> goes to agent buffer (#1)
        startBatch();
        distribute(-1, 13, REMOVE_EVENT); // and terminate transaction with virtual event outside of time sub (goes to HB, end transaction in HB)
        distribute(2, 14, TX_PENDING); // and start new transaction right here -> goes to agent buffer (#2)
        processBatch();
        // receive updates from agent buffer (first)
        history.forceRetrieveUpdate = true; // override retrieve balancing logic
        expectMore(1, 12, TX_PENDING); // #1
        expectMore(2, 14, TX_PENDING); // #2
        // receive the end from history (there's still a transaction in progress)
        expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP | TX_PENDING);
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending3() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // send some "old data" (as if the data was coming from the wire for previous subscription)
        distribute(3, 7, TX_PENDING);
        // receive the beginning of that transaction
        expectJust(3, 7, TX_PENDING);
        // and the send the rest of that "old" transaction
        distribute(2, 8, TX_PENDING);
        distribute(1, 9, 0);
        // stand sending new snapshot (will overwrite the "old data" above)
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        // and receive these two events from snapshot (will marked with transaction)
        expectMore(3, 10, SNAPSHOT_BEGIN | TX_PENDING);
        expectJust(2, 11, TX_PENDING);
        // now send one more item from the new snapshot
        distribute(1, 12, 0);
        // now finish the snapshot and immediately start two small transaction in a batch
        startBatch();
        distribute(0, 0, REMOVE_EVENT | SNAPSHOT_END); // data provider sees sub at 0 and send snapshot end (snip for agent)
        distribute(1, 13, TX_PENDING);
        distribute(2, 14, 0);
        distribute(1, 15, TX_PENDING);
        distribute(2, 16, 0);
        // between the phases of this batch receive the the above item at time=1 and snapshot snip at time=0
        betweenProcessPhases = () -> {
            expectMore(1, 15, TX_PENDING); // the value will be already updated in the first phase
            expectJust(0, 0, REMOVE_EVENT | SNAPSHOT_SNIP | TX_PENDING);
        };
        processBatch(); // process all the above in a single batch here
        // Note, that the 2nd phase will put all 5 update into the agent buffer, and now they must
        // be all retrieved, ending transaction only with the last one.
        expectMore(1, 13, TX_PENDING);
        expectMore(2, 14, TX_PENDING);
        expectMore(1, 15, TX_PENDING); // dupes this event -- receive from both HB agent and from agent buf here
        expectJust(2, 16, 0);
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending4() {
        if (blocking || unconflated)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(-2, true); // agent subscribes from -2, but subscription gets snipped to zero
        // snapshot begin
        distribute(3, 10, SNAPSHOT_BEGIN);
        expectJust(3, 10, SNAPSHOT_BEGIN);
        // finish snapshot and start transaction
        startBatch();
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(-1, 0, REMOVE_EVENT | SNAPSHOT_END);
        distribute(1, 13, TX_PENDING);
        processBatch();
        // finish prev transaction and start new one in batch
        startBatch();
        distribute(2, 14, 0);
        distribute(1, 15, TX_PENDING);
        distribute(2, 16, 0);
        // but do retrieve in between
        // retrieval happens in between from HB, but updates in 2nd phase will actually go to agent buffer
        betweenProcessPhases = () -> {
            // finish retrieval of snapshot (but HB was already updated)
            expectMore(2, 16, TX_PENDING);
            expectMore(1, 15, TX_PENDING);
            expectJust(0, 0, TX_PENDING | REMOVE_EVENT | SNAPSHOT_SNIP);
        };
        processBatch();
        // now do one more tx in batch (goes to agent buffer)
        startBatch();
        distribute(2, 17, TX_PENDING);
        distribute(1, 18, 0);
        processBatch();
        // and then one more tx in batch
        startBatch();
        distribute(1, 19, TX_PENDING);
        distribute(2, 20, 0);
        // with process in between its phases
        betweenProcessPhases = () -> expectMore(2, 14, TX_PENDING);
        processBatch();
        // retrieve the rest
        expectMore(1, 15, TX_PENDING);
        expectMore(2, 17, TX_PENDING);
        expectMore(1, 19, TX_PENDING);
        expectJust(2, 20, 0);
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending5() {
        createAgent2(0, true);
        createAgent(2, true);
        // send and receive snapshot (below our agent's sub time)
        distribute(1, 10, SNAPSHOT_BEGIN);
        distribute(0, 11, SNAPSHOT_END);
        // --
        expectJust(2, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // send two back-to-back transaction in a batch 1-0, followed by 0-1
        startBatch();
        distribute(1, 12, TX_PENDING);
        distribute(0, 13, 0);
        distribute(0, 14, TX_PENDING);
        distribute(1, 15, 0);
        // in between batch phases unsubscribe agent and resubscribe from 0 and retrieve tx (dirty) snapshot
        betweenProcessPhases = () -> {
            unsubscribeAgent();
            setSubTime(0);
            expectMore(1, 15, SNAPSHOT_BEGIN);
            expectJust(0, 14, SNAPSHOT_END);
        };
        processBatch();
        // expect that all in process record will be dropped because of the subscription change
        expectNothing();
    }

    // this case was found by MTStressTest
    @Test
    public void testLostTxPending6() {
        createAgent2(0, true); // 2nd agent subscribes for time>=0
        // send snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // create sub from time>=1 and receive snapshot
        createAgent(1, true);
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectJust(1, 12, SNAPSHOT_END);

        // start sending the other snapshot (without update on the first items)
        distribute(3, 10, SNAPSHOT_BEGIN); // no update at time=3
        // unsubscribe the other agent
        closeAgent2(); // will wait for begin snapshot
        // finish snapshot
        distribute(2, 13, 0);
        distribute(1, 14, 0); // snapshot end for subscription time=1
        distribute(0, 15, SNAPSHOT_END); // no sub anymore

        // do update transaction
        distribute(2, 16, TX_PENDING);
        distribute(3, 17, TX_PENDING); // now update at time=3 (the only non-ignored event)
        distribute(1, 18, 0); // txEnd

        // and finally distribute a snapshot
        distribute(3, 17, SNAPSHOT_BEGIN); // same as in tx above
        distribute(2, 19, 0);
        distribute(1, 20, SNAPSHOT_END);
        // --
        if (unconflated && blocking) {
            expectMore(3, 10, SNAPSHOT_BEGIN);
            expectMore(2, 13, TX_PENDING);
            expectMore(1, 14, SNAPSHOT_END);
            expectMore(2, 16, TX_PENDING);
            expectMore(3, 17, TX_PENDING);
            expectMore(1, 18, 0); // txEnd
            expectMore(3, 17, SNAPSHOT_BEGIN);
            expectMore(2, 19, TX_PENDING);
            expectJust(1, 20, SNAPSHOT_END);
        } else if (unconflated) {
            expectMore(3, 17, SNAPSHOT_BEGIN);
            expectMore(2, 19, 0);
            expectJust(1, 20, SNAPSHOT_END);
        } else {
            expectMore(2, 13, TX_PENDING);
            expectMore(1, 14, 0); // snapshot end
            expectMore(2, 16, TX_PENDING);
            expectMore(3, 17, TX_PENDING);
            expectMore(1, 18, 0); // txEnd
            expectMore(2, 19, TX_PENDING);
            expectJust(1, 20, 0);
        }
    }

    // this case was found by MTStressTest
    @Test
    public void testBeginSnapshotBufferClear() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(1, true);
        // process a part of an old update
        distribute(2, 10, 0);
        // receive that update
        expectJust(2, 10, 0);
        // update more stuff (above known time)
        distribute(1, 11, 0);
        distribute(2, 12, 0);
        // then snapshot
        distribute(2, 13, SNAPSHOT_BEGIN);
        distribute(1, 14, SNAPSHOT_END);
        // expect snapshot only (agent buffer must be dropped by unlinking buffered events)
        expectMore(2, 13, SNAPSHOT_BEGIN);
        expectMore(1, 14, SNAPSHOT_END);
        expectNothingRetrieves(); // tricky case, where "available" is true, but nothing retrieves, because events were unlinked
    }

    @Test
    public void testAgent2BreaksSnapshot() {
        // create 2nd agent to maintain subscription only at records with time>=10
        createAgent2(10, true);
        // create our main test agent
        createAgent(0, true);
        // distribute snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 13, SNAPSHOT_END);
        // close main agent and reopen it with the same time sub
        // all data will be lost, be HB is retained because of agent2 sub
        // but snapshot flag must be reset in HB (!)
        closeAgent();
        createAgent(0, true);
        // data provider does not know it and updates snapshot
        distribute(1, 14, 0);
        distribute(0, 15, 0);
        // both of them are ignored
        expectNothing();
        // now data provider sends the whole snapshot again
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 14, 0);
        distribute(0, 15, SNAPSHOT_END);
        // now this snapshot is delivered
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 14, 0);
        expectJust(0, 15, SNAPSHOT_END);
    }

    @Test
    public void testAgent2WithLargerSub() {
        // create 2nd agent with larger sub from time = 0
        createAgent2(0, true);
        // create our agent only for time >= 2
        createAgent(2, true);
        // distribute snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectJust(2, 11, SNAPSHOT_END);
        // distribute snapshot again
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        if (unconflated) {
            expectMore(3, 10, SNAPSHOT_BEGIN);
            expectJust(2, 11, SNAPSHOT_END);
        } else {
            expectNothing();
        }
    }

    @Test
    public void testAgent2StillConsistentOnTotalSubReduce() {
        // create agent for time >= 2
        createAgent(2, true);
        // send snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, SNAPSHOT_END);
        // --
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END);

        // now agent2 comes and subscribes for larger time interval
        createAgent2(0, true);
        // data source still sends updates on smaller subscription and they must be received by original agent
        distribute(4, 13, TX_PENDING);
        distribute(3, 14, TX_PENDING);
        distribute(2, 15, 0);
        //---
        expectMore(4, 13, TX_PENDING);
        expectMore(3, 14, TX_PENDING);
        expectJust(2, 15, 0);

        // now snapshot for agent2 is sent
        distribute(4, 13, SNAPSHOT_BEGIN);
        distribute(3, 14, 0);
        distribute(2, 15, 0);
        distribute(1, 16, 0);
        distribute(0, 17, SNAPSHOT_END);
        if (unconflated) {
            expectMore(4, 13, SNAPSHOT_BEGIN);
            expectMore(3, 14, 0);
            expectJust(2, 15, SNAPSHOT_END);
        } else {
            // nothing new for original agent, though
            expectNothing();
        }

        // now agent2 resubscribes for smaller time interval
        setSubTime2(10);
        // data source still sends updates and they must be received by original agent
        distribute(4, 18, TX_PENDING);
        distribute(3, 19, TX_PENDING);
        distribute(2, 20, 0);
        //---
        expectMore(4, 18, TX_PENDING);
        expectMore(3, 19, TX_PENDING);
        expectJust(2, 20, 0);

        // now snapshot is resent because of subscription change
        distribute(4, 18, SNAPSHOT_BEGIN);
        distribute(3, 19, 0);
        distribute(2, 20, SNAPSHOT_END);
        if (unconflated) {
            expectMore(4, 18, SNAPSHOT_BEGIN);
            expectMore(3, 19, 0);
            expectJust(2, 20, SNAPSHOT_END);
        } else {
            // nothing new for original agent, though
            expectNothing();
        }
    }

    @Test
    public void testNonSubscribedUpdates() {
        createAgent2(10, true); // use agent2 to keep HB state while we manipulate our sub
        // create our main test agent
        createAgent(0, true);
        // distribute snapshot
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, 0);
        distribute(1, 12, 0);
        distribute(0, 13, SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectMore(2, 11, 0);
        expectMore(1, 12, 0);
        expectJust(0, 13, SNAPSHOT_END);
        // change sub to 10
        setSubTime(10);
        // send snapshot again (with different data!), but we're not subscribed any more
        distribute(3, 14, SNAPSHOT_BEGIN);
        distribute(2, 15, 0);
        distribute(1, 16, 0);
        distribute(0, 17, SNAPSHOT_END);
        // ---
        if (unconflated && blocking) {
            expectMore(10, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        }
        expectJust(10, 0, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        // now close agent and reopen at time 0. Make sure valid snapshot is only up to time 10
        closeAgent();
        createAgent(0, true);
        expectNothing();
    }

    @Test
    public void testTwoSweep() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // Expose broken history buffer maintenance code
        distribute(0, 10, 0);
        distribute(2, 11, 0);
        distribute(3, 12, 0);
        distribute(4, 13, 0);
        // ---
        distribute(4, 14, SNAPSHOT_BEGIN);
        distribute(0, 15, 0);
        distribute(-1, 0, REMOVE_EVENT | SNAPSHOT_END);
        // ---
        distribute(4, 15, TX_PENDING);
        distribute(0, 16, 0);
        // retrieve snapshot
        expectMore(4, 15, SNAPSHOT_BEGIN);
        expectJust(0, 16, SNAPSHOT_END);
    }

    @Test
    public void testLowerSnapshot() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // distribute snapshot at higher values that we're subscribed (leftover for old subscription)
        distribute(3, 10, SNAPSHOT_BEGIN);
        distribute(2, 11, SNAPSHOT_END);
        // ---
        expectMore(3, 10, SNAPSHOT_BEGIN);
        expectJust(2, 11, 0);
        // distribute snapshot at lower values
        distribute(1, 12, SNAPSHOT_BEGIN);
        distribute(0, 13, SNAPSHOT_END);
        if (unconflated) {
            expectMore(1, 12, SNAPSHOT_BEGIN);
            expectJust(0, 13, SNAPSHOT_END);
        } else {
            // -- expect snapshot first, then updates (removes)
            expectMore(1, 12, TX_PENDING);
            expectMore(0, 13, TX_PENDING | SNAPSHOT_END);
            expectMore(3, 0, TX_PENDING | REMOVE_EVENT);
            expectJust(2, 0, REMOVE_EVENT);
        }
    }

    /*
     * Transaction can appear "out of thin air" due to implicit transaction that happens
     * is tracked during snapshot retrieve.
     */
    @Test
    public void testImplicitTxPending() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        createAgent(0, true);
        // distribute a snapshot
        distribute(2, 10, SNAPSHOT_BEGIN);
        distribute(1, 11, 0);
        distribute(0, 0, SNAPSHOT_END | REMOVE_EVENT);
        // retrieve one record
        expectMore(2, 10, SNAPSHOT_BEGIN);
        // distribute update for just retrieved record
        distribute(2, 12, 0);
        // retrieve the rest
        expectMore(1, 11, TX_PENDING);
        expectMore(0, 0, TX_PENDING | SNAPSHOT_END | REMOVE_EVENT);
        expectJust(2, 12, 0);
    }

    // See QD-985
    @Test
    public void testQD985TxPendingHang() {
        if (blocking)
            return; // cannot do this test in blocking mode -- it explicitly tests proper buffering
        // pre-fill HB with other subscription
        createAgent2(1, true);
        distribute(5, 10, TX_PENDING);
        distribute(4, 11, 0);
        // sub
        createAgent(5, true);
        // snapshot
        startBatch();
        distribute(5, 12, SNAPSHOT_BEGIN);
        distribute(3, 13, 0);
        distribute(2, 14, 0);
        distribute(1, 15, SNAPSHOT_END);
        // update
        distribute(1, 16, TX_PENDING);
        distribute(2, 17, 0);
        processBatch();
        // ---
        expectJust(5, 12, SNAPSHOT_BEGIN | SNAPSHOT_END);
    }

    // See QD-1098
    @Test
    public void testRemoveEventWithVirtualTimeOnSnipForSecondAgentTxDirty() {
        // create our main test agent
        createAgent(2, true);
        // distribute snapshot
        distribute(4, 10, SNAPSHOT_BEGIN);
        distribute(3, 11, 0);
        distribute(2, 12, SNAPSHOT_END);
        // check snapshot
        expectMore(4, 10, SNAPSHOT_BEGIN);
        expectMore(3, 11, 0);
        expectJust(2, 12, SNAPSHOT_END);

        // second agent for SNAPSHOT_SNIP in last event
        createAgent2(-2, true);
        // continue snapshot for the second agent, tx dirty. Event shall not be received by the first agent
        distribute(1, 13, TX_PENDING);
        // send "snapshot snip" to signal snapshot complete for agent2
        distribute(0, 14, SNAPSHOT_SNIP);

        // empty event with virtual time and REMOVE_EVENT flag
        expectJust(History.VIRTUAL_TIME, 0, REMOVE_EVENT);
        //close all agents
        closeAgent2();
        closeAgent();
    }

    // =================== utility methods ===================

    private void createAgent(long timeSub, boolean useHistorySnapshot) {
        assertEquals(null, agent);
        agent = history.agentBuilder().withHistorySnapshot(useHistorySnapshot).build();
        setSubTime(timeSub);
        provider = getProvider(agent);
        provider.setRecordListener(p -> {
            assertTrue("!available", !available);
            assertEquals(provider, p);
            available = true;
        });
    }

    private void setSubTime(long timeSub) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        RecordCursor cursor = sub.add(RECORD, CIPHER, null);
        cursor.setTime(timeSub);
        agent.addSubscription(sub);
        sub.release();
    }

    private void unsubscribeAgent() {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        agent.setSubscription(sub);
        sub.release();
    }

    void closeAgent() {
        agent.close();
        agent = null;
        provider = null;
        blockingProvider = null;
        blockingListener = null;
        assertTrue("!available", !available);
    }

    private void createAgent2(long timeSub, boolean useHistorySnapshot) {
        assertEquals(null, agent2);
        agent2 = history.agentBuilder().withHistorySnapshot(useHistorySnapshot).build();
        setSubTime2(timeSub);
    }

    private void setSubTime2(long timeSub) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        RecordCursor cursor = sub.add(RECORD, CIPHER, null);
        cursor.setTime(timeSub);
        agent2.addSubscription(sub);
        sub.release();
    }

    private void closeAgent2() {
        agent2.close();
        agent2 = null;
    }

    private void examineAll() {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        history.examineData(buf);
        provider = buf;
        available = buf.hasNext();
    }

    private void examineBySubscription(long time) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(RECORD, CIPHER, null).setTime(time);
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        history.examineDataBySubscription(buf, sub);
        sub.release();
        provider = buf;
        available = buf.hasNext();
    }

    private void examineRange(long startTime, long endTime) {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        history.examineData(RECORD, CIPHER, null, startTime, endTime, buf);
        provider = buf;
        available = buf.hasNext();
    }

    private void closeAgentAndExamine() {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        agent.closeAndExamineDataBySubscription(buf);
        agent = null;
        provider = buf;
        available = buf.hasNext();
    }

    // HistoryTxBlockingTest overrides
    RecordProvider getProvider(QDAgent agent) {
        // Non-blocking mode: do nothing
        if (!blocking)
            return agent;

        // Blocking mode:
        // Buffer size is limited to 1 and blocking is configured,
        // while the actual buffer is off-loaded to a separate buffer.
        // This way buffer blocking in History is stress-tested.
        agent.setBufferOverflowStrategy(QDAgent.BufferOverflowStrategy.BLOCK);
        agent.setMaxBufferSize(1);

        // our buffer
        final RecordBuffer buf = RecordBuffer.getInstance(agent.getMode());
        // our provider
        blockingProvider = new AbstractRecordProvider() {
            @Override
            public RecordMode getMode() {
                return agent.getMode();
            }

            @Override
            public boolean retrieve(RecordSink sink) {
                return buf.retrieve(sink);
            }

            @Override
            public void setRecordListener(RecordListener listener) {
                blockingListener = listener;
                if (listener != null && buf.hasNext())
                    listener.recordsAvailable(blockingProvider);
            }
        };
        // will mimic conflation logic
        final RecordSink conflatingSink = new AbstractRecordSink() {
            long lastPosition = -1;
            @Override
            public void append(RecordCursor cursor) {
                if (lastPosition >= buf.getPosition()) {
                    RecordCursor writeCursor = buf.writeCursorAt(lastPosition);
                    if (writeCursor.getTime() == cursor.getTime() && !unconflated) {
                        // Emulate conflation
                        writeCursor.setEventFlags(cursor.getEventFlags());
                        writeCursor.copyDataFrom(cursor);
                        return;
                    }
                }
                lastPosition = buf.getLimit();
                buf.append(cursor);
            }
        };
        // install an agent's listener
        agent.setRecordListener(p -> {
            boolean wasEmpty = !buf.hasNext();
            agent.retrieve(conflatingSink);
            if (wasEmpty && buf.hasNext() && blockingListener != null)
                blockingListener.recordsAvailable(blockingProvider);
        });
        return blockingProvider;
    }

    private void startBatch() {
        distributeBatch = true;
    }

    private void distribute(long time, int value, int flags) {
        RecordCursor cursor = distributeBuf.add(RECORD, CIPHER, null);
        cursor.setTime(time);
        cursor.setInt(VALUE_INDEX, value);
        cursor.setEventFlags(flags);
        if (!distributeBatch)
            processBatch();
    }

    private void processBatch() {
        distributor.process(distributeBuf);
        distributeBuf.clear();
        distributeBatch = false;
    }

    private void removeData() {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        buf.add(RECORD, CIPHER, null);
        history.remove(buf);
        buf.release();
    }

    private void expectMore(long time, int value, int flags) {
        expect(time, value, flags);
        assertTrue("available", available || !retrieveBuf.isEmpty());
    }

    private void expectJust(long time, int value, int flags) {
        expect(time, value, flags);
        assertTrue("!available", !available && retrieveBuf.isEmpty());
    }

    private void expectUnconflated(final long time, final int value, final int flags) {
        if (unconflated) {
            expect(time, value, flags);
        }
    }

    private void expect(final long time, final int value, final int flags) {
        assertTrue("available", available || !retrieveBuf.isEmpty());
        if (retrieveBuf.isEmpty())
            retrieveBatch(1);
        RecordCursor cursor = retrieveBuf.next();
        assertNotNull(cursor);
        assertEquals("record", RECORD, cursor.getRecord());
        assertEquals("cipher", CIPHER, cursor.getCipher());
        assertNull("symbol", cursor.getSymbol());
        assertEquals("time", time, cursor.getTime());
        assertEquals("value", value, cursor.getInt(VALUE_INDEX));
        assertEquals("flags", flags, cursor.getEventFlags());
        //see QD-1098
        if (time == History.VIRTUAL_TIME) {
            int cursorFlags = cursor.getEventFlags();
            assertEquals(cursorFlags, cursorFlags & ~SNAPSHOT_BEGIN & ~SNAPSHOT_END & ~SNAPSHOT_SNIP & ~SNAPSHOT_MODE);
        }
        retrieveBuf.cleanup(cursor);
    }

    private void expectNothing() {
        assertTrue("!available", !available && retrieveBuf.isEmpty());
        expectNothingRetrieves();
    }

    private void expectNothingRetrieves() {
        boolean hasMore = provider.retrieve(new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                fail();
            }
        });
        assertFalse(hasMore);
        available = false;
    }

    private void retrieveBatch(final int size) {
        assertTrue("available", available);
        assertEquals(0, retrieveBuf.size());
        available = false;
        boolean hasMore = provider.retrieve(new AbstractRecordSink() {
            int rem = size;

            @Override
            public boolean hasCapacity() {
                return rem > 0;
            }

            @Override
            public void append(RecordCursor cursor) {
                assertTrue(rem > 0);
                rem--;
                retrieveBuf.append(cursor);
            }
        });
        assertEquals("received", size, retrieveBuf.size());
        assertFalse("!available", available);
        available = hasMore;
    }

    // Limit subscription to zero (snip negative subscription)
    private static class HSF implements HistorySubscriptionFilter {
        @Override
        public long getMinHistoryTime(DataRecord record, int cipher, String symbol) {
            return 0;
        }

        @Override
        public int getMaxRecordCount(DataRecord record, int cipher, String symbol) {
            return Integer.MAX_VALUE;
        }
    }

    private class HistoryImpl extends History {
        boolean forceRetrieveUpdate;

        HistoryImpl(Builder<QDHistory> builder) {
            super(builder, new RecordOnlyFilter(builder.getScheme()) {
                @Override
                public boolean acceptRecord(DataRecord record) {
                    return !unconflated;
                }
            });
        }

        @Override
        protected void onBetweenProcessPhases() {
            if (betweenProcessPhases != null) {
                betweenProcessPhases.run();
                betweenProcessPhases = null;
            }
        }

        @Override
        protected boolean shallForceRetrieveUpdate() {
            return forceRetrieveUpdate;
        }
    }
}
