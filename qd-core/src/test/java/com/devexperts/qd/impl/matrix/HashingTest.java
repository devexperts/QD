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

import java.util.Arrays;

public class HashingTest {

    /**
     * This is an analytical tool for verification and comparison of hashing approaches.
     */
    public static void main(String[] args) {
        int[] random = new int[100];
        int[] indexed = new int[100];
        int[] rational = new int[100];
        double totRandom = 0;
        double totIndexed = 0;
        double totRational = 0;

        int capacity = 1_000_000;
        int shift = Hashing.getShift(capacity);
        int[] matrix = new int[1 << (32 - shift)];

        // 1st sample is a 1/3, 2nd is random peek, last one is golden ratio
//        check(capacity, shift, matrix, 1431655765, 0);  // 78992.060 ms avg 166585.953 max 333171 as 0.3333333332557231=[3 1/3 ~6.9849E-10]=6.9849E-10
//        check(capacity, shift, matrix, -1908874343, 0); // 38002.719 ms avg  52924.846 max 111112 as 0.5555555580649525=[1 1/1 ~0.4444,1 1/2 ~0.2222,3 4/7 ~0.7777,1 5/9 ~2.0326115190982819E-7]=2.0326115190982819E-7
//        check(capacity, shift, matrix, 1550960401, 0);  //  8012.940 ms avg  11095.443 max  22191 as 0.3611111084464937=[2 1/2 ~0.5555,1 1/3 ~0.2499,3 4/11 ~0.3055,2 9/25 ~0.6944,1 13/36 ~3.4533441066741943E-6,289573 3764458/10424653 ~0.6893]=3.4533441066741943E-6
//        check(capacity, shift, matrix, -1802078381, 0); //    52.644 ms avg     50.569 max    101 as 0.5804209306370467=[1 1/1 ~0.4195,1 1/2 ~0.3216,2 3/5 ~0.4894,1 4/7 ~0.4406,1 7/12 ~0.4193,1 11/19 ~0.5319,1 18/31 ~0.2154,4 83/143 ~0.0276,36 3006/5179 ~0.0011,836 2513099/4329787 ~0.8064]=0.0011949774343520403
//        check(capacity, shift, matrix, 1689372091, 0);  //    29.171 ms avg      5.805 max     20 as 0.3933375913184136=[2 1/2 ~0.4266,1 1/3 ~0.5400,1 2/5 ~0.1665,5 11/28 ~0.3766,2 24/61 ~0.3908,2 59/150 ~0.0958,10 614/1561 ~0.0311,32 19707/50102 ~0.0117,84 1656002/4210129 ~0.8361]=0.011781933717429638
//        check(capacity, shift, matrix, -1640531535, 0); //    12.530 ms avg      1.181 max      3 as 0.6180339867714792=[1 1/1 ~0.3819,1 1/2 ~0.4721,1 2/3 ~0.4376,1 3/5 ~0.4508,1 5/8 ~0.4458,1 8/13 ~0.4477,1 13/21 ~0.447,1 21/34 ~0.4472,1 34/55 ~0.4471,1 55/89 ~0.4472,1 89/144 ~0.4472,1 144/233 ~0.4471,1 233/377 ~0.4474,1 377/610 ~0.4464,1 610/987 ~0.4491,1 987/1597 ~0.4421,1 1597/2584 ~0.4604,1 2584/4181 ~0.4126,1 4181/6765 ~0.5377,1 6765/10946 ~0.2101]=0.2101698974147439

        int magic = 0;
        int[] magics = new int[10];
        for (int repeat = 1; repeat <= 100000; repeat++) {
            for (int i = 0; i < magics.length; i++) {
                magics[i] = Hashing.nextMagic(magic);
            }
            int m1 = findIndexed(magics);
            int m2 = findRational(magics);
            double hr1 = check(capacity, shift, matrix, magics[m1], 10);
            totIndexed += add(indexed, hr1);
            if (m1 != m2) {
                double hr2 = check(capacity, shift, matrix, magics[m2], 10);
                totRational += add(rational, hr2);
                totRandom += add(random, m1 == 0 ? hr1 : m2 == 0 ? hr2 : check(capacity, shift, matrix, magics[0], 50));
            } else {
                totRational += add(rational, hr1);
                totRandom += add(random, m1 == 0 ? hr1 : check(capacity, shift, matrix, magics[0], 50));
            }
            magic = magics[m2];
            if (repeat % 100 == 0) {
                System.out.println(capacity + " #" + repeat);
                System.out.println("random   " + toString(totRandom / repeat, random));
                System.out.println("indexed  " + toString(totIndexed / repeat, indexed));
                System.out.println("rational " + toString(totRational / repeat, rational));
            }
        }
    }

