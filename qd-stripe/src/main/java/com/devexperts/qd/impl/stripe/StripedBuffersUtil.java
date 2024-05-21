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

import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSource;

class StripedBuffersUtil {
    private static final int MAX_BUFFER_SIZE = 10_000;
    private static final ThreadLocal<RecordBuffer[]> THREAD_LOCAL_BUFFER = new ThreadLocal<>();

    private static RecordBuffer[] takeBuf(StripedCollector<?> collector) {
        RecordBuffer[] buf = THREAD_LOCAL_BUFFER.get();
        if (buf == null || buf.length < collector.n) {
            buf = new RecordBuffer[collector.n];
        } else {
            // Clear thread-local variable to force new buf creation on another call from the same thread
            THREAD_LOCAL_BUFFER.set(null);
        }
        return buf;
    }

    static void releaseBuf(RecordBuffer[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            RecordBuffer buffer = buffers[i];
            if (buffer == null)
                continue;
            if (buffer.size() > MAX_BUFFER_SIZE) {
                buffers[i] = null; // too big. do not pool
            } else {
                buffer.clear();
            }
        }
        THREAD_LOCAL_BUFFER.set(buffers);
    }

    private static void ensureBuf(RecordBuffer[] buf, int i, RecordMode mode) {
        if (buf[i] == null)
            buf[i] = new RecordBuffer();
        buf[i].setMode(mode);
    }

    static RecordBuffer[] stripeData(StripedCollector<?> collector, RecordSource source) {
        RecordBuffer[] buf = takeBuf(collector);
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            int i = collector.index(cur.getCipher(), cur.getSymbol());
            ensureBuf(buf, i, source.getMode());
            buf[i].append(cur);
        }
        return buf;
    }

    static RecordBuffer[] stripeSub(StripedCollector<?> collector, RecordSource source) {
        RecordBuffer[] buf = takeBuf(collector);
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (cur.getCipher() == collector.wildcard && collector.enableWildcards) {
                for (int i = 0; i < collector.n; i++) {
                    ensureBuf(buf, i, source.getMode());
                    buf[i].append(cur);
                }
            } else {
                int i = collector.index(cur.getCipher(), cur.getSymbol());
                ensureBuf(buf, i, source.getMode());
                buf[i].append(cur);
            }
        }
        return buf;
    }
}
