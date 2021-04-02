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
package com.devexperts.qd.impl;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.SubscriptionConsumer;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.util.LegacyAdapter;

public abstract class AbstractAgent extends AbstractRecordProvider implements QDAgent {

    // =========================== private instance fields ===========================

    @SuppressWarnings("rawtypes")
    private final AttachmentStrategy attachmentStrategy; // non-null when this agent keeps object attachments
    private final boolean useHistorySnapshot; // Used by History & Stream, true when History Snapshot is supported by this agent
    private final boolean hasEventTimeSequence;

    private final SubscriptionConsumer addingConsumer = new SubscriptionConsumer() {
        @Override
        public void processSubscription(SubscriptionIterator iterator) {
            AbstractAgent.this.addSubscription(iterator);
        }
    };

    private final SubscriptionConsumer removingConsumer = new SubscriptionConsumer() {
        @Override
        public void processSubscription(SubscriptionIterator iterator) {
            AbstractAgent.this.removeSubscription(iterator);
        }
    };

    // =========================== constructor and instance methods ===========================

    protected AbstractAgent(QDContract contract, Builder builder) {
        attachmentStrategy = builder.getAttachmentStrategy();
        useHistorySnapshot = contract != QDContract.TICKER && builder.useHistorySnapshot();
        hasEventTimeSequence = builder.hasEventTimeSequence();
    }

    public final boolean hasAttachmentStrategy() {
        return attachmentStrategy != null;
    }

    @SuppressWarnings("unchecked")
    public final Object updateAttachment(Object oldAttachment, RecordCursor cursor, boolean remove) {
        return attachmentStrategy.updateAttachment(oldAttachment, cursor, remove);
    }

    public boolean useHistorySnapshot() {
        return useHistorySnapshot;
    }

    @Override
    public RecordProvider getSnapshotProvider() {
        return RecordProvider.VOID;
    }

    @Override
    public final void setStreamOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow) {
        setBufferOverflowStrategy(max_buffer_size, drop_oldest, log_overflow);
    }

    @Override
    public final void setBufferOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow) {
        setMaxBufferSize(max_buffer_size);
        setBufferOverflowStrategy(drop_oldest ? BufferOverflowStrategy.DROP_OLDEST : BufferOverflowStrategy.DROP_NEWEST);
    }

    @Override
    public void setMaxBufferSize(int maxBufferSize) {
        if (maxBufferSize <= 0)
            throw new IllegalArgumentException();
    }

    @Override
    public void setBufferOverflowStrategy(BufferOverflowStrategy bufferOverflowStrategy) {
        if (bufferOverflowStrategy == null)
            throw new NullPointerException();
    }

    @Override
    public final SubscriptionConsumer getAddingSubscriptionConsumer() {
        return addingConsumer;
    }

    @Override
    public final SubscriptionConsumer getRemovingSubscriptionConsumer() {
        return removingConsumer;
    }

    @Override
    public final void addSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        addSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public int addSubscriptionPart(RecordSource source, int notify) {
        // no support for cooperation on lock-bound tasks by default in this abstract implementation
        addSubscription(source);
        return 0;
    }

    @Override
    public final void removeSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        removeSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public int removeSubscriptionPart(RecordSource source, int notify) {
        // no support for cooperation on lock-bound tasks by default in this abstract implementation
        removeSubscription(source);
        return 0;
    }

    @Override
    public final void setSubscription(SubscriptionIterator iterator) {
        RecordSource source = LegacyAdapter.of(iterator);
        setSubscription(source);
        LegacyAdapter.release(iterator, source);
    }

    @Override
    public int setSubscriptionPart(RecordSource source, int notify) {
        // no support for cooperation on lock-bound tasks by default in this abstract implementation
        setSubscription(source);
        return 0;
    }

    @Override
    public int closePart(int notify) {
        // no support for cooperation on lock-bound tasks by default in this abstract implementation
        close();
        return 0;
    }

    @Override
    public RecordMode getMode() {
        RecordMode mode = RecordMode.MARKED_DATA;
        if (attachmentStrategy != null)
            mode = mode.withAttachment();
        if (useHistorySnapshot)
            mode = mode.withEventFlags();
        if (hasEventTimeSequence)
            mode = mode.withEventTimeSequence();
        return mode;
    }

    @Override
    public abstract void setRecordListener(RecordListener listener); // must be implemented

    @Override
    public final boolean examineSubscription(SubscriptionVisitor visitor) {
        return examineSubscription(LegacyAdapter.of(visitor));
    }
}
