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
package com.devexperts.qd.tools;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This thread connects to collector via {@link com.devexperts.qd.QDDistributor distributor}
 * and passes randomly-generated records to it.
 *
 * @see NetTestProducerSide
 * @see NetTestWorkingThread
 */
class NetTestProducerDistributorThread extends NetTestWorkingThread {

    private static final int RECORDS_PER_ITERATION = 1000;

    private final List<QDDistributor> distributors;

    NetTestProducerDistributorThread(int index, NetTestProducerSide side, QDEndpoint endpoint) {
        super("ProducerDistributorThread", index, side, endpoint);
        distributors = new ArrayList<QDDistributor>();
        createDistributor(endpoint.getTicker());
        createDistributor(endpoint.getStream());
        createDistributor(endpoint.getHistory());
    }

    private void createDistributor(QDCollector collector) {
        if (collector != null) {
            distributors.add(collector.distributorBuilder().build());
        }
    }

    @Override
    public void run() {
        RecordBuffer buf = new RecordBuffer();
        RandomRecordsProvider provider = new RandomRecordsProvider(new DataRecord[] {NetTestSide.RECORD},
            side.symbols.generateRandomSublist(side.config.symbolsPerEntity), RECORDS_PER_ITERATION, RECORDS_PER_ITERATION);
        while (true) {
            provider.retrieve(buf);
            int num = buf.size();
            for (QDDistributor distributor : distributors) {
                buf.rewind();
                distributor.processData(buf);
                processedRecords += num;
            }
            buf.clear();
        }
    }
}
