/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.qd.DataConsumer;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.tools.RandomRecordsProvider;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

public class SampleGeneratorThread extends Thread {
    private static final long DELAY_MILLIS = TimePeriod.valueOf(
        SystemProperties.getProperty(SampleGeneratorThread.class, "delay", "0.5s")).getTime();

    private final DataScheme scheme;
    private final QDTicker ticker;
    private final QDStream stream;
    private final QDHistory history;

    public SampleGeneratorThread(QDEndpoint endpoint) {
        super("Generator");
        this.scheme = endpoint.getScheme();
        this.ticker = endpoint.getTicker();
        this.stream = endpoint.getStream();
        this.history = endpoint.getHistory();
    }

    @Override
    public void run() {
        DataConsumer[] consumers = {
            ticker.distributorBuilder().build(),
            stream.distributorBuilder().build(),
            history.distributorBuilder().build()
        };
        DataProvider[] providers = {
            new RandomRecordsProvider(scheme.getRecord(0), SampleClient.STRINGS, 10),
            new RandomRecordsProvider(scheme.getRecord(1), SampleClient.STRINGS, 3),
            new RandomRecordsProvider(scheme.getRecord(2), new String[] {SampleClient.STRINGS[0]}, 3)
        };
        RecordBuffer buffer = new RecordBuffer();
        try {
            while (!Thread.interrupted()) {
                for (DataProvider provider : providers)
                    provider.retrieveData(buffer);
                for (DataConsumer consumer : consumers) {
                    buffer.rewind();
                    consumer.processData(buffer);
                }
                buffer.clear();
                Thread.sleep(DELAY_MILLIS);
            }
        } catch (InterruptedException e) { /*just return*/ }
    }
}
