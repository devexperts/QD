/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.model.market;

import junit.framework.TestCase;

import java.util.Iterator;

/**
 * Unit test for {@link CheckedTreeList} class (test check/uncheck functionality).
 */
public class CheckedTreeTest extends TestCase {

    private CheckedTreeList<Integer> tree;

    public CheckedTreeTest(String s) {
        super(s);
    }

    @Override
    public void setUp() {
        tree = new CheckedTreeList<Integer>((o1, o2) -> o1 < o2 ? -1 : o1 > o2 ? 1 : 0)
        {
            @Override
            public boolean check(Node<Integer> node) {
                if (node.getValue() % 2 == 0)
                    return super.check(node);
                return false;
            }
        };
    }

    public void testCheckedTree() {
        tree.insert(1);
        tree.insert(6);
        tree.insert(3);
        tree.insert(2);
        tree.insert(8);
        tree.insert(5);
        tree.insert(4);
        assert (tree.validateTree());

        assertEquals(4, tree.size());
        assertEquals(-1, tree.indexOf(1));
        assertEquals(-1, tree.indexOf(3));
        assertEquals(-1, tree.indexOf(5));
        assertEquals(0, tree.indexOf(2));
        assertEquals(1, tree.indexOf(4));
        assertEquals(2, tree.indexOf(6));
        assertEquals(3, tree.indexOf(8));
        assert (tree.validateTree());
    }

    public void testCheckedTree1() {
        tree.insert(6);
        tree.insert(2);
        tree.insert(9);
        tree.insert(5);
        assert (tree.validateTree());

        assertEquals(2, tree.size());
        assertEquals(6, (int) tree.delete(6));
        assert (tree.validateTree());
    }

    public void testInsertDelete() {
        tree.insert(3);
        tree.insert(6);
        tree.insert(4);
        tree.insert(7);
        tree.insert(1);
        tree.insert(2);
        tree.insert(9);
        tree.insert(8);

        assertEquals(4, tree.size());
        assert (tree.validateTree());

        assertEquals(2, (int) tree.delete(2));
        assertEquals(null, tree.delete(2));
        assertEquals(null, tree.delete(10));
        assertEquals(4, (int) tree.delete(4));
        assertEquals(null, tree.delete(0));
        assertEquals(8, (int) tree.delete(8));
        assertEquals(null, tree.delete(8));
        assertEquals(7, (int) tree.delete(7));
        assertEquals(null, tree.delete(5));
        assertEquals(3, (int) tree.delete(3));
        assertEquals(1, (int) tree.delete(1));

        assertEquals(1, tree.size());
        assert (tree.validateTree());
    }

    public void testListOperations() {
        assertTrue(tree.isEmpty());

        for (int i = 0; i < 50; i++)
            tree.insert(i);

        assert (tree.validateTree());
        assertEquals(25, tree.size());

        for (int i = 0; i < 25; i++) {
            assertEquals(i * 2, (int) tree.get(i));
        }

        int counter = 0;
        for (Integer treeValue : tree)
            assertEquals(counter++ * 2, (int) treeValue);

        for (int i = 0; i < 50; i++) {
            assertEquals(i % 2 == 0, tree.contains(i));
            assertEquals(i, (int) tree.find(i));
            assertEquals(i % 2 == 0 ? (i / 2) : -1, tree.indexOf(i));
        }

        for (int i = 49; i >= 0; --i) {
            assertEquals(i, (int) tree.delete(i));
            assertTrue(!tree.contains(i));
            assertEquals(-1, tree.indexOf(i));
            assertEquals((i + 1) / 2, tree.size());
            assert (tree.validateTree());
        }

        assert (tree.validateTree());
        assertTrue(tree.isEmpty());
    }


    public void testTreeOperations() {
        for (int i = 0; i < 50; i++)
            tree.insert(i);

        assert (tree.validateTree());
        CheckedTreeList.Node<Integer> node = tree.getNode(42);
        assertEquals(42, (int) node.getValue());

        for (int i = 0; i < 50; i++) {
            node = tree.getNode(i);
            assertEquals((i % 2 == 0), node.checked);
            assertEquals((i % 2 == 0) ? i / 2 : -1, tree.getIndex(node));
        }
        assert (tree.validateTree());

        int counter = 0;
        for (Iterator<Integer> iterator = tree.treeIterator(); iterator.hasNext(); )
            assertEquals(counter++, (int) iterator.next());
    }
}
