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
package com.devexperts.qd.impl.matrix.management;

public class CollectorCounters {
    public void countLock(CollectorOperation op, long waitNanos, long lockNanos) {
        // actual impl overrides
    }

    public void countDistributionAndClear(RecordCounters incoming, RecordCounters outgoing, int spins) {
        // actual impl overrides
    }

    public void countRetrieval(int records) {
        // actual impl overrides
    }

    public void countDropped(int records) {
        // actual impl overrides
    }

    public CollectorCounters snapshot() {
        return this; // actual impl overrides
    }

    public CollectorCounters since(CollectorCounters snapshot) {
        return this; // actual impl overrides
    }

    public String textReport() {
        return ""; // actual impl overrides
    }

}
