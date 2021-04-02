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

import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.util.IndexedSet;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class RunningTask {
    private final IndexedSet<Long, RMITaskImpl<?>> serverChannelTasks = IndexedSet.createLong(RMITaskImpl.TASK_INDEXER_BY_ID);
    private final EnumMap<RMIChannelType,  Map<Long, IndexedSet<Long, RMITaskImpl<?>>>> mapNestedTask =
            new EnumMap<>(RMIChannelType.class);

    synchronized void add(RMITaskImpl<?> task) {
        if (!task.isNestedTask()) {
            serverChannelTasks.add(task);
            return;
        }
        IndexedSet<Long, RMITaskImpl<?>> set = getMap(task.getChannel().getType()).get(task.getChannelId());
        if (set == null) {
            set = IndexedSet.createLong(RMITaskImpl.TASK_INDEXER_BY_ID);
            getMap(task.getChannel().getType()).put(task.getChannelId(), set);
        }
        set.add(task);
    }

    // for inner task
    synchronized void remove(RMITaskImpl<?> task) {
        assert task.isNestedTask();
        IndexedSet<Long, RMITaskImpl<?>> set = getMap(task.getChannel().getType()).get(task.getChannelId());
        if (set == null)
            return;
        set.remove(task);
        if (set.isEmpty())
            getMap(task.getChannel().getType()).remove(task.getChannelId());
    }

    // for top-level tasks
    synchronized void remove(RMIChannelOwner owner, long channelId) {
        IndexedSet<Long, RMITaskImpl<?>> set = getMap(owner.getChannelType()).get(channelId);
        if (set != null && !set.isEmpty()) {
            for (RMITaskImpl<?> runTask : set)
                runTask.completeExceptionally(RMIExceptionType.CHANNEL_CLOSED, null);
        }
        if (owner.getChannelType() == RMIChannelType.SERVER_CHANNEL) {
            //noinspection SuspiciousMethodCalls
            serverChannelTasks.remove(owner);
        }
    }

    synchronized RMITaskImpl<?> removeById(long requestId, long channelId, RMIChannelType type) {
        if (channelId == 0)
            return serverChannelTasks.removeKey(requestId);
        IndexedSet<Long, RMITaskImpl<?>> set = getMap(type).get(channelId);
        if (set == null || set.isEmpty())
            return null;
        return set.removeKey(requestId);
    }

    synchronized Set<RMITaskImpl<?>> removeAllById(long channelId, RMIChannelType type) {
        IndexedSet<Long, RMITaskImpl<?>> set = getMap(type).get(channelId);
        if (set == null || set.isEmpty())
            return null;
        Set<RMITaskImpl<?>> result = new HashSet<>((getMap(type).get(channelId)));
        set.clear();
        return result;
    }

    @SuppressWarnings("unchecked")
    synchronized void close() {
        //task.cancel invokes runningTask.remove(task), so we need to avoid ConcurrentModificationException
        for (RMIChannelType type : RMIChannelType.values()) {
            IndexedSet<Long, RMITaskImpl<?>>[] mapNestedTasksArray =
                getMap(type).values().toArray(new IndexedSet[getMap(type).size()]);
            for (IndexedSet<Long, RMITaskImpl<?>> nestedTasks : mapNestedTasksArray) {
                RMITaskImpl<?>[] nestedTasksArray = nestedTasks.toArray(new RMITaskImpl[nestedTasks.size()]);
                for (RMITaskImpl<?> task : nestedTasksArray)
                    task.cancel(RMIExceptionType.DISCONNECTION);
            }
        }

        RMITaskImpl<?>[] channelTasksArray = serverChannelTasks.toArray(new RMITaskImpl[serverChannelTasks.size()]);
        for (RMITaskImpl<?> task : channelTasksArray)
            task.cancel(RMIExceptionType.DISCONNECTION);
    }

    boolean hasServerChannelTask() {
        return !serverChannelTasks.isEmpty(); // volatile read
    }

    private  Map<Long, IndexedSet<Long, RMITaskImpl<?>>> getMap(RMIChannelType type) {
        return mapNestedTask.computeIfAbsent(type, k -> new HashMap<>());
    }
}
