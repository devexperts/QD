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
package com.devexperts.qd.impl.matrix.management.impl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.StreamOutput;
import com.devexperts.qd.*;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.SubscriptionDumpVisitor;

public class SubscriptionDumpImpl implements SubscriptionDumpVisitor {
    public static void makeDump(final String file, final DataScheme scheme, final List<Collector> list) {
        Exec.EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    makeDumpImpl(file, scheme, list);
                } catch (Throwable t) {
                    QDLog.log.error("Failed to dump subscription to '" + file + "' file.", t);
                }
            }
        });
    }

    private static void makeDumpImpl(String file, DataScheme scheme, List<Collector> list) throws IOException {
        QDLog.log.info("Dumping subscription for " + list.size() + " collector(s) to '" + file + "' file...");
        try (StreamOutput out = new StreamOutput(new FileOutputStream(file), 100000)) {
            SubscriptionDumpImpl visitor = new SubscriptionDumpImpl(scheme, out);
            visitor.writeHeader();
            for (Collector collector : list)
                collector.dumpSubscription(visitor);
            visitor.writeEndOfFile();
        }
        QDLog.log.info("Subscription dump completed");
    }

    // ------------------------------ INSTANCE ------------------------------

    private final DataScheme scheme;
    private final SymbolCodec codec;
    private final SymbolCodec.Writer symbolWriter;
    private final BufferedOutput out;
    private final boolean[] seenRecords;

    private SubscriptionDumpImpl(DataScheme scheme, BufferedOutput out) {
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.symbolWriter = codec.createWriter();
        this.out = out;
        this.seenRecords = new boolean[scheme.getRecordCount()];
    }

    private void writeHeader() throws IOException {
        out.write(MAGIC);
        out.writeCompactInt(VERSION_2);
        out.writeCompactLong(System.currentTimeMillis());
        out.writeUTFString(QDFactory.getVersion());
        out.writeUTFString(scheme.getClass().getName());
        out.writeUTFString(codec.getClass().getName());
    }

    private void writeEndOfFile() throws IOException {
        out.writeCompactInt(-1);
    }

    @Override
    public void visitCollector(int id, String keyProperties, String contract, boolean hasTime) throws IOException {
        out.writeCompactInt(id == -1 ? 0 : id);
        out.writeUTFString(keyProperties);
        out.writeUTFString(contract);
        out.writeBoolean(hasTime);
    }

    @Override
    public void visitRecord(DataRecord record) throws IOException {
        int rid = record.getId();
        if (!seenRecords[rid]) {
            out.writeCompactInt(-rid - 2);
            out.writeUTFString(record.getName());
            seenRecords[rid] = true;
        } else
            out.writeCompactInt(rid);
    }

    @Override
    public void visitSymbol(int cipher, String symbol) throws IOException {
        symbolWriter.writeSymbol(out, cipher, symbol, 0);
    }

    @Override
    public void visitAgentNew(int aid, String keyProperties) throws IOException {
        assert aid >= 0;
        out.writeCompactInt(-aid - 2);
        out.writeUTFString(keyProperties);
    }

    @Override
    public void visitAgentAgain(int aid) throws IOException {
        assert aid >= 0;
        out.writeCompactInt(aid);
    }

    @Override
    public void visitTime(int t0, int t1) throws IOException {
        out.writeCompactInt(t0);
        out.writeCompactInt(t1);
    }

    @Override
    public void visitEndOfChain() throws IOException {
        out.writeCompactInt(-1);
    }

    @Override
    public void visitEndOfCollector() throws IOException {
        out.writeCompactInt(-1);
    }
}
