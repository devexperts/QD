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

import java.io.IOException;

public interface SubscriptionDumpVisitor {
    public static final byte[] MAGIC = { 'Q', 'D', 'S', 'D' };
    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;

    public void visitCollector(int id, String keyProperties, String contract, boolean hasTime) throws IOException;

    public void visitRecord(DataRecord record) throws IOException;

    public void visitSymbol(int cipher, String symbol) throws IOException;

    public void visitAgentNew(int aid, String keyProperties) throws IOException;

    public void visitAgentAgain(int aid) throws IOException;

    public void visitTime(int t0, int t1) throws IOException;

    public void visitEndOfChain() throws IOException;

    public void visitEndOfCollector() throws IOException;
}
