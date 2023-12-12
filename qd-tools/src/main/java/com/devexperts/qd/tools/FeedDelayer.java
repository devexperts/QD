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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordConsumer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSource;

/**
 * Delays stream of data records by specified time duration within specified record number limit.
 * The delayed records are stored in baskets with fixed size and time duration; new basket is
 * allocated if either basket size or time duration is exceeded. Therefore, data records are
 * automatically aggregated or split in bunches depending on incoming stream rate.
 */
class FeedDelayer implements Runnable {

    private static final long BASKET_DURATION = 100; // maximum time period covered by single basket, ms
    private static final int MAX_BASKET_SIZE = 1000; // maximum number of records in single basket, target for optimal basket size
    private static final int MIN_BASKET_SIZE = 10; // minimum number of records in single basket

    private static final class Basket {
        final RecordBuffer buffer = new RecordBuffer();
        long timestamp; // timestamp of first record in buffer, valid only if (!buffer.isEmpty())

        Basket() {}
    }

    private final Logging log;
    private final long delay;
    private final int max_baskets;
    private final int basket_size;

    private final Basket[] baskets;
    private int head;
    private int tail; // baskets[tail] accumulates data until basket size or duration exceeded

    private RecordConsumer consumer;

    private long incoming_records;
    private long outgoing_records;

    FeedDelayer(long delay, long max_records, Logging log) {
        if (delay <= BASKET_DURATION)
            throw new IllegalArgumentException("delay is too short");
        this.delay = delay;
        max_baskets = (int) Math.max(2 * delay / BASKET_DURATION, max_records / MAX_BASKET_SIZE) + 1;
        basket_size = (int) Math.max(max_records / max_baskets + 1, MIN_BASKET_SIZE);
        this.log = log;

        baskets = new Basket[max_baskets + 5];
        for (int i = 0; i < baskets.length; i++)
            baskets[i] = new Basket();

        Thread thread = new Thread(this, "FeedDelayer");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /**
     * Sets data consumer for outgoing records; effective for next data bunch. Accepts 'null'.
     */
    public synchronized void setDataConsumer(RecordConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * Returns number of records added to delayer; running counter since creation.
     * Subtract outgoing records counter to get number of delayed records.
     */
    public synchronized long getIncomingRecords() {
        return incoming_records;
    }

    /**
     * Returns number of records pushed from delayer; running counter since creation.
     * Subtract from incoming records counter to get number of delayed records.
     */
    public synchronized long getOutgoingRecords() {
        return outgoing_records;
    }

    /**
     * Returns timestamp of eldest record in delay queue; returns 0 if queue is empty.
     */
    public synchronized long getEldestRecordTimestamp() {
        return !baskets[head].buffer.isEmpty() ? baskets[head].timestamp : 0;
    }

    /**
     * Processes incoming records. Will trigger pushing of outgoing records if record number limit is exceeded.
     */
    public synchronized void process(RecordSource source) {
        long time = System.currentTimeMillis();
        if (!baskets[tail].buffer.isEmpty() && time - baskets[tail].timestamp >= BASKET_DURATION)
            forwardTail();
        for (RecordCursor cursor; (cursor = source.next()) != null;) {
            if (baskets[tail].buffer.size() >= basket_size)
                forwardTail();
            if (baskets[tail].buffer.isEmpty())
                baskets[tail].timestamp = time;
            incoming_records++;
            baskets[tail].buffer.append(cursor);
        }
    }

    // Moves [tail] forward 1 step. Consumes baskets at [head] if needed.
    // NOTE: requires external SYNCHRONIZATION.
    private void forwardTail() {
        tail = (tail + 1) % baskets.length;
        while ((tail - head + baskets.length) % baskets.length > max_baskets)
            consumeHead();
    }

    // Consumes 1 basket at [head]. Moves [head] forward 1 step if needed.
    // NOTE: requires external SYNCHRONIZATION.
    private void consumeHead() {
        if (!baskets[head].buffer.isEmpty()) {
            outgoing_records += baskets[head].buffer.size();
            if (consumer != null)
                consumer.process(baskets[head].buffer);
            baskets[head].buffer.clear();
        }
        if (head != tail)
            head = (head + 1) % baskets.length;
    }

    public synchronized void run() {
        while (true) {
            try {
                long time = System.currentTimeMillis();
                while (!baskets[head].buffer.isEmpty() && time - baskets[head].timestamp >= delay)
                    consumeHead();
                if (baskets[head].buffer.isEmpty())
                    wait(delay);
                else
                    wait(Math.max(delay - (time - baskets[head].timestamp), 1)); // remaining delay; made positive
            } catch (Throwable t) {
                log.error("Error in delayer thread. Will try to continue anyway", t);
            }
        }
    }
}
