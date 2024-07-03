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
package com.devexperts.qd;

import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.stats.QDStatsContainer;

import java.util.concurrent.Executor;

/**
 * The <code>QDAgent</code> represents an individual data consumer in the {@link QDCollector}.
 * It is responsible for tracking state of the consumer in the collector, including its
 * total subscription and accumulated data, and to provide access point for that consumer.
 */
public interface QDAgent extends RecordProvider, SubscriptionContainer, QDStatsContainer {

    /**
     * Returns record provider that is used to retrieve only snapshot from Ticker and History.
     * This provider does not provide anything for Stream.
     * Subsequent calls to this method on the same agent return the same instance.
     */
    public RecordProvider getSnapshotProvider();

    /**
     * Returns subscription consumer that can be used to add subscription.
     * Adding subscription via returned consumer has the same effect
     * as adding it directly via {@link #addSubscription} method.
     * @deprecated Use {@link #addSubscription} method.
     */
    public SubscriptionConsumer getAddingSubscriptionConsumer();

    /**
     * Returns subscription consumer that can be used to remove subscription.
     * Removing subscription via returned consumer has the same effect
     * as removing it directly via {@link #removeSubscription} method.
     * @deprecated Use {@link #removeSubscription} method.
     */
    public SubscriptionConsumer getRemovingSubscriptionConsumer();

    /**
     * Adds specified subscription to this agent's data interest.
     * This is a shortcut to adding subscription via corresponding
     * subscription consumer.
     * @deprecated Use {@link #addSubscription(RecordSource)}
     */
    public void addSubscription(SubscriptionIterator iterator);

    /**
     * Adds specified subscription to this agent's data interest.
     * This is a shortcut for a loop of
     * <pre><tt>
     *     int res = 0;
     *     do {
     *         res = {@link #addSubscriptionPart(RecordSource,int) addSubscriptionPart}(source, res);
     *     } while (res != 0);
     * </tt></pre>
     *
     * All agents support <em>mixed subscription</em>. Subscription can be removed with this method
     * when marked with {@link EventFlag#REMOVE_SYMBOL REMOVE_SYMBOL} flag.
     */
    public void addSubscription(RecordSource source);

    /**
     * Adds <em>part</em> of a specified subscription to this agent's data interest.
     * This method facilitates a cooperative mechanism to execute tasks that spends most of their time under
     * the global lock of the collector.
     *
     * <p> All agents support <em>mixed subscription</em>. Subscription can be removed with this method
     *    when marked with {@link EventFlag#REMOVE_SYMBOL REMOVE_SYMBOL} flag.
     *
     * @param source the subscription.
     * @param notify result of previous method invocation or 0 on the first invocation.
     * @return 0 when there is no more work to be done.
     * @see QDCollector#executeLockBoundTask(Executor, Runnable)
     */
    public int addSubscriptionPart(RecordSource source, int notify);

    /**
     * Removes specified subscription from this agent's data interest.
     * This is a shortcut to removing subscription via corresponding
     * subscription consumer.
     * @deprecated Use {@link #removeSubscription(RecordSource)}
     */
    public void removeSubscription(SubscriptionIterator iterator);

    /**
     * Removes specified subscription from this agent's data interest.
     * This is a shortcut for a loop of
     * <pre><tt>
     *     int res = 0;
     *     do {
     *         res = {@link #removeSubscriptionPart(RecordSource,int) removeSubscriptionPart}(source, res);
     *     } while (res != 0);
     * </tt></pre>
     */
    public void removeSubscription(RecordSource source);

    /**
     * Removes <em>part</em> of specified subscription from this agent's data interest.
     * This method facilitates a cooperative mechanism to execute tasks that spends most of their time under
     * the global lock of the collector.
     *
     * @param source the subscription.
     * @param notify result of previous method invocation or 0 on the first invocation.
     * @return 0 when there is no more work to be done.
     * @see QDCollector#executeLockBoundTask(Executor, Runnable)
     */
    public int removeSubscriptionPart(RecordSource source, int notify);

    /**
     * Sets specified subscription to be this agent's data interest.
     * This method keeps state of survived subscription; in other words,
     * it uses <i>addAll+retainAll</i> operations in contrast to
     * <i>removeAll+addAll</i> operations.
     * @deprecated Use {@link #setSubscription(RecordSource)}
     */
    public void setSubscription(SubscriptionIterator iterator);

    /**
     * Sets specified subscription to be this agent's data interest.
     * This is a shortcut for a loop of
     * <pre><tt>
     *     int res = 0;
     *     do {
     *         res = {@link #setSubscriptionPart(RecordSource,int) setSubscriptionPart}(source, res);
     *     } while (res != 0);
     * </tt></pre>
     */
    public void setSubscription(RecordSource source);

    /**
     * Sets <em>part</em> of specified subscription to be this agent's data interest.
     * This method facilitates a cooperative mechanism to execute tasks that spends most of their time under
     * the global lock of the collector.
     *
     * @param source the subscription.
     * @param notify result of previous method invocation or 0 on the first invocation.
     * @return 0 when there is no more work to be done.
     * @see QDCollector#executeLockBoundTask(Executor, Runnable)
     */
    public int setSubscriptionPart(RecordSource source, int notify);

    /**
     * Closes this agent and releases allocated resources in its {@link QDCollector}.
     * Closed agent can not be activated again and shall not be used anymore.
     * Attempt to use closed agent will result in no action - subscription
     * will be ignored and data retrieval will retrieve no data.
     *
     * <p> This is a shortcut for a loop of
     * <pre><tt>
     *     int res = 0;
     *     do {
     *         res = {@link #closePart(int) closePart}(res);
     *     } while (res != 0);
     * </tt></pre>
     */
    public void close();

