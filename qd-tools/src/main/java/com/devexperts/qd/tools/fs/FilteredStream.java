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
package com.devexperts.qd.tools.fs;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

import java.util.function.Consumer;

/**
 * A special implementation of QDStream. It filters incoming data with specified
 * time interval. It stores both stream and ticker collectors inside.
 * It stores all data in ticker the first, and then retrieves it from the ticker
 * with specified frequency and passes through the stream.
 * <br> The scheme below describes the structure of FilteredStream:
 * <pre><tt>
 *                                        FilteredStream
 *             +---------------------------------------------------------------------+
 *             |                                                                     |
 *             |                             --&gt; Subscription --&gt;                    |
 *             |                           +----------------------------------+   composite
 *             |                           |                                  |  distributors
 *       underlying  +-----------+-(D)-----+    +-----------+                [*]&lt;--------------&gt;
 *         stream    |           |              |           |  &lt;-- Data &lt;--   |      |
 *         agents    |  STREAM   | &lt;-- Data &lt;-- |  TICKER   |-(D)-------------+      |
 *     &lt;--------(A)-&gt;|           |-(D)------(A)-|           |                        |
 *             |     +-----------+          /\  +-----------+                        |
 *             |                            ||           ||                          |
 *             |               Subscription ||           || All                      |
 *             |               for new data ||           || data                     |
 *             |                            ||           \/                          |
 *             |                           +---------------+                         |
 *             |                           |ExaminingThread|                         |
 *             |                           +---------------+                         |
 *             |                                                                     |
 *             +---------------------------------------------------------------------+
 * </tt></pre>
 * FilteredStream supports wildcard subscription as a usual stream.
 */
public class FilteredStream extends AbstractCollector implements QDStream {
    private final QDTicker ticker;
    private final QDStream stream;

    /**
     * Creates new instance of FilteredStream.
     *
     * @param factory QDFactory to use to create underlying QDTicker and QDStream.
     * @param builder builder to build from
     * @param dataFrequency maximum data outgoing frequency.
     */
    public FilteredStream(QDFactory factory, Builder<?> builder, long dataFrequency) {
        super(builder);
        this.stream = factory.streamBuilder().copyFrom(builder).build();
        this.ticker = factory.tickerBuilder().copyFrom(builder).withStoreEverything(true).build();

        new ExaminingThread(ticker, stream.distributorBuilder().build(), dataFrequency).start();
    }

    @Override
    public void setEnableWildcards(boolean enableWildcards) {
        stream.setEnableWildcards(enableWildcards);
    }

    @Override
    public boolean getEnableWildcards() {
        return stream.getEnableWildcards();
    }

    @Override
    public QDStats getStats() {
        return stream.getStats();
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return stream.buildAgent(builder);
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        QDDistributor streamDistributor = stream.buildDistributor(builder);
        QDDistributor tickerDistributor = ticker.buildDistributor(builder);
        return new CompositeDistributor(streamDistributor, tickerDistributor);
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        ticker.setErrorHandler(errorHandler);
        stream.setErrorHandler(errorHandler);
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return ticker.isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return stream.examineSubscription(sink);
    }

    @Override
    public int getSubscriptionSize() {
        return stream.getSubscriptionSize();
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return ticker.getSymbol(chars, offset, length);
    }

    @Override
    public void close() {
        ticker.close();
        stream.close();
    }

    @Override
    public void setDroppedLog(Consumer<String> droppedLog) {
        super.setDroppedLog(droppedLog);
        if (ticker instanceof AbstractCollector) {
            ((AbstractCollector) ticker).setDroppedLog(droppedLog);
        }
        if (stream instanceof AbstractCollector) {
            ((AbstractCollector) stream).setDroppedLog(droppedLog);
        }
    }
}
