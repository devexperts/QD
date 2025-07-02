/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.mars.jvm;

import com.devexperts.mars.common.MARSNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GcStressTestSnippet {

    public static void main(String[] args) throws Exception {
        MARSNode.getRoot().subNode("StressTest");

        List<Object> holder = new ArrayList<>();
        Random random = new Random();

        int iteration = 0;
        //noinspection InfiniteLoopStatement
        while (true) {
            // Randomly decide to drop references to encourage GC
            if (iteration % 1_000 == 0 && !holder.isEmpty()) {
                holder.clear(); // Removes references, makes objects eligible for GC
            }

            // Allocate new objects and add to list
            for (int i = 0; i < 1_000; i++) {
                // Allocate arrays to fill up the heap quickly
                holder.add(new byte[random.nextInt(1_000) + 1_000]);
            }

            iteration++;

            // Optional: slow down to avoid freezing the system
            if (iteration % 100 == 0) {
                Thread.sleep(10);
            }
        }
    }
}

