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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.util.TimeFormat;

import java.io.PrintWriter;
import java.io.StringWriter;

class FatalError {
    private final long time;
    private final Throwable error;

    FatalError(Throwable error) {
        this.time = System.currentTimeMillis();
        this.error = error;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        sw.append(TimeFormat.DEFAULT.format(time));
        sw.append(": ");
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
