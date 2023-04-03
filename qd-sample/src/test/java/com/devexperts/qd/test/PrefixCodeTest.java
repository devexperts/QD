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
package com.devexperts.qd.test;

import com.devexperts.qd.tools.analysis.PrefixCode;
import org.junit.Test;

import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class PrefixCodeTest {

    @Test
    public void test1() {
        PrefixCode code = new PrefixCode();
        code.build(new long[] { 1, 2 }, 10);
        assertEquals(1, code.getBitCount(0));
        assertEquals(1, code.getBitCount(1));
        assertEquals(0, code.getCode(0));
        assertEquals(1, code.getCode(1));
        assertEquals(0, code.getIndex(0));
        assertEquals(1, code.getIndex(1));
    }

    @Test
    public void test2() {
        PrefixCode code = new PrefixCode();
        code.build(new long[] { 1, 2, 4 }, 10);
        assertEquals(2, code.getBitCount(0));
        assertEquals(2, code.getBitCount(1));
        assertEquals(1, code.getBitCount(2));
        assertEquals(2, code.getCode(0));
        assertEquals(3, code.getCode(1));
        assertEquals(0, code.getCode(2));
        assertEquals(2, code.getIndex(0));
        assertEquals(0, code.getIndex(1));
        assertEquals(1, code.getIndex(2));
    }

    @Test
    public void testBalance() {
        int b = 10;
        int n = 1 << b;
        PrefixCode code = new PrefixCode();
        code.build(new long[n], b);
        for (int i = 0; i < n; i++)
            assertEquals(b, code.getBitCount(i));
        code.build(new long[n], b + 1);
        for (int i = 0; i < n; i++)
            assertEquals(b, code.getBitCount(i));
        code.build(new long[n], b + 2);
        for (int i = 0; i < n; i++)
            assertEquals(b, code.getBitCount(i));
    }

    @Test
    public void testRandom() {
        int n = 1000;
        Random rnd = new Random(1234);
        long[] w = new long[n];
        for (int i = 0; i < n; i++)
            w[i] = rnd.nextInt(10000);
        // Run Huffman
        PriorityQueue<Node> queue = new PriorityQueue<Node>();
        for (int i = 0; i < n; i++)
            queue.add(new Node(w[i]));
        while (queue.size() > 1) {
            Node one = queue.remove();
            Node two = queue.remove();
            long sw = one.w + two.w;
            queue.add(new Node(sw, one.total + two.total + sw, Math.max(one.depth, two.depth) + 1));
        }
        Node node = queue.remove();
        int maxBitCount = node.depth;
        // now build prefix code and compare total bit lenght
        PrefixCode code = new PrefixCode();
        code.build(w, maxBitCount);
        long total = 0;
        for (int i = 0; i < n; i++)
            total += w[i] * code.getBitCount(i);
        assertEquals(node.total, total);
    }

    private static class Node implements Comparable<Node> {
        long w;
        long total;
        int depth;

        private Node(long w) {
            this.w = w;
        }

        private Node(long w, long total, int depth) {
            this.w = w;
            this.total = total;
            this.depth = depth;
        }

        public int compareTo(Node o) {
            return Long.compare(w, o.w);
        }
    }
}
