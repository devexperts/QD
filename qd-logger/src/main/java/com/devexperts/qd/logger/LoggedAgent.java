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
package com.devexperts.qd.logger;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.SubscriptionConsumer;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.LegacyAdapter;

public class LoggedAgent extends LoggedRecordProvider implements QDAgent {
    private final QDAgent delegate;
    private final DataScheme scheme;
    private volatile LoggedRecordProvider snapshotProvider;

    public LoggedAgent(Logger log, QDAgent delegate, DataScheme scheme) {
        super(log, delegate);
        this.delegate = delegate;
        this.scheme = scheme;
    }

    @Override
    public RecordProvider getSnapshotProvider() {
        LoggedRecordProvider snapshotProvider = this.snapshotProvider;
        if (snapshotProvider != null)
            return snapshotProvider;
        synchronized (this) {
            snapshotProvider = this.snapshotProvider;
            if (snapshotProvider != null)
                return snapshotProvider;
            return this.snapshotProvider =
                new LoggedRecordProvider(log.child("snapshot"), delegate.getSnapshotProvider());
        }
    }

    @Override
    public SubscriptionConsumer getAddingSubscriptionConsumer() {
        log.debug("USING DEPRECATED getAddingSubscriptionConsumer");
        return delegate.getAddingSubscriptionConsumer();
    }

    @Override
    public SubscriptionConsumer getRemovingSubscriptionConsumer() {
        log.debug("USING DEPRECATED getRemovingSubscriptionConsumer");
        return delegate.getRemovingSubscriptionConsumer();
    }

    @Override
    public void addSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        addSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public void addSubscription(RecordSource source) {
        log.debug("addSubscription(" + logSubscription(source) + ")");
        delegate.addSubscription(source);
    }

    @Override
    public int addSubscriptionPart(RecordSource source, int notify) {
        long savePosition = source.getPosition();
        int result = delegate.addSubscriptionPart(source, notify);
        log.debug("addSubscriptionPart(" + notify + ", " +
            logSubscription(source.newSource(savePosition, source.getPosition())) + ") = " + result);
        return result;
    }

    @Override
    public void removeSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        removeSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public void removeSubscription(RecordSource source) {
        log.debug("removeSubscription(" + logSubscription(source) + ")");
        delegate.removeSubscription(source);
    }

    @Override
    public int removeSubscriptionPart(RecordSource source, int notify) {
        log.debug("removeSubscriptionPart(...)");
        return delegate.removeSubscriptionPart(source, notify);
    }

    @Override
    public void setSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        setSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public void setSubscription(RecordSource source) {
        log.debug("setSubscription(" + logSubscription(source) + ")");
        delegate.setSubscription(source);
    }

    @Override
    public int setSubscriptionPart(RecordSource source, int notify) {
        long savePosition = source.getPosition();
        int result = delegate.setSubscriptionPart(source, notify);
        log.debug("setSubscriptionPart(" + notify + ", " +
            logSubscription(source.newSource(savePosition, source.getPosition())) + ") = " + result);
        return result;
    }

    private String logSubscription(RecordSource source) {
        StringBuilder sb = new StringBuilder();
        long savePosition = source.getPosition();
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (sb.length() > 0)
                sb.append(", ");
            Logger.appendRecord(sb, cur.getRecord(), cur.getCipher(), cur.getSymbol());
            if (cur.hasTime() && cur.getInt(0) != 0)
                Logger.appendTime(sb, cur.getRecord(), cur.getInt(0));
            if (cur.getEventFlags() != 0)
                sb.append("[").append(EventFlag.formatEventFlags(cur.getEventFlags())).append("]");
        }
        source.setPosition(savePosition);
        return sb.toString();
    }

    @Override
    public void close() {
        log.debug("close()");
        delegate.close();
    }

    @Override
    public int closePart(int notify) {
        int result = delegate.closePart(notify);
        log.debug("closePart(" + notify + ") = " + result);
        return result;
    }

    @Override
    public void closeAndExamineDataBySubscription(RecordSink sink) {
        log.debug("closeAndExamineDataBySubscription(...)");
        delegate.closeAndExamineDataBySubscription(sink);
    }

    @Override
    public QDStats getStats() {
        return delegate.getStats();
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return delegate.isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(SubscriptionVisitor visitor) {
        return delegate.examineSubscription(visitor);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return delegate.examineSubscription(sink);
    }

    @Override
    public int getSubscriptionSize() {
        return delegate.getSubscriptionSize();
    }

    @Override
    public void setStreamOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow) {
        setBufferOverflowStrategy(max_buffer_size, drop_oldest, log_overflow);
    }

    @Override
    public void setBufferOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow) {
        log.debug("[DEPRECATED] setBufferOverflowStrategy(" + max_buffer_size + ", " + drop_oldest + ", " + log_overflow + ")");
        delegate.setBufferOverflowStrategy(max_buffer_size, drop_oldest, log_overflow);
    }

    @Override
    public void setMaxBufferSize(int maxBufferSize) {
        log.debug("setMaxBufferSize(" + maxBufferSize + ")");
        delegate.setMaxBufferSize(maxBufferSize);
    }

    @Override
    public void setBufferOverflowStrategy(BufferOverflowStrategy bufferOverflowStrategy) {
        log.debug("setBufferOverflowStrategy(" + bufferOverflowStrategy + ")");
        delegate.setBufferOverflowStrategy(bufferOverflowStrategy);
    }
}