    /**
     * Closes <em>part</em> of this agent and releases allocated resources in its {@link QDCollector}.
     * This method facilitates a cooperative mechanism to execute tasks that spends most of their time under
     * the global lock of the collector.
     *
     * <p>Agents that started closing can not be activated again and shall not be used anymore.
     * Attempt to use agent that had started closing result in no action - subscription
     * will be ignored and data retrieval will retrieve no data.
     *
     * @param notify result of previous method invocation or 0 on the first invocation.
     * @return 0 when there is no more work to be done.
     * @see QDCollector#executeLockBoundTask(Executor, Runnable)
     */
    public int closePart(int notify);

    /**
     * Closes this agent and with {@link #close()} and atomically examines all data that is
     * available for this agent in the collector storage.
     * @see #close()
     */
    public void closeAndExamineDataBySubscription(RecordSink sink);

    /**
     * Sets new strategy for handling of stream and history buffer overflow events.
     * New strategy settings apply only for newly distributed data.
     * Their effect on already accumulated data is not defined.
     * Has no effect on ticker agents.
     *
     * @param max_buffer_size how many records to keep in buffer, default is <b>1000000</b>
     * @param drop_oldest which record shall be dropped - oldest (true) or newest (false), default is <b>true</b>
     * @param log_overflow whether overflow event shall be logged or not, default is <b>true</b>.
     *                     <b>This value is ignored. Dropped events on overflow are always logged.</b>
     * @throws IllegalArgumentException if max_buffer_size is not a positive integer
     * @deprecated Use {@link #setMaxBufferSize(int)} and {@link #setBufferOverflowStrategy(BufferOverflowStrategy)}
     */
    public void setStreamOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow);

    /**
     * Sets new strategy for handling of stream and history buffer overflow events.
     * New strategy settings apply only for newly distributed data.
     * Their effect on already accumulated data is not defined.
     * Has no effect on ticker agents.
     *
     * @param max_buffer_size how many records to keep in buffer, default is <b>1000000</b>
     * @param drop_oldest which record shall be dropped - oldest (true) or newest (false), default is <b>true</b>
     * @param log_overflow whether overflow event shall be logged or not, default is <b>true</b>.
     *                     <b>This value is ignored. Dropped events on overflow are always logged.</b>
     * @throws IllegalArgumentException if max_buffer_size is not a positive integer
     * @deprecated Use {@link #setMaxBufferSize(int)} and {@link #setBufferOverflowStrategy(BufferOverflowStrategy)}
     */
    public void setBufferOverflowStrategy(int max_buffer_size, boolean drop_oldest, boolean log_overflow);

    /**
     * Sets max buffers size for stream and history agents.
     * The effect on already accumulated data is not defined.
     * It has no effect on ticker agents.
     *
     * @param maxBufferSize how many records to keep in buffer, default is <b>1000000</b>
     * @throws IllegalArgumentException if maxBufferSize is not a positive integer
     * @see #setBufferOverflowStrategy(BufferOverflowStrategy)
     */
    public void setMaxBufferSize(int maxBufferSize);

    /**
     * Sets new strategy for handling of stream and history buffer overflow events.
     * New strategy settings apply only for newly distributed data.
     * The effect on already accumulated data is not defined.
     * It has no effect on ticker agents.
     *
     * @param bufferOverflowStrategy the strategy, default is {@link BufferOverflowStrategy#DROP_OLDEST}.
     * @throws NullPointerException if bufferOverflowStrategy is null.
     * @see #setMaxBufferSize(int)
     */
    public void setBufferOverflowStrategy(BufferOverflowStrategy bufferOverflowStrategy);

    /**
     * Strategy for handling of stream and history buffer overflow events.
     * @see #setBufferOverflowStrategy(BufferOverflowStrategy)
     */
    public enum BufferOverflowStrategy {
        /**
         * Drop oldest records when buffer overflows.
         */
        DROP_OLDEST,

        /**
         * Drop newest records when buffer overflows.
         */
        DROP_NEWEST,

        /**
         * Block data processing when buffer overflows until buffer space is available.
         * The {@link QDDistributor#processData(DataIterator)} method will block until buffer
         * space in this agent becomes available. This block can be
         * {@link Thread#interrupt() interrupted}, in which case data processing aborts leaving
         * the interrupt flag set.
         */
        BLOCK
    }

    /**
     * Builder for collector agents.
     * Instances of this class are immutable, withXXX method creates new instances if needed.
     */
    public interface Builder {
        public QDFilter getFilter();
        public QDFilter getStripe();
        public String getKeyProperties();
        public AttachmentStrategy<?> getAttachmentStrategy();
        public boolean useHistorySnapshot();
        public boolean hasEventTimeSequence();
        public boolean hasVoidRecordListener();

        public Builder withFilter(QDFilter filter);
        public Builder withStripe(QDFilter stripeFilter);
        public Builder withKeyProperties(String keyProperties);
        public Builder withAttachmentStrategy(AttachmentStrategy<?> attachmentStrategy);
        public Builder withHistorySnapshot(boolean useHistorySnapshot);
        public Builder withOptSet(ProtocolOption.Set optSet);
        public Builder withEventTimeSequence(boolean hasEventTimeSequence);
        public Builder withVoidRecordListener(boolean hasVoidRecordListener);

        public QDAgent build();
    }

    // WARNING: This is an EXPERIMENTAL interface. DO NOT IMPLEMENT
    public interface AttachmentStrategy<T> {
        public T updateAttachment(T oldAttachment, RecordCursor cursor, boolean remove);
    }
}
