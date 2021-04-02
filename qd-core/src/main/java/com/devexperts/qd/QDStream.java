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
package com.devexperts.qd;

/**
 * The <code>QDStream</code> represents a stream-view of the data.
 * In the stream-view, all data events are important and are delivered
 * to the consumers.
 */
public interface QDStream extends QDCollector {
    /**
     * Sets "wildcards enabled" mode (disabled by default).
     * <ul>
     * <li>When wildcards are enabled agents can subscribe to {@link SymbolCodec#getWildcardCipher() wildcard symbol} and
     * receive all data that is being distributed. Wildcard subscription is also propagated upwards to distributors.
     * <li>When wildcards are disabled (by default) attempts to subscribe to  {@link SymbolCodec#getWildcardCipher() wildcard symbol}
     * are ignored and are not propagated upwards to distributors.
     * </ul>
     */
    public void setEnableWildcards(boolean enableWildcards);

    /**
     * Returns status of "wildcards enabled" mode.
     * @see #setEnableWildcards(boolean)
     */
    public boolean getEnableWildcards();
}
