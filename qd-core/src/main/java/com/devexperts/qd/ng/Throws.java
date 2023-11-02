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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataRecord;

// --------------- helper throw methods -------------------
// these seemingly stupid methods provide best byte code for inlining decision by HotSpot
class Throws {
    static void throwNullPointerException() {
        throw new NullPointerException();
    }

    static void throwReleased() {
        throw new IllegalStateException("Released");
    }

    static void throwCleared() {
        throw new IllegalStateException("Cleared");
    }

    static void throwReadOnly() {
        throw new IllegalStateException("Read-only");
    }

    static void throwModeChangeNonEmpty(RecordMode thisMode, RecordMode otherMode) {
        throw new IllegalStateException("Trying to change mode of non-empty buffer from " + thisMode + " to " + otherMode);
    }

    static void throwWrongMode(RecordMode mode) {
        throw new IllegalStateException("Wrong mode " + mode);
    }

    static void throwIndexOutOfBoundsException(int index, int count) {
        throw new IndexOutOfBoundsException("index=" + index + ", count=" + count);
    }

    static void throwIndexOutOfBoundsException(int index, int count, int length) {
        throw new IndexOutOfBoundsException("index=" + index + ", count=" + count + ", length=" + length);
    }

    static void throwWrongRecord(DataRecord thisRecord, DataRecord otherRecord) {
        throw new IllegalArgumentException("Different record: " + thisRecord + " != " + otherRecord);
    }

    static void throwDifferentNumberOfFields(DataRecord newRecord, DataRecord oldRecord) {
        throw new IllegalArgumentException("Cannot replace " + oldRecord.getName() + " with " + newRecord.getName() + " because of different number of fields");
    }

    static void throwInvalidStateOrParameters(int iFromPos, int oFromPos, int iToPos, int oToPos) {
        StringBuilder builder = new StringBuilder();
        builder.append("Invalid state or input params, from: ");
        RecordCursor.formatPosition(builder, iFromPos, oFromPos);
        builder.append(", to: ");
        RecordCursor.formatPosition(builder, iToPos, oToPos);
        throw new IllegalArgumentException(builder.toString());
    }

    static void throwIndexOutOfBoundsRangeCheckException(int fromPos, int toPos, int limit) {
        throw new IndexOutOfBoundsException(
            "Invalid range: from position: " + fromPos + ", to position: " + toPos + ", limit: " + limit);
    }
}
