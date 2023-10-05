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
package com.devexperts.qd.dxlink.websocket.transport;

import com.devexperts.qd.qtp.MessageConnectorState;

enum SocketState {
    NEW(MessageConnectorState.DISCONNECTED),
    STARTED(MessageConnectorState.CONNECTING),
    CONNECTING(MessageConnectorState.CONNECTING),
    CONNECTED(MessageConnectorState.CONNECTED),
    STOPPED(MessageConnectorState.DISCONNECTED);

    final MessageConnectorState state;

    SocketState(MessageConnectorState state) { this.state = state; }
}
