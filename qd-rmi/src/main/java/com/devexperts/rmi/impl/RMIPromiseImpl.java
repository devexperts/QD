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
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

public class RMIPromiseImpl<T> extends Promise<T> {
    private final RMIRequest<T> request;

    RMIPromiseImpl(RMIRequest<T> request) {
        this.request = request;
    }

    public RMIRequest<T> getRequest() {
        return request;
    }

    @Override
    protected void handleDone(PromiseHandler<? super T> handler) {
        request.cancelOrAbort();
        super.handleDone(handler);
    }
}
