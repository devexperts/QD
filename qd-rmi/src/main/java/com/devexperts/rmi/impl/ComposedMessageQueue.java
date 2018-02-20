/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import java.util.ArrayList;

/**
 * This class is NOT thread-safe (can be used only with external synchronization) with an exception
 * of {@link #getTotalMessagesSize()} which is safe for use without one.
 */
class ComposedMessageQueue {
    /**
     * Total size (in bytes) of all composed messages in the queue. It is being checked
     * from the message composing thread without synchronization for load-balancing.
     */
    private volatile long totalSize;

    // ArrayList here is actually used as a queue (the queue length is very small and thus garbage matters here much more than performance).
    // This might be replaced by ArrayDeque after migration to java 1.6.
    private final ArrayList<ComposedMessage> queue = new ArrayList<>();

    int size() {
        return queue.size();
    }

    /**
     * Returns total size (in bytes) of all composed messages in the queue.
     * This method is thread-safe.
     */
    long getTotalMessagesSize() {
        return totalSize;
    }

    void addFirst(ComposedMessage composedMessage) {
        totalSize += composedMessage.totalChunksLength();
        queue.add(0, composedMessage);
    }

    void addLast(ComposedMessage composedMessage) {
        totalSize += composedMessage.totalChunksLength();
        queue.add(composedMessage);
    }

    ComposedMessage remove() {
        if (queue.isEmpty())
            return null;
        ComposedMessage result = queue.remove(0);
        totalSize -= result.totalChunksLength();
        return result;
    }

}
