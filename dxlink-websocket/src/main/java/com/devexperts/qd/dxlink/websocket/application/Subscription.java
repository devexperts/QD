/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

class Subscription {
    public final String type;
    public final String symbol;
    public final String source;
    public final Long fromTime;

    Subscription(String type, String symbol, String source, Long fromTime) {
        this.type = type;
        this.symbol = symbol;
        this.source = source;
        this.fromTime = fromTime;
    }
}
