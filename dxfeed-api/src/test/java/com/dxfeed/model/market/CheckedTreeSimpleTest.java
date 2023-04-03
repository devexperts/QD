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
package com.dxfeed.model.market;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link CheckedTreeList} class (simple case where all nodes are checked).
 */
public class CheckedTreeSimpleTest {

    CheckedTreeList<Integer> tree;

    @Before
    public void setUp() {
        tree = new CheckedTreeList<>(Integer::compareTo);
    }

    @After
    public void tearDown() {
        tree = null;
    }

    @Test
    public void testSimpleTree() {
        assert(tree.validateTree());
        assertEquals(0, tree.size());

        tree.insert(6);
        tree.insert(2);
        tree.insert(9);
        tree.insert(5);
        assert(tree.validateTree());

        assertEquals(4, tree.size());
        assertEquals(6, (int) tree.delete(6));
        assert(tree.validateTree());
    }

    @Test
    public void testInsertDelete() {
        tree.insert(3);
        tree.insert(6);
        tree.insert(4);
        tree.insert(7);
        tree.insert(1);
        tree.insert(2);
        tree.insert(9);
        tree.insert(8);
        assert(tree.validateTree());

        assertEquals(1, (int) tree.delete(1));
        assertNull(tree.delete(1));
        assertNull(tree.delete(10));
        assertEquals(4, (int) tree.delete(4));
        assertNull(tree.delete(0));
        assertEquals(8, (int) tree.delete(8));
        assertNull(tree.delete(8));
        assertNull(tree.delete(5));

        assertEquals(5, tree.size());
        assert(tree.validateTree());
    }

    @Test
    public void testListOperations() {
        for (int i = 0; i < 50; i++)
            tree.insert(i);

        assert(tree.validateTree());
        assertEquals(50, tree.size());

        for (int i = 0; i < 50; i++) {
            assertEquals(i, (int) tree.get(i));
            assertTrue(tree.contains(i));
            assertEquals(i, tree.indexOf(i));
        }

        int counter = 0;
        for (Integer treeValue: tree)
            assertEquals(counter++, (int) treeValue);

        for (int i = 49; i >= 0; --i) {
            assertEquals(i, (int) tree.delete(i));
            assertFalse(tree.contains(i));
            assertEquals(-1, tree.indexOf(i));
            assertEquals(i, tree.size());
            assert(tree.validateTree());
        }
        assertTrue(tree.isEmpty());
        assert(tree.validateTree());
    }

    @Test
    public void testTreeOperations() {
        for (int i = 0; i < 50; i++)
            tree.insert(i);

        assert(tree.validateTree());
        CheckedTreeList.Node<Integer> node = tree.getNode(42);
        assertEquals(42, (int) node.getValue());

        for (int i = 0; i < 50; i++) {
            node = tree.getNode(i);
            assertTrue(node.checked);
            assertEquals(i, tree.getIndex(node));
        }
        assertEquals(tree.size(), tree.treeSize());
        assert(tree.validateTree());

        int counter = 0;
        for (Iterator<Integer> iterator = tree.treeIterator(); iterator.hasNext(); )
            assertEquals(counter++, (int) iterator.next());
    }

    @Test
    public void testInsertDuplicate() {
        try {
            tree.insert(100);
            tree.insert(100);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
        assertEquals(1, tree.size());
    }

    @Test
    public void testRandomTree() {
        final int size = 10000;
        SortedSet<Integer> standard = new TreeSet<>();
        tree.clear();

        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < size; i++) {
            int value = r.nextInt();

            if (standard.add(value))
                tree.insert(value);

            assert(tree.validateTree());
        }

        assertEquals(standard.size(), tree.size());
        assertTrue(standard.containsAll(tree));
        assertTrue(tree.containsAll(standard));

        for (int i = 0; i < size / 5; i++) {
            int value = r.nextInt(standard.size());

            boolean removed1 = standard.remove(value);
            boolean removed2 = tree.delete(value) != null;

            assert(tree.validateTree());

            assertEquals(removed1, removed2);
            assertEquals(standard.size(), tree.size());
        }
        assertTrue(standard.containsAll(tree));
        assertTrue(tree.containsAll(standard));
    }
}
