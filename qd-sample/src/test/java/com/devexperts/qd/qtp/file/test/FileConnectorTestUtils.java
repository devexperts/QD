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
package com.devexperts.qd.qtp.file.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.file.FileConnector;
import com.devexperts.qd.qtp.file.FileReaderParams;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

public class FileConnectorTestUtils {

    private static final DataRecord RECORD = new DefaultRecord(0, "Quote", false,
        new DataIntField[]{
            new CompactIntField(0, "Quote.Bid.Price"),
            new CompactIntField(1, "Quote.Ask.Price"),
            new CompactIntField(2, "Quote.Bid.Size"),
            new CompactIntField(3, "Quote.Ask.Size"),
        }, new DataObjField[0]);

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);

    private FileConnectorTestUtils() {
        //do not instantiate
    }

    public static FileConnector initFileConnector(RecordListener recordListener, BlockingQueue<String> receivedSymbols,
        String address, Date start)
    {
        // create stream
        QDStream stream = QDFactory.getDefaultFactory().streamBuilder().withScheme(SCHEME).build();
        stream.setEnableWildcards(true);
        // create and subscribe agent
        QDAgent agent = stream.agentBuilder().build();
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, SCHEME.getCodec().getWildcardCipher(), null);
        agent.setSubscription(sub);
        // set agent listener
        agent.setRecordListener(provider -> {
            // Call external listener
            if (recordListener != null)
                recordListener.recordsAvailable(provider);

            provider.retrieve(new AbstractRecordSink() {
                @Override
                public void append(RecordCursor cursor) {
                    assert cursor.getRecord() == RECORD;
                    receivedSymbols.offer(cursor.getDecodedSymbol());
                }
            });
        });
        // create and start file connector
        FileConnector connector = new FileConnector(
            MessageConnectors.applicationConnectionFactory(new DistributorAdapter.Factory(stream)), address);
        connector.setStart(start);
        connector.setSpeed(FileReaderParams.MAX_SPEED);
        connector.start();
        return connector;
    }

    public static void writeTextFile(File file, String... lines) throws IOException {
        Files.write(file.toPath(), Arrays.asList(lines));
    }
}
