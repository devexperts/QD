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
package com.devexperts.qd.test;

public class MicroTestIntfVsNullSpeed {
    interface Filter {
        boolean accept(int i);
    }

    private static final Filter ANY = new Filter() {
        public boolean accept(int i) {
            return true;
        }

        public String toString() {
            return "ANY";
        }
    };

    private static final Filter NONE = new Filter() {
        public boolean accept(int i) {
            return false;
        }

        public String toString() {
            return "NONE";
        }
    };

    private static final Filter EVEN = new Filter() {
        public boolean accept(int i) {
            return (i & 1) == 0;
        }

        public String toString() {
            return "EVEN";
        }
    };

    private static final int CNT = 100000000;
    private static final Filter[] filters = new Filter[] {null, ANY, NONE, EVEN};

    public static int dummy;

    public static void main(String[] args) {
        for (int pass = 1; pass <= 3; pass++) {
            System.out.println("Pass #" + pass);
            for (int i = 0; i < filters.length; i++)
                dummy += checkCond(filters[i]);
            for (int i = 1; i < filters.length; i++)
                dummy += checkDirect(filters[i]);
        }

    }

    private static int checkCond(Filter filter) {
        long time = System.currentTimeMillis();
        int result = 0;
        for (int i = 0; i < CNT; i++)
            if (filter == null || filter.accept(i))
                result++;
        time = System.currentTimeMillis() - time;
        System.out.println("checkCond(" + filter + ") in " + time + " ms");
        return result;
    }

    private static int checkDirect(Filter filter) {
        long time = System.currentTimeMillis();
        int result = 0;
        for (int i = 0; i < CNT; i++)
            if (filter.accept(i))
                result++;
        time = System.currentTimeMillis() - time;
        System.out.println("checkDirect(" + filter + ") in " + time + " ms");
        return result;
    }
}
