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
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class SentRequests {

    private final IndexedSet<Long, RMIRequestImpl<?>> channelRequests = IndexedSet.createLong((IndexerFunction.LongKey<RMIRequestImpl<?>>) RMIRequestImpl::getId);
    private final Map<Long, IndexedSet<Long, RMIRequestImpl<?>>> clientNestedRequests = new HashMap<>();
    private final Map<Long, IndexedSet<Long, RMIRequestImpl<?>>> serverNestedRequests = new HashMap<>();

    synchronized void addSentRequest(RMIRequestImpl<?> request) {
        if (!request.isNestedRequest()) {
            channelRequests.add(request);
            return;
        }
        Map<Long, IndexedSet<Long, RMIRequestImpl<?>>> map = request.getKind().hasClient() ? clientNestedRequests : serverNestedRequests;
        IndexedSet<Long, RMIRequestImpl<?>> set = map.get(request.getChannelId());
        if (set == null) {
            set = IndexedSet.createLong((IndexerFunction.LongKey<RMIRequestImpl<?>>) RMIRequestImpl::getId);
            map.put(request.getChannelId(), set);
        }
        set.add(request);
    }

    //if channelId = 0 => top-level request
    RMIRequestImpl<?> removeSentRequest(long channelId, long curRequestId, RMIMessageKind kind) {
        RMIRequestImpl<?> headRequest;
        RMIRequestImpl<?> result;
        IndexedSet<Long, RMIRequestImpl<?>> set;
        // Limit synchronized range to honor lock hierarchy with requestLock
        synchronized (this) {
            if (channelId != 0) {
                IndexedSet<Long, RMIRequestImpl<?>> requests = kind.hasClient() ? clientNestedRequests.get(channelId)
                    : serverNestedRequests.get(channelId);
                result = requests != null ? requests.removeKey(curRequestId) : null;
                return result;
            }
            headRequest = channelRequests.removeKey(curRequestId);
            if (headRequest == null)
                return null;
            set = clientNestedRequests.remove(((RMIChannelImpl) headRequest.getChannel()).getChannelId());
        }
        if (set != null  && !set.isEmpty()) {
            for (RMIRequestImpl<?> request : set)
                request.setFailedState(RMIExceptionType.CHANNEL_CLOSED, null);
        }
        return headRequest;
    }

    synchronized RMIRequestImpl<?>[] getSentRequests(RMIRequestImpl<?>[] requests) {
        return channelRequests.toArray(requests);
    }

    void close() {
        List<RMIRequestImpl<?>> allRequests = new ArrayList<>();
        // Limit synchronized range to honor lock hierarchy with requestLock
        synchronized (this) {
            for (IndexedSet<Long, RMIRequestImpl<?>> requests : clientNestedRequests.values())
                allRequests.addAll(requests);
            clientNestedRequests.clear();
            for (IndexedSet<Long, RMIRequestImpl<?>> requests : serverNestedRequests.values())
                allRequests.addAll(requests);
            serverNestedRequests.clear();
            allRequests.addAll(channelRequests);
            channelRequests.clear();
        }
        for (RMIRequestImpl<?> request : allRequests) {
            request.setFailedState(RMIExceptionType.DISCONNECTION, null);
        }
    }
}
