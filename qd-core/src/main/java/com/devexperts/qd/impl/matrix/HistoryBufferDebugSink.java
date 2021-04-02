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

import com.devexperts.qd.DataRecord;

/**
 * Special interface for debugging of {@link HistoryBuffer} state in {@code DebugDumpReader}.
 */
public interface HistoryBufferDebugSink {
    public void visitHistoryBuffer(DataRecord record, int cipher, String symbol, long timeTotalSub, HistoryBuffer hb);
    public void visitDone(DataRecord record, int cipher, String symbol, int examineMethodResult);
}
