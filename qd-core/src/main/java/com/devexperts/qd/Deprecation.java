/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

class Deprecation {
    static void warning(Class<?> clazz) {
        String source = "<unknown>";
        StackTraceElement[] trace = new Exception().getStackTrace();
        for (StackTraceElement ste : trace) {
            if (ste.getClassName().equals("com.devexperts.qd.Deprecation") ||
                ste.getClassName().equals("com.devexperts.qd.DataBuffer") ||
                ste.getClassName().equals("com.devexperts.qd.SubscriptionBuffer"))
            {
                continue;
            }
            source = ste.getClassName() + "." + ste.getMethodName();
            break;
        }
        QDLog.log.warn("WARNING: Loading DEPRECATED class " + clazz.getName() + " from " + source);
    }
}
