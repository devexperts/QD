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
package com.devexperts.qd.stats;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class QDStatsTest {

    private static final QDStats.SType BOX = new QDStats.SType("Box", QDStats.FLAG_IO | QDStats.FLAG_RID);
    private static final QDStats.SType APPLE = new QDStats.SType("Apple", QDStats.FLAG_IO | QDStats.FLAG_RID);
    private static final QDStats.SType ORANGE = new QDStats.SType("Orange", QDStats.FLAG_IO | QDStats.FLAG_RID);

    private static final QDStats.SValue VALUE = QDStats.SValue.IO_READ_BYTES;

    @Test
    public void testSimple() {
        // Any
        //   Box
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);

        updateValue(box, 1);
        updateValue(box, 2);
        updateValue(box, 4);

        assertEquals(7, box.getValue(VALUE));
        assertEquals(7, root.getValue(VALUE));
    }

    @Test
    public void testGet() {
        // Any
        //   Box1
        //   Box2{key=value2}
        //   Box3{key=value3}
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box1 = root.create(BOX);
        QDStats box2 = root.create(BOX, "key=value2");
        QDStats box3 = root.getOrCreate(BOX, "key=value3");

        assertNotSame(box1, box3);
        assertNotSame(box2, box3);

        assertSame(box1, root.get(BOX));
        assertSame(box2, root.get(BOX, "key=value2"));
        assertSame(box3, root.get(BOX, "key=value3"));

        List<QDStats> stats = root.getAll(BOX);
        assertNotNull(stats);
        assertEquals(3, stats.size());

        assertSame(box1, root.getOrCreate(BOX));
        assertSame(box2, root.getOrCreate(BOX, "key=value2"));

        assertSame(QDStats.VOID, root.getOrVoid(APPLE));
    }

    @Test
    public void testEquivalenceRelation() {
        QDStats.SType A = new QDStats.SType("A");
        QDStats.SType B = new QDStats.SType("B", 0, A);
        QDStats.SType C = new QDStats.SType("C", 0, B);

        assertTrue(A.isSameAs(A));
        assertTrue(A.isSameAs(B));
        assertTrue(A.isSameAs(C));
        assertTrue(B.isSameAs(C));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEquivalence() {
        QDStats.SType A = new QDStats.SType("A");
        QDStats.SType B = new QDStats.SType("B");
        QDStats.SType C = new QDStats.SType("C", 0, A, B);
    }

    @Test
    public void testSimpleTree() {
        // Any
        //   Box=4
        //     Apple=1
        //     Orange=2
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats apple = box.create(APPLE);
        QDStats orange = box.create(ORANGE);

        updateValue(apple, 1);
        updateValue(orange, 2);
        updateValue(box, 4);

        assertEquals(1, apple.getValue(VALUE));
        assertEquals(2, orange.getValue(VALUE));
        assertEquals(4, box.getValue(VALUE, true));

        assertEquals(7, box.getValue(VALUE));
        assertEquals(7, root.getValue(VALUE));
        assertEquals(1, root.get(APPLE).getValue(VALUE));
        assertEquals(2, root.get(ORANGE).getValue(VALUE));
    }

    @Test
    public void testSumTotal() {
        // Any
        //   Box1
        //     Apple1
        //     Orange1
        //     Orange2
        //   Box2
        //     Apple2
        //     Apple3
        //     Orange3
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box1 = root.create(BOX);
        QDStats apple1 = box1.create(APPLE);
        QDStats orange1 = box1.create(ORANGE);
        QDStats orange2 = box1.create(ORANGE);

        QDStats box2 = root.create(BOX);
        QDStats apple2 = box2.create(APPLE);
        QDStats apple3 = box2.create(APPLE);
        QDStats orange3 = box2.create(ORANGE);

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        updateValue(apple3, 4);
        updateValue(orange1, 10);
        updateValue(orange2, 20);
        updateValue(orange3, 40);

        assertEquals(31, box1.getValue(VALUE));
        assertEquals(46, box2.getValue(VALUE));
        assertEquals(77, root.getValue(VALUE));

        // All stats are summed across all nodes
        assertEquals(7, root.get(APPLE).getValue(VALUE));
        assertEquals(70, root.get(ORANGE).getValue(VALUE));
    }

    @Test
    public void testSumMixed() {
        // Any
        //   Box
        //     Apple1
        //     Apple2
        //     Orange1
        //     Orange2
        //       Apple3
        //       Orange3
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);

        QDStats apple1 = box.create(APPLE);
        QDStats apple2 = box.create(APPLE);

        QDStats orange1 = box.create(ORANGE);
        QDStats orange2 = box.create(ORANGE);

        // Mixed hierarchy of types
        QDStats apple3 = orange2.create(APPLE);
        QDStats orange3 = orange2.create(ORANGE);

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        updateValue(apple3, 4);

        updateValue(orange1, 10);
        updateValue(orange2, 20);
        updateValue(orange3, 40);

        assertEquals(64, orange2.getValue(VALUE));

        // All stats are summed across all nodes
        assertEquals(7, root.get(APPLE).getValue(VALUE));
        assertEquals(70, root.get(ORANGE).getValue(VALUE));
    }

    @Test
    public void testSumWithClosed() {
        // Any
        //   Box
        //     Apple1
        //     Apple2
        //     Orange1
        //     Orange2
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats apple1 = box.create(APPLE, "n=apple1");
        QDStats apple2 = box.create(APPLE, "n=apple2");
        QDStats orange1 = box.create(ORANGE, "n=orange1");
        QDStats orange2 = box.create(ORANGE, "n=orange2");

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        updateValue(orange1, 10);
        updateValue(orange2, 20);

        assertEquals(3, root.get(APPLE).getValue(VALUE));
        assertEquals(30, root.get(ORANGE).getValue(VALUE));
        assertEquals(33, box.getValue(VALUE));
        assertEquals(33, root.getValue(VALUE));

        // Any
        //   Box
        //     Apple1
        //     Orange1
        apple2.close();
        orange2.close();

        assertEquals(3, root.get(APPLE).getValue(VALUE));
        assertEquals(30, root.get(ORANGE).getValue(VALUE));
        assertEquals(11, box.getValue(VALUE));
        assertEquals(33, root.getValue(VALUE));
    }

    @Test
    public void testSameTypeTree() {
        // Any
        //   Box1
        //     Box11
        //     Box12
        //   Box2
        //     Box21
        //     Box22
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);

        QDStats box1 = box.create(BOX, "n=box1");
        QDStats box11 = box1.create(BOX, "nn=box11");
        QDStats box12 = box1.create(BOX, "nn=box12");

        QDStats box2 = box.create(BOX, "n=box2");
        QDStats box21 = box2.create(BOX, "nn=box21");
        QDStats box22 = box2.create(BOX, "nn=box22");

        updateValue(box11, 1);
        updateValue(box12, 2);
        updateValue(box21, 10);
        updateValue(box22, 20);

        assertEquals(0, box1.getValue(VALUE, true));
        assertEquals(3, box1.getValue(VALUE));

        assertEquals(0, box2.getValue(VALUE, true));
        assertEquals(30, box2.getValue(VALUE));

        assertEquals(33, box.getValue(VALUE));
        assertEquals(33, root.getValue(VALUE));

        // Any
        //   Box1
        //     Box11
        //   Box2
        //     Box21
        box12.close();
        box22.close();

        assertEquals(2, box1.getValue(VALUE, true));
        assertEquals(3, box1.getValue(VALUE));
        assertEquals(20, box2.getValue(VALUE, true));
        assertEquals(30, box2.getValue(VALUE));

        assertEquals(33, box.getValue(VALUE));
        assertEquals(33, root.getValue(VALUE));
    }

    @Test
    public void testCloseMiddleStats() {
        // Any
        //   Box
        //     Apple
        //       Orange
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats apple = box.create(APPLE, "n=apple1");
        QDStats orange = apple.create(ORANGE, "n=orange1");

        updateValue(box, 1);
        updateValue(apple, 2);
        updateValue(orange, 4);

        assertEquals(7, box.getValue(VALUE));
        apple.close();
        assertEquals(5, box.getValue(VALUE));
        assertEquals(7, root.getValue(VALUE));
    }

    @Test
    public void testCloseRootChildStats() {
        // Any
        //   Box
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);

        updateValue(box, 1);

        assertEquals(1, box.getValue(VALUE));
        box.close();

        // Since root (Any) node does not aggregate stats, closing any node under root would loose values
        assertEquals(0, root.getValue(VALUE));
    }

    @Test
    public void testCloseHierarchy() {
        // Any
        //   Box1
        //     ...
        //       Box10
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats stats1 = root.create(BOX, "n1=1");
        QDStats stats2 = stats1.create(APPLE, "n2=2");
        QDStats stats3 = stats2.create(ORANGE, "n3=3");
        QDStats stats4 = stats3.create(BOX, "n4=4");
        QDStats stats5 = stats4.create(APPLE, "n5=5");
        QDStats stats6 = stats5.create(ORANGE, "n6=6");
        QDStats stats7 = stats6.create(BOX, "n7=7");
        QDStats stats8 = stats7.create(APPLE, "n8=8");
        QDStats stats9 = stats8.create(ORANGE, "n9=9");
        QDStats stats10 = stats9.create(BOX, "n10=10");

        updateValue(stats10, 100);

        // Closed value is stored in the parent
        stats2.close();
        assertEquals(100, root.getValue(VALUE));

        // Since root (Any) node does not aggregate stats, closing any node under root would loose values
        stats1.close();
        assertEquals(0, root.getValue(VALUE));
    }

    @Test
    public void testCloseHierarchySameType() {
        QDStats root = new QDStats(QDStats.SType.ANY);

        int count = 10;
        QDStats[] statsArray = new QDStats[count];
        statsArray[0] = root.create(BOX, "n0=0");
        for (int i = 1; i < 10; i++) {
            statsArray[i] = statsArray[i - 1].create(BOX, "n" + i + "=" + i);
            statsArray[i].updateIOReadBytes(1);
        }

        statsArray[1].close();
        assertEquals(9, root.get(BOX).getValue(VALUE));
    }

    @Test
    public void testSumNodeOnSameType() {
        // Any
        //   Box
        //     Apple
        //       Box1
        //         Box2
        //     #Box <- sumNode should be created here
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats apple = box.create(APPLE);
        QDStats box1 = apple.create(BOX);
        QDStats box2 = box1.create(BOX);

        updateValue(box, 1);
        updateValue(box1, 2);
        updateValue(box2, 4);

        assertNotNull(root.get(BOX).get(BOX));
        assertEquals(7, root.get(BOX).getValue(VALUE));

        box2.close();
        assertEquals(7, root.get(BOX).getValue(VALUE));
    }

    @Test
    public void testSumNodeOnSameType2() {
        // Any
        //   Box
        //     Apple
        //       Apple
        //         Box
        //       #Box <- sumNode should be present
        //     #Box <- sumNode should be present
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats apple1 = box.create(APPLE);
        QDStats apple2 = apple1.create(APPLE);
        QDStats box1 = apple2.create(BOX);

        updateValue(box1, 1);

        assertNotNull(root.get(BOX).get(BOX));
        assertNotNull(root.get(BOX).get(APPLE).get(BOX));
        assertEquals(1, root.get(BOX).getValue(VALUE));

        // Artifacts of getting value on a sumNode or from direct node
        assertEquals(0, root.get(APPLE).getValue(VALUE));
        assertEquals(1, apple2.getValue(VALUE));
        assertEquals(1, apple2.getValue(VALUE));

        box1.close();
        assertEquals(1, root.get(BOX).getValue(VALUE));

        // Artifacts of getting value on a sumNode or from direct node
        assertEquals(0, root.get(APPLE).getValue(VALUE));
        assertEquals(0, apple2.getValue(VALUE));
    }

    @Test
    public void testSameTypesTree() {
        // Any
        //   Box
        //     OtherBox
        //       AnotherBox
        //     <- No sum nodes should be created, since all boxes are "same" types
        QDStats.SType OTHER_BOX = new QDStats.SType("OtherBox", 0, BOX);
        QDStats.SType ANOTHER_BOX = new QDStats.SType("OtherBox", 0, OTHER_BOX);

        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        QDStats otherBox = box.create(OTHER_BOX);
        QDStats anotherBox = otherBox.create(ANOTHER_BOX);

        updateValue(anotherBox, 1);
        assertNull(root.get(BOX).get(ANOTHER_BOX));
        assertEquals(1, root.get(BOX).getValue(VALUE));

        // Unfortunately it means that "same" nodes are not propagated up the tree
        assertNull(root.get(OTHER_BOX));
        assertNull(root.get(ANOTHER_BOX));
    }

    @Test
    public void testCloseSumNodeFirstTree() {
        // Any
        //   Box0
        //     Box1
        //       Apple1
        //     #Apple
        //     Box2
        //       Apple2
        //   #Apple
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box0 = root.create(BOX, "n=0");
        QDStats box1 = box0.create(BOX, "nn=1");
        QDStats apple1 = box1.create(APPLE);
        QDStats box2 = box0.create(BOX, "n=2");
        QDStats apple2 = box2.create(APPLE);

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        assertEquals(3, root.get(APPLE).getValue(VALUE));

        // Close box1 so that sumNode #Apple is the first in the children list:
        // Any
        //   Box0
        //     #Apple
        //     Box2
        //       Apple2
        //   #Apple
        box1.close();
        apple1.close();
        assertEquals(3, root.get(APPLE).getValue(VALUE));

        // Close box0
        box0.close();
        apple2.close();

        // Check that all stats are summed up correctly
        QDStats sumNode = root.get(APPLE);
        assertEquals(3, sumNode.getValue(VALUE));
        assertNull(sumNode.get(APPLE));
    }

    @Test
    public void testCloseSumNode() {
        // Any
        //   Box0
        //     Box1
        //       Apple1
        //     #Apple <-- close()
        //     Box2
        //       Apple2
        //   #Apple
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box0 = root.create(BOX, "n=0");
        QDStats box1 = box0.create(BOX, "nn=1");
        QDStats apple1 = box1.create(APPLE);
        QDStats box2 = box0.create(BOX, "n=2");
        QDStats apple2 = box2.create(APPLE);

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        assertEquals(3, root.get(APPLE).getValue(VALUE));

        // As a result of removing sum node, all apples in the boxes should be removed as well:
        // Any
        //   Box0
        //     Box1
        //     Box2
        //   #Apple
        box0.get(APPLE).close();

        assertNull(box0.get(APPLE));
        assertNull(box1.get(APPLE));
        assertNull(box2.get(APPLE));
        assertNull(root.get(APPLE).get(APPLE));
        assertNotNull(root.get(APPLE));
        assertEquals(3, root.get(APPLE).getValue(VALUE));
    }

    @Test
    public void testRecreateSumNode() {
        // Any
        //   Box0
        //     Box1
        //       Apple1
        //     #Apple <-- close()
        //     Box2
        //       Apple2
        //   #Apple
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box0 = root.create(BOX, "n=0");
        QDStats box1 = box0.create(BOX, "nn=1");
        QDStats apple1 = box1.create(APPLE);
        QDStats box2 = box0.create(BOX, "n=2");
        QDStats apple2 = box2.create(APPLE);

        updateValue(apple1, 1);
        updateValue(apple2, 2);
        assertEquals(3, root.get(APPLE).getValue(VALUE));

        QDStats oldSum = box0.get(APPLE);
        oldSum.close();

        // Any
        //   Box0
        //     Box1
        //       Apple3
        //     #Apple <-- should be recreated
        //     Box2
        //       Apple4
        //   #Apple
        updateValue(box1.create(APPLE), 4);
        updateValue(box2.create(APPLE), 8);

        QDStats newSum = box0.get(APPLE);
        assertNotNull(newSum);
        assertNotSame(oldSum, newSum);
        assertEquals(15, root.get(APPLE).getValue(VALUE));
    }

    @Test
    public void testGetValue() {
        // Any
        //   Box0=1
        //     Box1=2
        //       Apple1=4
        //     #Apple=8
        //   #Apple=16
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box0 = root.create(BOX);
        QDStats box1 = box0.create(BOX);
        QDStats apple1 = box1.create(APPLE);

        updateValue(box0, 1);
        updateValue(box1, 2);
        updateValue(apple1, 4);
        updateValue(box0.get(APPLE), 8);
        updateValue(root.get(APPLE), 16);

        assertEquals(1, box0.getValue(VALUE, true));
        assertEquals(15, box0.getValue(VALUE));

        assertEquals(2, box1.getValue(VALUE, true));
        assertEquals(6, box1.getValue(VALUE));

        assertEquals(8, box0.get(APPLE).getValue(VALUE, true));
        assertEquals(12, box0.get(APPLE).getValue(VALUE));

        assertEquals(16, root.get(APPLE).getValue(VALUE, true));
        assertEquals(28, root.get(APPLE).getValue(VALUE));
    }

    // This test is similar to testGetValue() but using addValues() instead of getValue()
    @Test
    public void testAddValues() {
        // Any
        //   Box0=1
        //     Box1=2
        //       Apple1=4
        //     #Apple=8
        //   #Apple=16
        QDStats root = new QDStats();
        // Initialize with just a record count instead of real data scheme
        //noinspection deprecation
        root.initRoot(QDStats.SType.ANY, 1);

        QDStats box0 = root.create(BOX);
        QDStats box1 = box0.create(BOX);
        QDStats apple1 = box1.create(APPLE);

        updateRidValue(box0, 1);
        updateRidValue(box1, 2);
        updateRidValue(apple1, 4);
        updateRidValue(box0.get(APPLE), 8);
        updateRidValue(root.get(APPLE), 16);

        assertRidValue(1, box0, true);
        assertRidValue(15, box0, false);

        assertRidValue(2, box1, true);
        assertRidValue(6, box1, false);

        assertRidValue(8, box0.get(APPLE), true);
        assertRidValue(12, box0.get(APPLE), false);

        assertRidValue(16, root.get(APPLE), true);
        assertRidValue(28, root.get(APPLE), false);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testNoRid() {
        QDStats root = new QDStats();
        root.initRoot(QDStats.SType.ANY, 100);
        QDStats box = root.create(BOX, null, false);
        QDStats apple = box.create(BOX, null, true);
        QDStats orange = root.create(BOX, null);

        assertEquals(100, root.getRidCount());
        assertEquals(0, box.getRidCount());
        assertEquals(0, apple.getRidCount());
        assertEquals(100, orange.getRidCount());
    }

    //TODO Fix QDStats API to avoid erroneously adding stats on sumNodes.
    @Test
    public void testCreateOnSumNode() {
        // Any
        //   Box
        //     Apple
        //   #Apple
        //     Orange
        QDStats root = new QDStats(QDStats.SType.ANY);
        QDStats box = root.create(BOX);
        box.create(APPLE);

        // Create stats node on sum node
        QDStats orange = root.get(APPLE).create(ORANGE);
        updateValue(orange, 1);

        assertEquals(1, root.get(APPLE).getValue(VALUE));
    }

    private void updateValue(QDStats stats, long value) {
        stats.updateIOReadBytes(value);
    }

    private void updateRidValue(QDStats stats, long value) {
        stats.updateIOReadRecordBytes(0, value);
    }

    private static void assertRidValue(long expected, QDStats stats, boolean localOnly) {
        long[] values = new long[stats.getRidCount()];
        values[0] = 0;
        stats.addValues(VALUE, localOnly, values);
        assertEquals(expected, values[0]);
    }
}
