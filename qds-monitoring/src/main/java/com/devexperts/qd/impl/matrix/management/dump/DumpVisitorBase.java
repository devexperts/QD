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

import java.io.PrintStream;

class DumpVisitorBase {
    final PrintStream out;
    final String filterSymbol;
    final String filterRecord;

    DumpVisitorBase(PrintStream out, String filterSymbol, String filterRecord) {
        this.out = out;
        this.filterSymbol = filterSymbol;
        this.filterRecord = filterRecord;
    }

    boolean matches(DataRecord record, String symbol) {
        return (filterSymbol == null || filterSymbol.equals(symbol)) &&
            (filterRecord == null || filterRecord.equals(record.getName()));
    }
}
