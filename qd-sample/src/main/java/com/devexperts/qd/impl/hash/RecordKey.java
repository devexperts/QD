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
package com.devexperts.qd.impl.hash;

import com.devexperts.qd.DataRecord;

/**
 * The <code>RecordKey</code> represents a key to individual record.
 * It uses data record and cipher-symbol pair for identification.
 */
class RecordKey {

    private DataRecord record;
    private int cipher;
    private String symbol;

    RecordKey(DataRecord record, int cipher, String symbol) {
        this.record = record;
        this.cipher = cipher;
        this.symbol = symbol;
    }

    final DataRecord getRecord() {
        return record;
    }

    final int getCipher() {
        return cipher;
    }

    final String getSymbol() {
        return symbol;
    }

    void set(DataRecord record, int cipher, String symbol) {
        this.record = record;
        this.cipher = cipher;
        this.symbol = symbol;
    }

    public final int hashCode() {
        return record.hashCode() ^ (cipher != 0 ? cipher : symbol.hashCode());
    }

    public final boolean equals(Object obj) {
        RecordKey other = (RecordKey) obj;
        return record == other.record &&
            cipher == other.cipher &&
            (cipher != 0 || symbol.equals(other.symbol));
    }
}
