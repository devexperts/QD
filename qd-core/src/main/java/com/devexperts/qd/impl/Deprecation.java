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
package com.devexperts.qd.impl;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataIterator;

class Deprecation {

    private static final Logging log = Logging.getLogging(Deprecation.class);

    private Deprecation() {}

    private static volatile boolean legacyDataIteratorWarningShown;

    public static void legacyDataIteratorWarning(DataIterator iterator) {
        if (legacyDataIteratorWarningShown)
            return;
        legacyDataIteratorWarningSync(iterator);
    }

    private static synchronized void legacyDataIteratorWarningSync(DataIterator iterator) {
        if (legacyDataIteratorWarningShown)
            return;
        legacyDataIteratorWarningShown = true;
        log.warn("WARNING: DEPRECATED use of custom DataIterator implementation class " +
            iterator.getClass().getName() + " from " + getSource() +
            ". Do not implement DataIterator interface. It is slow. Use RecordBuffer instead.");
    }

    private static String getSource() {
        StackTraceElement[] trace = new Exception().getStackTrace();
        for (StackTraceElement ste : trace) {
            if (ste.getClassName().startsWith("com.devexperts.qd.impl."))
                continue;
            return ste.getClassName() + "." + ste.getMethodName();
        }
        return "<unknown>";
    }
}
