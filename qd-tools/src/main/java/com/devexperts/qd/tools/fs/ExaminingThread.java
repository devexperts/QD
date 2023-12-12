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
package com.devexperts.qd.tools.fs;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;

/**
 * This thread runs inside FilteredStream. It periodically examines underlying ticker's
 * data to update its agent subscription and retrieves new records from ticker's agent
 * to stream's distributor.
 *
 * @see FilteredStream
 */
class ExaminingThread extends Thread {

    private static final Logging log = Logging.getLogging(ExaminingThread.class);

    private final QDTicker ticker;
    private final QDDistributor distributor;
    private final long dataFrequency;

    ExaminingThread(QDTicker ticker, QDDistributor distributor, long dataFrequency) {
        super("FilteredStreamExaminingThread");
        setDaemon(true);
        this.ticker = ticker;
        this.distributor = distributor;
        this.dataFrequency = dataFrequency;
    }

    @Override
    public void run() {
        try {
            QDAgent agent = ticker.agentBuilder().build();
            RecordBuffer subscriptionBuffer = new RecordBuffer(RecordMode.SUBSCRIPTION);
            RecordBuffer recordBuffer = new RecordBuffer();
            while (!interrupted()) {
                // Step 1. Revise new data to update subscription
                if (ticker.examineData(subscriptionBuffer)) {
                    // Some data was not examined
                    log.warn("WARNING: Not all data was examined in ticker inside filtered stream.");
                }
                agent.addSubscription(subscriptionBuffer);
                subscriptionBuffer.clear();

                // Step 2. Retrieve available data from ticker's agent
                // and pass it into stream's distributor.
                agent.retrieveData(recordBuffer);
                distributor.processData(recordBuffer);
                recordBuffer.clear();

                sleep(dataFrequency);
            }
        } catch (InterruptedException ie) {
            // do nothing
        } catch (Throwable t) {
            log.error("Exception in " + getName(), t);
        }
    }
}
