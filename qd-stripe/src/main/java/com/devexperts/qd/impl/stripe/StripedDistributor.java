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
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;

class StripedDistributor<C extends QDCollector> extends AbstractDistributor {
    private final StripedCollector<C> collector;
    private final int n;
    private final QDDistributor[] dists; // Can contain null entries!
    private QDDistributor firstDist;
    private final AtomicReference<RecordProvider> asubp = new AtomicReference<>();
    private final AtomicReference<RecordProvider> rsubp = new AtomicReference<>();

    static <C extends QDCollector> QDDistributor createDistributor(
        StripedCollector<C> collector, Builder builder)
    {
        BitSet stripes = collector.getStriper().getIntersectingStripes(builder.getStripe());
        if (stripes != null && stripes.cardinality() == 1) {
            // Do not create striped distributor for single stripe
            int stripe = stripes.nextSetBit(0);
            return collector.collectors()[stripe].buildDistributor(builder);
        }
        return new StripedDistributor<>(collector, builder, stripes);
    }

    private StripedDistributor(StripedCollector<C> collector, Builder builder, BitSet stripes) {
        this.collector = collector;
        n = collector.n;
        dists = new QDDistributor[n];
        for (int i = 0; i < collector.n; i++) {
            if (stripes != null && !stripes.get(i))
                continue;
            dists[i] = collector.collectors()[i].buildDistributor(builder);
            if (firstDist == null)
                firstDist = dists[i];
        }
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
        for (int i = 0; i < n; i++) {
            if (dists[i] != null)
                dists[i].close();
        }
    }

    @Override
    public QDStats getStats() {
        return firstDist.getStats();
    }

    @Override
    public void process(RecordSource source) {
        RecordBuffer[] buf = StripedBuffersUtil.stripeData(collector, source);
        for (int i = 0; i < n; i++) {
            if (dists[i] != null && buf[i] != null && !buf[i].isEmpty()) {
                dists[i].process(buf[i]);
            }
        }
        StripedBuffersUtil.releaseBuf(buf);
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
            if (notify.hasNext()) {
                notifyListener();
            }
        }

        class Listener implements RecordListener {
            private final int i;

            Listener(int i) {
                this.i = i;
            }

            @Override
            public void recordsAvailable(RecordProvider provider) {
                if (notify.notify(i)) {
                    notifyListener();
                }
            }
        }
    }

    private class AddedSubProvider extends SubProvider {
        AddedSubProvider() {
            for (int i = 0; i < n; i++) {
                if (dists[i] != null)
                    dists[i].getAddedRecordProvider().setRecordListener(new Listener(i));
            }
        }

        @Override
        public RecordMode getMode() {
            return firstDist.getAddedRecordProvider().getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            int i;
            while ((i = notify.next()) >= 0) {
                if (dists[i] != null && dists[i].getAddedRecordProvider().retrieve(sink)) {
                    notify.notify(i);
                    return true;
                }
            }
            return false;
        }
    }

    private class RemovedSubProvider extends SubProvider {
        RemovedSubProvider() {
            for (int i = 0; i < n; i++) {
                if (dists[i] != null)
                    dists[i].getRemovedRecordProvider().setRecordListener(new Listener(i));
            }
        }

        @Override
        public RecordMode getMode() {
            return firstDist.getRemovedRecordProvider().getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            int i;
            while ((i = notify.next()) >= 0) {
                if (dists[i] != null && dists[i].getRemovedRecordProvider().retrieve(sink)) {
                    notify.notify(i);
                    return true;
                }
            }
            return false;
        }
    }
}
