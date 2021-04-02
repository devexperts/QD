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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;

import java.io.PrintStream;

public class DumpUtil {
    static void printTime(PrintStream out, DataRecord r, RecordCursor cursor) {
        out.print("\t");
        out.print(cursor.getTime());
        out.print("\t{");
        out.print(r.getIntField(0).getString(cursor));
        out.print(" ");
        out.print(r.getIntField(1).getString(cursor));
        out.print("}");
    }

    static String timeString(DataRecord r, long time) {
        return time + " {" +
            r.getIntField(0).toString((int) (time >> 32)) + " " +
            r.getIntField(1).toString((int) time) + "}";
    }
}
