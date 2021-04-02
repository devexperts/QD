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
package com.devexperts.qd.impl.matrix;

import java.util.Arrays;

/**
 * Tracks and number instances of processData for History contract.
 */
class ProcessVersionTracker {
    private static final int HAS_WAITERS_FLAG = History.PROCESS_VERSION_MASK + 1;

    /**
     * A number that numbers an instance of processing.
     * It is masked with {@link History#PROCESS_VERSION_MASK}.
     */
    private int processVersion;

    private int[] inProcessVersions = new int[4]; // == version | HAS_WAITERS_FLAG

    // starts next processing, returns its version
    synchronized int next() {
        processVersion = (processVersion + 1) & History.PROCESS_VERSION_MASK;
        if (processVersion == 0)
            processVersion = 1;
        addImpl(processVersion);
        return processVersion;
    }

    private void addImpl(int v) {
        int n = inProcessVersions.length;
        for (int i = 0; i < n; i++) {
            if (inProcessVersions[i] == 0) {
                inProcessVersions[i] = v;
                return;
            }
        }
        inProcessVersions = Arrays.copyOf(inProcessVersions, 2 * n);
        inProcessVersions[n] = v;
    }

    synchronized void done(int v) {
        int i = findIndex(v);
        if (i < 0)
            throw new IllegalStateException("Process version " + v + " should have been found");
        if ((inProcessVersions[i] & HAS_WAITERS_FLAG) != 0) {
            /*
               Wakeup all waiters here.
               Waiting is a rare corner-case, so we don't care about efficiency of wake ups.
               Basically, this code path shall never be invoked in a normal operation.
            */
            notifyAll();
        }
        inProcessVersions[i] = 0;
    }

    synchronized void waitWhileInProcess(int v) {
        boolean wasInterrupted = false;
        for (int i; (i = findIndex(v)) >= 0;) {
            try {
                inProcessVersions[i] |= HAS_WAITERS_FLAG;
                wait();
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
        }
        if (wasInterrupted)
            Thread.currentThread().interrupt();
    }

    // returns index or -1 if not found
    private int findIndex(int v) {
        if (v == 0)
            throw new IllegalArgumentException();
        for (int i = 0; i < inProcessVersions.length; i++) {
            if ((inProcessVersions[i] & History.PROCESS_VERSION_MASK) == v)
                return i;
        }
        return -1;
    }
}
