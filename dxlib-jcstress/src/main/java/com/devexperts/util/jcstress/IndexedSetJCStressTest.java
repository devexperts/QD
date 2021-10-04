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
package com.devexperts.util.jcstress;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Description;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;
import sun.misc.Contended;

/**
 * These tests check IndexedSet implementation for issues related to state that concurrent readers may observe during
 * concurrent set updates. IndexedSet claims to be "thread safe" in single-writer-multi-readers scenarios, but prior to
 * the QD-1320 changes the "thread-safety" of IndexedSet didn't actually guarantee safe publication of mutable objects.
 *
 * <p>The problem was observed on ARM-based systems (while theoretically possible, it was never witnessed on Intel/AMD):
 *
 * <pre>
 *   java -jar dxlib-jcstress.jar -v -hs 1024 -m stress
 *   ...
 *
 *   RUN RESULTS:
 *   Interesting tests: No matches.
 *
 *   Failed tests: 2 matching test results.
 *
 * ...... [FAILED] com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization
 * 
 *   Results across all configurations:
 *
 *   RESULT        SAMPLES     FREQ      EXPECT  DESCRIPTION
 *   -1, -1  4,159,035,956   99.83%  Acceptable  Missed both
 *    -1, 8              1   <0.01%   Forbidden  Non linearizable
 *    0, -1            172   <0.01%   Forbidden
 *    1, -1             53   <0.01%   Forbidden
 *    2, -1              2   <0.01%   Forbidden
 *    3, -1             43   <0.01%   Forbidden
 *    4, -1      5,173,147    0.12%  Acceptable  Missed second
 *     4, 0              6   <0.01%   Forbidden
 *     4, 6              4   <0.01%   Forbidden
 *     4, 8      1,965,336    0.05%  Acceptable  Got both
 *
 * ...... [FAILED] com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication
 *
 *   Results across all configurations:
 *
 *   RESULT        SAMPLES     FREQ      EXPECT  DESCRIPTION
 *     0, 0  6,313,884,262   99.71%  Acceptable  Missed
 *     1, 0            668   <0.01%   Forbidden
 *     1, 2            240   <0.01%   Forbidden
 *     1, 4             13   <0.01%   Forbidden
 *     1, 6            260   <0.01%   Forbidden
 *     1, 8     18,079,997    0.29%  Acceptable  New element
 * ...
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:TieredStopAtLevel=1]: Observed forbidden state: 4, 0 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:TieredStopAtLevel=1]: Observed forbidden state: 0, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:TieredStopAtLevel=1]: Observed forbidden state: 1, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:TieredStopAtLevel=1]: Observed forbidden state: 2, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:TieredStopAtLevel=1]: Observed forbidden state: 3, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: 4, 0 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: 4, 6 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: -1, 8 (Non linearizable)
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: 0, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: 1, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimplePutLinearization [-XX:-TieredCompilation]: Observed forbidden state: 3, -1 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:-TieredCompilation]: Observed forbidden state: 1, 0 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:-TieredCompilation]: Observed forbidden state: 1, 2 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:-TieredCompilation]: Observed forbidden state: 1, 6 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:TieredStopAtLevel=1]: Observed forbidden state: 1, 0 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:TieredStopAtLevel=1]: Observed forbidden state: 1, 2 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:TieredStopAtLevel=1]: Observed forbidden state: 1, 4 ()
 * com.devexperts.util.jcstress.IndexedSetJCStressTest.SimpleSafePublication [-XX:TieredStopAtLevel=1]: Observed forbidden state: 1, 6 ()
 * </pre>
 */
public class IndexedSetJCStressTest {

    public static class Mutable {
        @Contended
        public final int key;
        public int x1;
        public int x2;
        public int x3;
        public int x4;

        public Mutable(int key) {
            this.key = key;
        }

        public void setX(int _x) {
            x1 = _x;
            x2 = _x;
            x3 = _x;
            x4 = _x;
        }

    }

    @State
    public static class MyState {
        final IndexedSet<Integer, Mutable> set;

        public MyState() {
            set = IndexedSet.createInt((IndexerFunction.IntKey<? super Mutable>) v -> v.key);
            set.ensureCapacity(16);
        }
    }

    @JCStressTest()
    @Description("Safe publication of mutable object")
    @Outcome(id = "0, 0", expect = Expect.ACCEPTABLE, desc = "Missed")
    @Outcome(id = "1, 8", expect = Expect.ACCEPTABLE, desc = "New element")
    @Outcome(expect = Expect.FORBIDDEN)
    public static class SimpleSafePublication {
        @Actor
        public void actor1(MyState s, II_Result r) {
            IndexedSet<Integer, Mutable> set = s.set;
            Mutable m = new Mutable(1);
            m.setX(2);
            set.put(m);
        }

        @Actor
        public void actor2(MyState s, II_Result r) {
            int x, y;
            Mutable m = s.set.getByKey(1);
            if (m == null) {
                x = 0;
                y = 0;
            } else {
                x = m.key;
                y = m.x1 + m.x2 + m.x3 + m.x4;
            }
            r.r1 = x;
            r.r2 = y;
        }
    }

    @JCStressTest()
    @Description("Put linearization")
    @Outcome(id = "-1, -1", expect = Expect.ACCEPTABLE, desc = "Missed both")
    @Outcome(id = "4, -1", expect = Expect.ACCEPTABLE, desc = "Missed second")
    @Outcome(id = "4, 8", expect = Expect.ACCEPTABLE, desc = "Got both")
    @Outcome(id = "-1, 8", expect = Expect.FORBIDDEN, desc = "Non linearizable")
    @Outcome(expect = Expect.FORBIDDEN)
    public static class SimplePutLinearization {
        @Actor
        public void actor1(MyState s, II_Result r) {
            IndexedSet<Integer, Mutable> set = s.set;
            Mutable m = new Mutable(1);
            m.setX(1);
            set.put(m);
            Mutable m2 = new Mutable(2);
            m2.setX(2);
            set.put(m2);
        }

        @Actor
        public void actor2(MyState s, II_Result r) {
            int x, y;
            IndexedSet<Integer, Mutable> set = s.set;
            Mutable m2 = set.getByKey(2);
            Mutable m = set.getByKey(1);
            x = m == null ? -1 : m.x1 + m.x2 + m.x3 + m.x4;
            y = m2 == null ? -1 : m2.x1 + m2.x2 + m2.x3 + m2.x4;
            r.r1 = x;
            r.r2 = y;
        }
    }
}
