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

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

class PostMessageQueue extends MessageConsumerAdapter {
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<Subscriber>();
    // preQueue accumulates data before any Subscriber is created, it became null after first Subscriber was created
    private Queue<Item> preQueue = new ConcurrentLinkedQueue<Item>();

    @Override
    protected void processData(DataIterator iterator, MessageType message) {
        // Note: we know that iterator is always RecordSource here
        RecordSource source = (RecordSource) iterator;
        RecordBuffer data = new RecordBuffer(source.getMode());
        data.process(source);
        Item item = new Item(message, data);
        synchronized (this) {
            // preQueue accumulates data before any Subscriber is created, it became null after first Subscriber was created
            if (preQueue != null) {
                preQueue.add(item);
                return;
            }
        }
        for (Subscriber subscriber : subscribers) {
            subscriber.add(item);
        }
    }

    synchronized Subscriber newSubscriber(SubscriptionFilter filter) {
        Subscriber subscriber = new Subscriber(filter);
        subscribers.add(subscriber);
        // preQueue accumulates data before any Subscriber is created, it became null after first Subscriber was created
        if (preQueue != null) {
            while (!preQueue.isEmpty())
                subscriber.add(preQueue.poll());
            preQueue = null;
            notifyAll();
        }
        return subscriber;
    }

    public void waitDone() throws InterruptedException {
        // preQueue accumulates data before any Subscriber is created, it became null after first Subscriber was created
        synchronized (this) {
            while (preQueue != null && !preQueue.isEmpty())
                wait();
        }
        for (Subscriber subscriber : subscribers) {
            subscriber.waitEmpty();
        }
    }

    static class FilteredProvider extends AbstractRecordProvider {
        private final RecordSource source;
        private final SubscriptionFilter filter;

        FilteredProvider(RecordBuffer data, SubscriptionFilter filter) {
            this.source = data.newSource();
            this.filter = filter;
        }

        @Override
        public RecordMode getMode() {
            return RecordMode.DATA;
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            while (sink.hasCapacity()) {
                RecordCursor cur = source.next();
                if (cur == null)
                    return false;
                if (filter == null || filter.acceptRecord(cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                    sink.append(cur);
            }
            return true;
        }

        @Override
        public void setRecordListener(RecordListener listener) {
            // nothing to do
        }
    }

    static class Subscriber implements MessageProvider {
        private final SubscriptionFilter filter;
        private final Queue<Item> queue = new ConcurrentLinkedQueue<Item>();
        private volatile MessageListener listener;

        Subscriber(SubscriptionFilter filter) {
            this.filter = filter;
        }

        public boolean retrieveMessages(MessageVisitor visitor) {
            // We peek first
            Item item = queue.peek();
            if (item == null)
                return false;
            visitor.visitData(new FilteredProvider(item.data, filter), item.message);
            // Remove only when data was visited.
            // Presumably, visited data was actually serialized to socket and we can close it now
            queue.remove(); // so we can remove it from the queue
            signalWhenEmpty();
            return !queue.isEmpty();
        }

        public void setMessageListener(MessageListener listener) {
            this.listener = listener;
            if (listener != null && !queue.isEmpty())
                listener.messagesAvailable(this);
        }

        void add(Item item) {
            queue.add(item);
            MessageListener listener = this.listener; // Atomic read.
            if (listener != null)
                listener.messagesAvailable(this);
        }

        synchronized void waitEmpty() throws InterruptedException {
            while (!queue.isEmpty())
                wait();
        }

        private synchronized void signalWhenEmpty() {
            if (queue.isEmpty())
                notifyAll();
        }
    }

    private static class Item {
        final MessageType message;
        final RecordBuffer data;

        Item(MessageType message, RecordBuffer data) {
            this.message = message;
            this.data = data;
        }
    }
}
