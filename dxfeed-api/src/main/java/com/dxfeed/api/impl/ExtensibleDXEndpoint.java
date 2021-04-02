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
package com.dxfeed.api.impl;

import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.TypedKey;
import com.devexperts.util.TypedMap;
import com.dxfeed.api.DXEndpoint;

public abstract class ExtensibleDXEndpoint extends DXEndpoint {
    private TypedMap extensions;

    public abstract Object getLock();

    public <T> T getExtension(TypedKey<T> key) {
        synchronized (getLock()) {
            return extensions == null ? null : extensions.get(key);
        }
    }

    public <T> void setExtension(TypedKey<T> key, T value) {
        synchronized (getLock()) {
            if (extensions == null)
                extensions = new TypedMap();
            extensions.set(key, value);
        }
    }

    public abstract ExecutorProvider.Reference getExecutorReference();

    public abstract void setConnectedAddressSync(String address);
}
