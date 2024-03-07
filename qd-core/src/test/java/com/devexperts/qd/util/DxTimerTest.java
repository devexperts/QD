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
package com.devexperts.qd.util;

import org.junit.Assume;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class DxTimerTest {

    @Test(expected = NullPointerException.class)
    public void testInvalidSchedule() {
        DxTimer.getInstance().runOnce(null, 1000);
    }

    @Test
    public void testRunOnce() throws InterruptedException {
        CountDownLatch invoke = new CountDownLatch(1);
        DxTimer.getInstance().runOnce(invoke::countDown, 0);
        assertTrue(invoke.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testRunDaily() throws InterruptedException {
        CountDownLatch invoke = new CountDownLatch(1);
        Instant invokeTime = Instant.now().plusSeconds(1);
        DxTimer.Cancellable cancellable = DxTimer.getInstance().runDaily(invoke::countDown,
            LocalTime.from(ZonedDateTime.ofInstant(invokeTime, ZoneId.systemDefault())));
        try {
            Assume.assumeTrue(Instant.now().isBefore(invokeTime));
            assertTrue(invoke.await(10, TimeUnit.SECONDS));
        } finally {
            cancellable.cancel();
        }
    }
}
