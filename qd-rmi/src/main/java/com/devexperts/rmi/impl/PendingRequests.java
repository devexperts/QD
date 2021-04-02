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

import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMILoadBalancer;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.SynchronizedIndexedSet;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Holds requests that cannot be sent/processed yet. There are two reasons for this: either load balancing is in
 * progress or load balancing completed but there is no route to the selected target.
 * <p>
 * This class is used both on client- and server-side. On the server side we don't have
 * {@link RMIRequest RMI request objects} and we keep {@link ServerRequestInfo} objects.
 * <p>
 * This class is thread-safe, however iteration operations are not atomic. See Javadoc for the methods for details.
 */
@ThreadSafe
class PendingRequests {
    private final IndexedSet<Long, PendingRequest> requests = SynchronizedIndexedSet.createLong(PendingRequest::getId);

    /**
     * Adds a new pending RMI request. The request is balanced - its tentative target is already determined by
     * the {@link RMILoadBalancer load balancer}. The request is kept until {@link #dropPendingRequest(long)} or
     * {@link #removeAllBalanced()} method is called.
     * @param pendingRequest RMI request
     */
    void addPendingRequest(@Nonnull RMIRequestImpl<?> pendingRequest) {
        requests.add(PendingRequest.fromRMIRequest(pendingRequest));
    }

    /**
     * Adds an incomplete request balancing promise for an {@link RMIRequest}.
     * The promise is kept until it is completed or cancelled. After the promise completes, a given action is
     * invoked (with no locks held).
     * @param rmiRequest RMI request that is being balanced
     * @param balancePromise a balancing promise
     * @param promiseCompletionAction action to be invoked when balancing completes. The action is not invoked
     *                                if the request has been dropped with {@link #dropPendingRequest(long)}
     */
    void addBalancePromise(@Nonnull RMIRequestImpl<?> rmiRequest, @Nonnull Promise<BalanceResult> balancePromise,
        @Nonnull BiConsumer<RMIRequestImpl<?>, Promise<BalanceResult>> promiseCompletionAction)
    {
        requests.add(PendingRequest.fromBalancePromise(rmiRequest, balancePromise));
        // We should not capture the request in whenDone handler so that even if a load balancer
        // leaks its promises we don't leak the associated requests and they can be GCed when they are aborted
        long reqId = rmiRequest.getId();
        balancePromise.whenDone(result -> {
            // Any balancing promise completion requires removing the request from the 'being balanced' map
            PendingRequest pendingRequest = requests.removeKey(reqId);

            if (pendingRequest == null) // someone already dropped the request
                return;

            RMILog.logBalancingCompletion(pendingRequest.rmiRequest, balancePromise);

            promiseCompletionAction.accept(pendingRequest.rmiRequest, balancePromise);
        });
    }

    /**
     * Adds an incomplete request balancing promise for an {@link ServerRequestInfo}.
     * The promise is kept until it is completed or cancelled. After the promise completes, a given action is
     * invoked (with no locks held).
     * @param requestInfo request that is being balanced
     * @param balancePromise a balancing promise
     * @param promiseCompletionAction action to be invoked when balancing completes. The action is not invoked
     *                                if the request has been dropped with {@link #dropPendingRequest(long)}
     */
    void addBalancePromise(@Nonnull ServerRequestInfo requestInfo, @Nonnull Promise<BalanceResult> balancePromise,
        @Nonnull BiConsumer<ServerRequestInfo, Promise<BalanceResult>> promiseCompletionAction)
    {
        requests.add(PendingRequest.fromBalancePromise(requestInfo, balancePromise));
        long reqId = requestInfo.reqId;
        balancePromise.whenDone(result -> {
            // Any balancing promise completion requires removing the request from the 'being balanced' map
            PendingRequest pendingRequest = requests.removeKey(reqId);

            if (pendingRequest == null) // someone already dropped the request
                return;

            RMILog.logBalancingCompletion(requestInfo, balancePromise);

            promiseCompletionAction.accept(pendingRequest.serverRequestInfo, balancePromise);
        });
    }

