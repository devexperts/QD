/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.ng.*;

class Buffers {
    static final ThreadLocal<RecordBuffer[]> buf = new ThreadLocal<>();

    private static RecordBuffer[] takeBuf(StripedCollector<?> collector) {
        RecordBuffer[] buf = Buffers.buf.get();
        if (buf == null || buf.length < collector.n)
            buf = new RecordBuffer[collector.n];
        else
            Buffers.buf.set(null);
        return buf;
    }

    private static void ensureBuf(RecordBuffer[] buf, int i, RecordMode mode) {
        if (buf[i] == null)
            buf[i] = new RecordBuffer();
        buf[i].setMode(mode);
    }

    static RecordBuffer[] filterData(StripedCollector<?> collector, RecordSource source) {
        RecordBuffer[] buf = takeBuf(collector);
        filterData(collector, buf, source);
        return buf;
    }

    private static void filterData(StripedCollector<?> collector, RecordBuffer[] buf, RecordSource source) {
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            int i = collector.index(cur.getCipher(), cur.getSymbol());
            ensureBuf(buf, i, source.getMode());
            buf[i].append(cur);
        }
    }

    static RecordBuffer[] filterSub(StripedCollector<?> collector, RecordSource source) {
        RecordBuffer[] buf = takeBuf(collector);
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (cur.getCipher() == collector.wildcard && collector.enableWildcards) {
                for (int i = 0; i < collector.n; i++) {
                    ensureBuf(buf, i, source.getMode());
                    buf[i].add(cur);
                }
            } else {
                int i = collector.index(cur.getCipher(), cur.getSymbol());
                ensureBuf(buf, i, source.getMode());
                buf[i].add(cur);
            }
        }
        return buf;
    }
}