    private static double add(int[] a, double r) {
        a[Math.min((int) (r / 10), a.length - 1)]++;
        return r;
    }

    private static String toString(double rate, int[] a) {
        int i = a.length;
        while (i > 0 && a[i - 1] == 0) {
            i--;
        }
        return String.format("%10.6f %s", rate, Arrays.toString(Arrays.copyOf(a, i)));
    }

    private static double check(int capacity, int shift, int[] matrix, int magic, double print) {
        Arrays.fill(matrix, 0);
        long start = System.nanoTime();
        long hits = 0;
        long hm = 0;
        for (int key = 1; key <= capacity; key++) {
            int distance = addKey(key, matrix, magic, shift);
            hits += distance;
            hm = Math.max(hm, distance);
        }
        long end = System.nanoTime();
        double hr = (double) hits / capacity;
        if (hr >= print) {
            System.out.println(magic + " in " + (end - start) / 1000 / 1000.0 + " ms avg " + hr + " max " + hm +
                " as " + printContinuedFraction(magic));
        }
        return hr;
    }

    private static int addKey(int key, int[] matrix, int magic, int shift) {
        int distance = 1;
        int index = (key * magic) >>> shift;
        int test;
        while ((test = matrix[index]) != key) {
            if (test == 0) {
                if (index > 0) {
                    matrix[index] = key;
                    return distance;
                }
                index = matrix.length;
            }
            index--;
            distance++;
        }
        throw new IllegalStateException("duplicate key " + matrix[index] + " for " + key + " at " + index);
    }

    // uses old evaluation formula from IndexedSet
    private static int findIndexed(int[] magics) {
        int best = 0;
        long eval = evaluateContinuedFractionIndexed(magics[best]);
        for (int i = 1; i < magics.length; i++) {
            long e = evaluateContinuedFractionIndexed(magics[i]);
            if (e < eval) {
                best = i;
                eval = e;
            }
        }
        return best;
    }

    // uses new evaluation formula from Hashing
    private static int findRational(int[] magics) {
        int best = 0;
        double eval = Hashing.evaluateContinuedFraction(magics[best]);
        for (int i = 1; i < magics.length; i++) {
            double e = Hashing.evaluateContinuedFraction(magics[i]);
            if (e > eval) {
                best = i;
                eval = e;
            }
        }
        return best;
    }

    // old evaluation formula from IndexedSet
    private static long evaluateContinuedFractionIndexed(int magic) {
        double d = (double) (magic & 0xFFFFFFFFL) / (1L << 32);
        long result = 1;
        long prev = 1;
        for (int i = 0; i < 15; i++) {
            d = 1 / d;
            long c = (long) d;
            result = Math.max(result, prev * c / (i + 1));
            prev = c;
            d -= c;
        }
        return result;
    }

    private static String printContinuedFraction(int magic) {
        double x = (double) (magic & 0xFFFFFFFFL) / (1L << 32);
        double rem = x;
        long p2 = 1;
        long q2 = 0;
        long p1 = 0;
        long q1 = 1;
        double grade = x;
        StringBuilder sb = new StringBuilder();
        sb.append(x).append("=[");
        for (int i = 0; i < 20; i++) {
            rem = 1 / rem;
            long a = (long) rem;
            rem -= a;
            long p = a * p1 + p2;
            long q = a * q1 + q2;
            p2 = p1;
            q2 = q1;
            p1 = p;
            q1 = q;
            double e = Math.abs(x * q - p) * q;
            grade = Math.min(grade, e);
            sb.append(a).append(" ").append(p).append("/").append(q);
            sb.append(" ~").append(e < 0.0001 ? e : (long) (e * 10000) / 10000.0).append(",");
            if (grade < 1e-6 || rem < 1e-6 || q > (1 << 20))
                break;
        }
        sb.setLength(sb.length() - 1);
        sb.append("]=").append(grade);
        return sb.toString();
    }
}