    /**
     * Drops a request with the given id. If there is a pending balance request promise, it is cancelled.
     * This method is to support manual/timeout request cancellation.
     * @param requestId id of the request per {@link RMIRequestImpl#getId()} or {@link ServerRequestInfo#reqId}.
     * @return true if the request has been present, false otherwise
     */
    boolean dropPendingRequest(long requestId) {
        PendingRequest pendingRequest = requests.removeKey(requestId);
        if (pendingRequest != null && pendingRequest.balancePromise != null)
            pendingRequest.balancePromise.cancel();
        return pendingRequest != null;
    }

    /**
     * @return number of pending requests and requests being balanced
     */
    int size() {
        return requests.size();
    }

    /**
     * Passes each pending balanced {@link RMIRequestImpl RMI request} to the given consumer. Skips requests
     * that are being balanced.
     * Note that this operation is not atomic: if requests are {@link #addPendingRequest(RMIRequestImpl) added}
     * concurrently they might be missed by the consumer. Apply external locking and prevent concurrent request
     * addition if you need atomicity.
     * @param consumer consumer
     */
    void forEachRMIRequest(@Nonnull Consumer<RMIRequestImpl<?>> consumer) {
        for (Iterator<PendingRequest> it = requests.concurrentIterator(); it.hasNext();) {
            PendingRequest pendingRequest = it.next();
            if (pendingRequest.rmiRequest != null)
                consumer.accept(pendingRequest.rmiRequest);
        }
    }

    /**
     * Passes each load balance promise to the given consumer. Skips pending requests that are already balanced.
     * Note that this operation is not atomic: if promises are
     * {@link #addBalancePromise(RMIRequestImpl, Promise, BiConsumer)} added} concurrently they might be missed
     * by the consumer. Apply external locking and prevent concurrent promises addition if you need atomicity.
     * @param consumer consumer
     */
    private void forEachBalancePromise(@Nonnull Consumer<Promise<BalanceResult>> consumer) {
        for (Iterator<PendingRequest> it = requests.concurrentIterator(); it.hasNext();) {
            PendingRequest pendingRequest = it.next();
            if (pendingRequest.balancePromise != null)
                consumer.accept(pendingRequest.balancePromise);
        }
    }

    /**
     * Removes all pending {@link RMIRequestImpl requests} that are already balanced. Note that this operation is not
     * atomic: if requests are {@link #addPendingRequest(RMIRequestImpl) added} concurrently they might
     * remain after this method completes. Apply external synchronization and prevent concurrent request addition
     * if you need atomicity.
     * @return list of all balanced pending requests or empty list
     */
    List<RMIRequestImpl<?>> removeAllBalanced() {
        List<RMIRequestImpl<?>> result = new ArrayList<>();
        for (Iterator<PendingRequest> it = requests.concurrentIterator(); it.hasNext();) {
            PendingRequest pendingRequest = it.next();
            if (pendingRequest.balancePromise == null) {
                it.remove();
                result.add(pendingRequest.rmiRequest);
            }
        }
        return result;
    }

    /**
     * Clears all pending requests cancelling balance promises
     */
    void clear() {
        forEachBalancePromise(Promise::cancel);
        requests.clear();
    }


    private static class PendingRequest {
        final Promise<BalanceResult> balancePromise;
        final RMIRequestImpl<?> rmiRequest;
        final ServerRequestInfo serverRequestInfo;

        PendingRequest(Promise<BalanceResult> balancePromise, RMIRequestImpl<?> rmiRequest,
            ServerRequestInfo serverRequestInfo)
        {
            assert rmiRequest != null || serverRequestInfo != null;
            this.balancePromise = balancePromise;
            this.rmiRequest = rmiRequest;
            this.serverRequestInfo = serverRequestInfo;
        }

        static PendingRequest fromBalancePromise(RMIRequestImpl<?> request, Promise<BalanceResult> balancePromise) {
            return new PendingRequest(balancePromise, request, null);
        }

        static PendingRequest fromBalancePromise(ServerRequestInfo request, Promise<BalanceResult> balancePromise) {
            return new PendingRequest(balancePromise, null, request);
        }

        static PendingRequest fromRMIRequest(RMIRequestImpl<?> request) {
            return new PendingRequest(null, request, null);
        }

        long getId() {
            return rmiRequest != null ? rmiRequest.getId() : serverRequestInfo.reqId;
        }
    }
}
