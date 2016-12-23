/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.impl.stripe;

import java.util.concurrent.atomic.AtomicReference;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.stats.QDStats;

class StripedDistributor<C extends QDCollector> extends AbstractDistributor {
    private final StripedCollector<C> collector;
    private final int n;
    private final QDDistributor[] dists;
    private final AtomicReference<RecordProvider> asubp = new AtomicReference<>();
    private final AtomicReference<RecordProvider> rsubp = new AtomicReference<>();

    StripedDistributor(StripedCollector<C> collector, Builder builder) {
        this.collector = collector;
        n = collector.n;
        dists = new QDDistributor[n];
        for (int i = 0; i < n; i++)
            dists[i] = collector.collectors()[i].buildDistributor(builder);
    }

    @Override
    public RecordProvider getAddedRecordProvider() {
        RecordProvider result;
        do {
            result = asubp.get();
            if (result != null)
                break;
            result = new AddedSubProvider();
        } while (!asubp.compareAndSet(null, result));
        return result;
    }

    @Override
    public RecordProvider getRemovedRecordProvider() {
        RecordProvider result;
        do {
            result = rsubp.get();
            if (result != null)
                break;
            result = new RemovedSubProvider();
        } while (!rsubp.compareAndSet(null, result));
        return result;
    }

    @Override
    public void close() {
        for (int i = 0; i < n; i++)
            dists[i].close();
    }

    @Override
    public QDStats getStats() {
        return dists[0].getStats();
    }

    @Override
    public void process(RecordSource source) {
        RecordBuffer[] buf = Buffers.filterData(collector, source);
        for (int i = 0; i < n; i++)
            if (buf[i] != null && !buf[i].isEmpty()) {
                dists[i].processData(buf[i]);
                buf[i].clear();
            }
        Buffers.buf.set(buf);
    }

    abstract class SubProvider extends AbstractRecordProvider {
        final StripedNotification notify = new StripedNotification(n);
        volatile RecordListener listener;

        void notifyListener() {
            RecordListener listener = this.listener;
            if (listener != null)
                listener.recordsAvailable(this);
        }

        @Override
        public void setRecordListener(RecordListener listener) {
            this.listener = listener;
            if (notify.hasNext())
                notifyListener();
        }

        class Listener implements RecordListener {
            private final int i;

            Listener(int i) {
                this.i = i;
            }

            @Override
            public void recordsAvailable(RecordProvider provider) {
                if (notify.notify(i))
                    notifyListener();
            }
        }
    }

    private class AddedSubProvider extends SubProvider {
        AddedSubProvider() {
            for (int i = 0; i < n; i++)
                dists[i].getAddedRecordProvider().setRecordListener(new Listener(i));
        }

        @Override
        public RecordMode getMode() {
            return dists[0].getAddedRecordProvider().getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            int i;
            while ((i = notify.next()) >= 0)
                if (dists[i].getAddedRecordProvider().retrieve(sink)) {
                    notify.notify(i);
                    return true;
                }
            return false;
        }

    }

    private class RemovedSubProvider extends SubProvider {
        RemovedSubProvider() {
            for (int i = 0; i < n; i++)
                dists[i].getRemovedRecordProvider().setRecordListener(new Listener(i));
        }

        @Override
        public RecordMode getMode() {
            return dists[0].getRemovedRecordProvider().getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            int i;
            while ((i = notify.next()) >= 0)
                if (dists[i].getRemovedRecordProvider().retrieve(sink)) {
                    notify.notify(i);
                    return true;
                }
            return false;
        }

    }
}
