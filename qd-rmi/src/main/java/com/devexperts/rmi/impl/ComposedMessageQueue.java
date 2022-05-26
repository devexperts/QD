/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import java.util.ArrayDeque;

/**
 * This class is NOT thread-safe (can be used only with external synchronization).
 *
 * Deprecation candidate:
 * the class is used to provide a total size of messages in queue estimation that was removed due to inefficiency.
 * For the moment the class doesn't add any functions to the essential deque behavior,
 * so could be replaced by an appropriate {@link java.util.Deque} implementation lately.
 */
class ComposedMessageQueue {
    private final ArrayDeque<ComposedMessage> queue = new ArrayDeque<>();

    int size() {
        return queue.size();
    }

    void addFirst(ComposedMessage composedMessage) {
        queue.addFirst(composedMessage);
    }

    void addLast(ComposedMessage composedMessage) {
        queue.addLast(composedMessage);
    }

    ComposedMessage remove() {
        return queue.pollFirst();
    }
}
