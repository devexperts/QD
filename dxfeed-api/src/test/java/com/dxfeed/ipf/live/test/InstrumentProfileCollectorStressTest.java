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
package com.dxfeed.ipf.live.test;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Stress test trying to mimic InstrumentProfile leaking in IPC.
 * Run only manually and collect heap dumps to detect memory leaks.
 */
public class InstrumentProfileCollectorStressTest {

    public static int PROFILE_COUNT = 10_000;
    public static int ITERATION_COUNT = 100;

    private RandomInstrumentProfiles profiles;
    private InstrumentProfileCollector collector;

    @Before
    public void setUp() {
        long seed = System.currentTimeMillis();
        System.out.println("Seed: " + seed);

        profiles = new RandomInstrumentProfiles(new Random(seed));
        collector = new InstrumentProfileCollector();
    }

    @Ignore
    @Test
    public void testUpdates() {
        collector.addUpdateListener(instruments -> {
            while (instruments.hasNext())
                instruments.next();
        });

        // Start with one live instrument
        InstrumentProfile one = new InstrumentProfile();
        one.setSymbol("AAAAAAAAAA");
        collector.updateInstrumentProfiles(Collections.singletonList(one), null);

        // In a loop create/update a batch of profiles and remove all previous profiles
        Set<Object> generations = new HashSet<>();
        for (int i = 0; i < ITERATION_COUNT; i++) {
            collector.removeGenerations(generations);
            Object generation = new Object();
            collector.updateInstrumentProfiles(profiles.randomProfiles(PROFILE_COUNT), generation);
            generations.add(generation);

            System.gc();
            System.out.println(
                "Iteration " + i +
                ", Total " + Runtime.getRuntime().totalMemory() +
                ", Free " + Runtime.getRuntime().freeMemory());
        }
    }
}
