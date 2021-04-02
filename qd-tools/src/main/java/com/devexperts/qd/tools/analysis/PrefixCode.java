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
package com.devexperts.qd.tools.analysis;

import com.devexperts.util.ArrayUtil;

import java.util.Arrays;

public class PrefixCode {
    /*
     * Boundary package-merge algorithm from paper
     * "A Fast and Space-Economical Algorithm for length-Limited Coding" by
     * Jyrki Katajainen, Alistair Moffat, Andrew Turpin.
     * http://www.diku.dk/hjemmesider/ansatte/jyrki/Paper/ISAAC95.pdf
     *
     * Levels (j) are numbered from to 0 to L-1.
     * Alphabet letters are numbered from 0 to n-1.
     */

    private int n;
    private Chain[] alphabet = new Chain[0]; // alphabet sorted by weight
    private Chain[] levels = new Chain[0]; // two look-ahead chains for each level
    private boolean[] used = new boolean[0]; // true when both look-ahead chains for a level are already used

    private int[] bitCount = new int[0];
    private int[] code = new int[0];

    public int getSize() {
        return n;
    }

    public int getIndex(int order) {
        return alphabet[order].index;
    }

    public int getBitCount(int index) {
        return bitCount[index];
    }

    public int getCode(int index) {
        return code[index];
    }

    public void build(long[] weights, int maxBitCount) {
        if (maxBitCount > 32 || maxBitCount < 0 || weights.length > (1 << maxBitCount))
            throw new IllegalArgumentException("Invalid maxBitCount=" + maxBitCount);
        n = weights.length;
        alphabet = ArrayUtil.grow(alphabet, n + 1);
        for (int i = 0; i <= n; i++) {
            if (alphabet[i] == null)
                alphabet[i] = new Chain();
            alphabet[i].index = i;
            alphabet[i].weight = i < n ? weights[i] : Long.MAX_VALUE / 2; // sentinel value at the end
        }
        Arrays.sort(alphabet, 0, n);
        for (int i = 0; i < n; i++)
            alphabet[i].count = i + 1;
        levels = ArrayUtil.grow(levels, 2 * maxBitCount);
        used = ArrayUtil.grow(used, maxBitCount);
        for (int j = 0; j < maxBitCount; j++) {
            // two look-ahead chains at each level
            levels[2 * j] = alphabet[0];
            levels[2 * j + 1] = alphabet[1];
            used[j] = false;
        }
        for (int k = 0; k < n - 2; k++) // repeat n-2 times to create 2*n-2 chains (we already have 2!) from last level (L-1)
            create2(maxBitCount - 1);
        Chain chain = levels[2 * maxBitCount - 1]; // last taken chain at level L-1
        // travel through chain and compute bitCount deltas
        bitCount = ArrayUtil.grow(bitCount, n);
        Arrays.fill(bitCount, 0, n, 0);
        while (chain != null) {
            bitCount[chain.count - 1]++;
            chain = chain.prev;
        }
        // fold bitCount deltas into actual bit counts
        for (int i = n - 1; --i >= 0;)
            bitCount[i] += bitCount[i + 1];
        // copy bitCounts to weights and reorder alphabet by bitCount
        for (int i = 0; i < n; i++)
            alphabet[i].weight = bitCount[i];
        Arrays.sort(alphabet, 0, n);
        // compute codes and bitCounts by alphabet's index
        code = ArrayUtil.grow(code, n);
        int lastCode = -1;
        int lastBitCount = 0;
        for (int i = 0; i < n; i++) {
            Chain a = alphabet[i];
            int curBitCount = (int) a.weight; // will copy weight back into bitCount for an appropriate index
            lastCode = (lastCode + 1) << (curBitCount - lastBitCount);
            bitCount[a.index] = curBitCount;
            code[a.index] = lastCode;
            lastBitCount = curBitCount;
        }
        // Let GC remove the chains we don't need anymore
        Arrays.fill(levels, null);
    }

    // Creates two new chains at level j
    // It writes them to levels[2*j] and levels[2*j+1] and sets used[j] = false
    private void create2(int j) {
        Chain last = levels[2 * j + 1];
        levels[2 * j] = last = create(j, last);
        levels[2 * j + 1] = create(j, last);
        used[j] = false;
    }

    // Create new chain at level j when j > 0
    private Chain create(int j, Chain last) {
        if (j == 0)
            return alphabet[last.count];
        if (used[j - 1]) // create two new chains at prev level if needed (if they were used by previous create)
            create2(j - 1);
        Chain second = levels[2 * j - 1]; // second chain at previous level
        long sum = levels[2 * j - 2].weight + second.weight;
        long weight = alphabet[last.count].weight;
        if (weight > sum) {
            used[j - 1] = true; // using both look-ahead chains on previous level
            return new Chain(sum, last.count, second);
        }
        return new Chain(weight, last.count + 1, last.prev);
    }

    static final class Chain implements Comparable<Chain> {
        int index;
        long weight;
        int count;
        Chain prev;

        Chain() {}

        Chain(long weight, int count, Chain prev) {
            this.index = -1; // to fail fast if we accidentally use it in a wrong place
            this.weight = weight;
            this.count = count;
            this.prev = prev;
        }

        public int compareTo(Chain o) {
            return weight < o.weight ? -1 : weight > o.weight ? 1 : index - o.index;
        }
    }
}
