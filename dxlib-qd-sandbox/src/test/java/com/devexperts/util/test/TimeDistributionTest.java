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
package com.devexperts.util.test;

import com.devexperts.util.TimeDistribution;
import org.junit.Test;

import java.util.Random;

import static com.devexperts.util.TimeDistribution.Precision.HIGH;
import static com.devexperts.util.TimeDistribution.Precision.LOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TimeDistributionTest {

    @Test
    public void testEmptyDistribution() {
        assertEquals("0", new TimeDistribution(HIGH).toString());
        assertEquals("0", new TimeDistribution(LOW).toString());
    }

    @Test
    public void testOneStrings() {
        assertEquals("1, avg=0ns; min=0ns [0ns - 0ns - 0ns] max=1ns", getOne(0));
        assertEquals("1, avg=1ns; min=1ns [1ns - 1ns - 1ns] max=2ns", getOne(1));
        assertEquals("1, avg=2ns; min=2ns [2ns - 2ns - 2ns] max=3ns", getOne(2));
        assertEquals("1, avg=10ns; min=10ns [10ns - 10ns - 10ns] max=11ns", getOne(10));
        assertEquals("1, avg=100ns; min=100ns [100ns - 100ns - 100ns] max=101ns", getOne(100));
        assertEquals("1, avg=1us; min=1us [1us - 1us - 1.01us] max=1.01us", getOne(1000));
        assertEquals("1, avg=1.5us; min=1.49us [1.49us - 1.5us - 1.5us] max=1.5us", getOne(1500));
        assertEquals("1, avg=2.01us; min=2us [2us - 2.01us - 2.01us] max=2.02us", getOne(2000));

        assertEquals("1, avg=10us; min=9.98us [10us - 10us - 10.1us] max=10.1us", getOne(10000));
        assertEquals("1, avg=20.1us; min=20us [20us - 20.1us - 20.2us] max=20.2us", getOne(20000));
        assertEquals("1, avg=99.8us; min=99.3us [99.4us - 99.8us - 100us] max=100us", getOne(100000));

        assertEquals("1, avg=100ms; min=99.6ms [99.7ms - 100ms - 101ms] max=101ms", getOne(100000000L));
        assertEquals("1, avg=200ms; min=199ms [199ms - 200ms - 201ms] max=201ms", getOne(200000000L));
        assertEquals("1, avg=801ms; min=797ms [798ms - 801ms - 804ms] max=805ms", getOne(800000000L));
        assertEquals("1, avg=902ms; min=898ms [898ms - 902ms - 905ms] max=906ms", getOne(900000000L));

        assertEquals("1, avg=1s; min=998ms [999ms - 1s - 1.01s] max=1.01s", getOne(1000000000L));
        assertEquals("1, avg=2s; min=2s [2s - 2s - 2.01s] max=2.01s", getOne(2000000000L));
        assertEquals("1, avg=3s; min=2.99s [2.99s - 3s - 3.02s] max=3.02s", getOne(3000000000L));
        assertEquals("1, avg=4.01s; min=3.99s [4s - 4.01s - 4.02s] max=4.03s", getOne(4000000000L));
        assertEquals("1, avg=5s; min=4.97s [4.97s - 5s - 5.03s] max=5.03s", getOne(5000000000L));
        assertEquals("1, avg=5.47s; min=5.44s [5.44s - 5.47s - 5.5s] max=5.5s", getOne(5500000000L));

        assertEquals("1, avg=59.9s; min=59.6s [59.6s - 59.9s - 1min] max=1min", getOne(60000000000L));
        assertEquals("1, avg=9.95min; min=9.88min [9.89min - 9.95min - 10min] max=10min", getOne(600000000000L));
        assertEquals("1, avg=20min; min=10min [20min - 20min - 20min] max=inf", getOne(1200000000000L));
    }

    @Test
    public void testTwoStrings() {
        assertEquals("2, avg=2us; min=1us [1us - 2.98us - 3us] max=3.01us", getTwo(1000, 3000));
        assertEquals("2, avg=502ms; min=999us [1ms - 998ms - 1s] max=1.01s", getTwo(1000000L, 1000000000L));
    }

    @Test
    public void testLowPrecision() {
        checkPrecision(TimeDistribution.Precision.LOW, 0.07);
    }

    @Test
    public void testHighPrecision() {
        checkPrecision(TimeDistribution.Precision.HIGH, 0.01);
    }

    // test that maximal error does not exceed a documented value
    private void checkPrecision(TimeDistribution.Precision p, double maxError) {
        Random rnd = new Random(20131218);
        for (int i = 0; i < 10000; i++) {
            long nanos = randomNanos(rnd);
            TimeDistribution td = new TimeDistribution(p);
            td.addMeasurement(nanos);
            double error = nanos == 0 ? Math.abs(td.getAverageNanos()) :
                (double) Math.abs(nanos - td.getAverageNanos()) / nanos;
            if (error > maxError) {
                fail("Failed precision for " + nanos + " (" + TimeDistribution.formatNanos(nanos) +
                    " with error of " + error);
            }
        }
    }

    private long randomNanos(Random rnd) {
        int bits = rnd.nextInt(60);
        long nanos = rnd.nextInt(1 << Math.min(bits, 30));
        if (bits > 30)
            nanos = (nanos << (bits - 30)) | rnd.nextInt(1 << (bits - 30));
        return nanos;
    }

    private String getOne(long ns) {
        TimeDistribution td = new TimeDistribution(HIGH);
        td.addMeasurement(ns);
        return td.toString();
    }

    private String getTwo(long ns1, long ns2) {
        TimeDistribution td = new TimeDistribution(HIGH);
        td.addMeasurement(ns1);
        td.addMeasurement(ns2);
        return td.toString();
    }
}
