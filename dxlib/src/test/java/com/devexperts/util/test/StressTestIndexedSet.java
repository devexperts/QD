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
package com.devexperts.util.test;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexedSetStats;
import com.devexperts.util.IndexerFunction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StressTestIndexedSet<K> {
    private static final IndexerFunction<Long, Integer> LONG_INDEXER = new IndexerFunction.LongKey<Integer>() {
        @Override
        public long getNumberKey(Integer value) {
            return value;
        }

        @Override
        public String toString() {
            return "LONG_INDEXER";
        }
    };

    private static final IndexerFunction<String, Integer> STRING_INDEXER = new IndexerFunction<String, Integer>() {
        @Override
        public String getObjectKey(Integer value) {
            return value.toString();
        }

        @Override
        public String toString() {
            return "STRING_INDEXER";
        }
    };

    public static void main(String[] args) throws IOException {
        List<Integer> ids = load(args[0]);
        StressTestIndexedSet<?> test;
        if (args.length > 1) {
            String type = args[1];
            if (type.equalsIgnoreCase("string"))
                test = new StressTestIndexedSet<String>(convertToStrs(ids), STRING_INDEXER);
            else if (type.toLowerCase(Locale.US).startsWith("bad")) {
                int badness = Integer.parseInt(type.substring(3));
                test = new StressTestIndexedSet<Long>(convertToLongs(ids), new BadIndexer(badness));
            } else
                throw new IllegalArgumentException(type);
        } else
            test = new StressTestIndexedSet<Long>(convertToLongs(ids), LONG_INDEXER);
        test.go();
    }

    private static Map<Long, Integer> convertToLongs(List<Integer> ids) {
        Map<Long, Integer> result = new LinkedHashMap<Long, Integer>();
        for (Integer id : ids)
            result.put(id.longValue(), id);
        return result;
    }

    private static Map<String, Integer> convertToStrs(List<Integer> ids) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (Integer id : ids)
            result.put(id.toString(), id);
        return result;
    }

    private static List<Integer> load(String fileName) throws IOException {
        List<Integer> ids = new ArrayList<Integer>();
        try {
            for (int i = Integer.parseInt(fileName); --i >= 0;)
//              ids.add(i);
                ids.add((int) (Integer.MAX_VALUE * Math.random()));
            System.out.println("Generated " + ids.size() + " ids");
            return ids;
        } catch (NumberFormatException e) {}
        BufferedReader in = new BufferedReader(new FileReader(fileName));
        for (String line; (line = in.readLine()) != null;)
            ids.add(Integer.parseInt(line.trim()));
        in.close();
        System.out.println("Loaded " + ids.size() + " ids from \"" + fileName + "\"");
        return ids;
    }

    private final Map<K, Integer> map;
    private final IndexerFunction<K, Integer> indexer;

    private int maxAllocatedSize;
    private double maxAverageDistance;
    private double sumAverageDistance;
    private double maxAmortizedCost;
    private double sumAmortizedCost;

    public StressTestIndexedSet(Map<K, Integer> map, IndexerFunction<K, Integer> indexer) {
        this.map = map;
        this.indexer = indexer;
    }

    private void go() {
        System.out.println("Testing with " + indexer);
        for (int i = 1;; i++)
            performTest(i);
    }

    private void performTest(int i) {
        ArrayList<Integer> values = new ArrayList<Integer>(map.values());
        Collections.shuffle(values);
        IndexedSet<K, Integer> set = IndexedSet.create(indexer);
        set.addAll(values);
        IndexedSetStats stats = set.getStats();
        for (Map.Entry<K, Integer> entry : map.entrySet())
            if (set.getByKey(entry.getKey()) != entry.getValue())
                throw new AssertionError("Structure is broken at " + entry.getKey() + " with " + stats);
        sumAverageDistance += stats.getAverageDistance();
        sumAmortizedCost += stats.getAmortizedCost();
        if (i % 10 == 0 ||
            stats.getAllocatedSize() > maxAllocatedSize ||
            stats.getAverageDistance() > maxAverageDistance ||
            stats.getAmortizedCost() > maxAmortizedCost)
        {
            maxAllocatedSize = Math.max(maxAllocatedSize, stats.getAllocatedSize());
            maxAverageDistance = Math.max(maxAverageDistance, stats.getAverageDistance());
            maxAmortizedCost = Math.max(maxAmortizedCost, stats.getAmortizedCost());
            System.out.printf(Locale.US, "%d: %s [overall avgdist %.3f, amortized %.3f]%n",
                i, stats, sumAverageDistance / i, sumAmortizedCost / i);
        }
    }


    private static class BadIndexer implements IndexerFunction.LongKey<Integer> {
        private final int badness;

        BadIndexer(int badness) {
            this.badness = badness;
        }

        @Override
        public int hashCodeByValue(Integer value) {
            return value / badness;
        }

        @Override
        public int hashCodeByKey(Long key) {
            return (int) (key / badness);
        }

        @Override
        public long getNumberKey(Integer value) {
            return value;
        }

        @Override
        public String toString() {
            return "BAD_INDEXER[" + badness + "]";
        }
    }
}
