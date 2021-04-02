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
package com.dxfeed.ipf.transform;

import com.devexperts.util.Timing;
import com.dxfeed.ipf.InstrumentProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Contains transform execution context.
 * Allows parallel execution of the same transform using separate instances of transform context.
 * This class is <b>NOT</b> thread-safe. Use a single instance of the context from at most one thread at a time.
 */
public final class TransformContext {
    private static final int[] EMPTY_COUNTERS = new int[0];

    private Date sysdate;
    private Timing.Day sysday;
    final List<String> customFieldsForRetainFieldsStatement = new ArrayList<>();

    private InstrumentProfile currentProfile; // Currently processed profile.
    private boolean currentProfileCopied;
    private boolean currentProfileModified;

    private int epoch; // Epoch number for modification counters.
    private int[] counters = EMPTY_COUNTERS; // [line*2] = epoch, [line*2+1] = counter

    // ===================== public instance methods =====================

    /**
     * Creates new instance of transform context.
     */
    public TransformContext() {}

    // ===================== package private instance methods =====================

    void reset() {
        sysdate = null;
        epoch++;
    }

    Date getSysdate() {
        if (sysdate == null) {
            long time = System.currentTimeMillis();
            if (sysday == null || !sysday.contains(time))
                sysday = Timing.LOCAL.getByTime(time);
            sysdate = Compiler.getDate(sysday.day_id);
        }
        return sysdate;
    }

    void setCurrentProfile(InstrumentProfile ip, boolean copied) {
        currentProfile = ip;
        currentProfileCopied = copied;
        currentProfileModified = false;
    }

    InstrumentProfile currentProfile() {
        return currentProfile;
    }

    InstrumentProfile copyProfile() {
        if (!currentProfileCopied) {
            currentProfile = new InstrumentProfile(currentProfile);
            currentProfileCopied = true;
        }
        return currentProfile;
    }

    boolean isCurrentProfileModified() {
        return currentProfileModified;
    }

    void ensureCapacity(int size) {
        int size2 = size << 1;
        if (counters.length < size2)
            counters = Arrays.copyOf(counters, size2);
    }

    void incModificationCounter(int tokenLine) {
        currentProfileModified = true;
        int idx = tokenLine << 1;
        if (idx < counters.length) {
            if (counters[idx] != epoch) {
                counters[idx] = epoch;
                counters[idx + 1] = 1;
            } else
                counters[idx + 1]++;
        }
    }

    int getModificationCounter(int tokenLine) {
        int idx = tokenLine << 1;
        return idx < counters.length && counters[idx] == epoch ? counters[idx + 1] : 0;
    }
}
