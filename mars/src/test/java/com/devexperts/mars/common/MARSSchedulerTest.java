/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.mars.common;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import junit.framework.TestCase;

public class MARSSchedulerTest extends TestCase {
    public void testCancel() {
        // schedule a few command over random times
        int n = 10;
        Random rnd = new Random(1);
        Runnable[] cmd = new Runnable[n];
        for (int i = 0; i < n; i++) {
            cmd[i] = newCommand();
            MARSScheduler.schedule(cmd[i], rnd.nextInt(100) + 1, TimeUnit.SECONDS);
        }
        // cancel them
        for (int i = 0; i < n; i++) {
            MARSScheduler.cancel(cmd[i]);
        }
    }

    @Nonnull
    private Runnable newCommand() {
        return new Runnable() {
            @Override
            public void run() {

            }
        };
    }

}
