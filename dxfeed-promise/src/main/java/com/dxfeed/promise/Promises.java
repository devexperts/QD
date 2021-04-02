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
package com.dxfeed.promise;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility methods to manipulate {@link Promise promises}.
 */
public final class Promises {
    private Promises() {
    } // do not create

    /**
     * Returns a new promise that {@link Promise#isDone() completes} when all promises from the given collection
     * complete normally or exceptionally.
     * The results of the given promises are not reflected in the returned promise, but may be
     * obtained by inspecting them individually. If no promises are provided, returns a promise completed
     * with the value null.
     * When the resulting promise completes for any reason (is {@link Promise#cancel() canceled}, for example)
     * then all of the promises from the given collection are canceled.
     *
     * @param promises a collection of promises.
     * @throws NullPointerException if promises is null or any individual promise there is null.
     * @return a new promise that {@link Promise#isDone() completes} when all promises from the given collection complete.
     */
    @SuppressWarnings("unchecked")
    public static Promise<Void> allOf(Collection<? extends Promise<?>> promises) {
        return (Promise<Void>) aggregationImpl(promises.toArray(new Promise[promises.size()]), false);
    }

    /**
     * Returns a new promise that {@link Promise#isDone() completes} when all promises from the given array
     * complete normally or exceptionally.
     * The results of the given promises are not reflected in the returned promise, but may be
     * obtained by inspecting them individually. If no promises are provided, returns a promise completed
     * with the value null.
     * When the resulting promise completes for any reason (is {@link Promise#cancel() canceled}, for example)
     * then all of the promises from the given array are canceled.
     *
     * @param promises an array of promises.
     * @throws NullPointerException if promises is null or any individual promise there is null.
     * @return a new promise that {@link Promise#isDone() completes} when all promises from the given array complete.
     */
    @SuppressWarnings("unchecked")
    public static Promise<Void> allOf(Promise<?>... promises) {
        return aggregationImpl(promises.clone(), false);
    }

    /**
     * Returns a new promise that {@link Promise#isDone() completes} when any promise from the given collection
     * complete normally or exceptionally.
     * If any of the given promises {@link Promise#hasException() complete exceptionally}, then the returned promise
     * also does so with the same {@link Promise#getException() exception} and all non-completed promises in the
     * list are {@link Promise#cancel() canceled}.
     * If any of the given promises {@link Promise#hasResult() complete with result}, then the returned promise
     * also does so with the same {@link Promise#getResult() result} and all non-completed promises in the
     * list are {@link Promise#cancel() canceled}.
     * When the resulting promise completes for any reason (is {@link Promise#cancel() canceled}, for example)
     * then all of the promises from the given collection are canceled.
     *
     * @param promises a collection of promises.
     * @throws NullPointerException if promises is null or any individual promise there is null.
     * @return a new promise that {@link Promise#isDone() completes} when any promise from the given collection completes.
     */
    @SuppressWarnings("unchecked")
    public static <T> Promise<T> anyOf(Collection<? extends Promise<T>> promises) {
        return (Promise<T>) aggregationImpl(promises.toArray(new Promise[promises.size()]), true);
    }

    /**
     * Returns a new promise that {@link Promise#isDone() completes} when any promise from the given array
     * complete normally or exceptionally.
     * If any of the given promises {@link Promise#hasException() complete exceptionally}, then the returned promise
     * also does so with the same {@link Promise#getException() exception} and all non-completed promises in the
     * list are {@link Promise#cancel() canceled}.
     * If any of the given promises {@link Promise#hasResult() complete with result}, then the returned promise
     * also does so with the same {@link Promise#getResult() result} and all non-completed promises in the
     * list are {@link Promise#cancel() canceled}.
     * When the resulting promise completes for any reason (is {@link Promise#cancel() canceled}, for example)
     * then all of the promises from the given array are canceled.
     *
     * @param promises a collection of promises.
     * @throws NullPointerException if promises is null or any individual promise there is null.
     * @return a new promise that {@link Promise#isDone() completes} when any promise from the given array completes.
     */
    @SuppressWarnings("unchecked")
    public static <T> Promise<T> anyOf(Promise<T>... promises) {
        return aggregationImpl(promises.clone(), true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Promise aggregationImpl(final Promise[] promises, final boolean any) {
        final Promise result = new Promise();
        if (promises.length == 0) {
            result.complete(null);
            return result;
        }
        final AtomicInteger count = new AtomicInteger();
        PromiseHandler<Object> handler = promise -> {
            if (count.incrementAndGet() == (any ? 1 : promises.length)) {
                if (result.isDone())
                    return; // optimization to avoid long recursion of promiseDone invocations
                if (any) {
                    // "anyOf" reflects the result
                    if (promise.hasException())
                        result.completeExceptionally(promise.getException());
                    else
                        result.complete(promise.getResult());
                } else {
                    // "allOf" always completes with null
                    result.complete(null);
                }
            }
        };
        for (Promise promise : promises)
            promise.whenDone(handler);
        result.whenDone(promise -> {
            for (Promise p : promises)
                p.cancelImpl("aggregated result promise had completed");
        });
        return result;
    }

}
