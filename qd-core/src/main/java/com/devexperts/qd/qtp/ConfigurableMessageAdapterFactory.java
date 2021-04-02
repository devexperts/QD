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
package com.devexperts.qd.qtp;

/**
 * The <code>ConfigurableMessageAdapterFactory</code> creates {@link MessageAdapter.Factory}
 * with the given specification string.
 *
 * @deprecated Use {@link MessageAdapter.ConfigurableFactory}
 */
public interface ConfigurableMessageAdapterFactory extends MessageAdapter.Factory {
    /**
     * Creates {@link MessageAdapter.Factory} with the given specification string.
     * @throws NullPointerException if spec is null.
     * @throws AddressSyntaxException if spec is illegal or not supported.
     */
    public MessageAdapter.Factory createMessageAdapterFactory(String spec) throws AddressSyntaxException;
}
