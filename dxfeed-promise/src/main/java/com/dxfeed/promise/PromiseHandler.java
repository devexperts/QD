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

/**
 * Handler for promised computation completion.
 */
@FunctionalInterface
public interface PromiseHandler<T> {
    /**
     * Invoked when promised computation has
     * {@link Promise#complete(Object) completed normally},
     * or {@link Promise#completeExceptionally(Throwable) exceptionally},
     * or was {@link Promise#cancel() canceled}.
     * @param promise the promise.
     */
    public void promiseDone(Promise<? extends T> promise);
}
