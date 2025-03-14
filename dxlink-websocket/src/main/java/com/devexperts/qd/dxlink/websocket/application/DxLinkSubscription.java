/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

final class DxLinkSubscription {
    public final String type;
    public final String symbol;
    public final String source;
    public final Long fromTime;

    private DxLinkSubscription(String type, String symbol, String source, Long fromTime) {
        this.type = type;
        this.symbol = symbol;
        this.source = source;
        this.fromTime = fromTime;
    }

    public static DxLinkSubscription createSubscription(String type, String symbol, String source) {
        return new DxLinkSubscription(type, symbol, source, null);
    }

    public static DxLinkSubscription createTimeSeriesSubscription(String type, String symbol, long fromTime) {
        return new DxLinkSubscription(type, symbol, null, fromTime);
    }
}
