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
package com.devexperts.rmi.impl;

import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.util.IndexedSet;

import java.util.EnumMap;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class ChannelsManager {

    private final EnumMap<RMIChannelType, IndexedSet<Long, RMIChannelImpl>> channels =
        new EnumMap<>(RMIChannelType.class);

    synchronized void addChannel(RMIChannelImpl channel) {
        getSet(channel.getType()).add(channel);
    }

    synchronized void removeChannel(long id, RMIChannelType type) {
        getSet(type).removeKey(id);
    }

    synchronized RMIChannelImpl getChannel(Long id, RMIChannelType type) {
        return getSet(type).getByKey(id);
    }

    private IndexedSet<Long, RMIChannelImpl> getSet(RMIChannelType type) {
        IndexedSet<Long, RMIChannelImpl> result = channels.get(type);
        if (result == null) {
            result = IndexedSet.createLong(RMIChannelImpl.CHANNEL_INDEXER_BY_REQUEST_ID);
            channels.put(type, result);
        }
        return result;
    }
}
