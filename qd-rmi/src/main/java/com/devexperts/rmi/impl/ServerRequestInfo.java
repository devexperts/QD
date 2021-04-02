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

import com.devexperts.io.Marshalled;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.RMIServiceId;

class ServerRequestInfo {
    final long reqId;
    final long channelId;
    final RMIRequestMessage<?> message;
    final Marshalled<?> subject;
    final RMIMessageKind kind;
    // true if server-side (or mux-side) load balancer retargeted the request, false otherwise
    final boolean retargetedByLoadBalancer;

    ServerRequestInfo(RMIMessageKind kind, long reqId, long channelId, RMIRequestMessage<?> message,
        Marshalled<?> subject)
    {
        this(kind, reqId, channelId, message, subject, false);
    }

    private ServerRequestInfo(RMIMessageKind kind, long reqId, long channelId, RMIRequestMessage<?> message,
        Marshalled<?> subject, boolean retargetedByLoadBalancer)
    {
        this.reqId = reqId;
        this.channelId = channelId;
        this.subject = subject;
        this.message = message;
        this.kind = kind;
        this.retargetedByLoadBalancer = retargetedByLoadBalancer;
    }

    ServerRequestInfo changeTargetRoute(RMIServiceId newTarget) {
        RMIRequestMessage<?> retargetedMessage = message.changeTargetRoute(newTarget, message.getRoute());
        return new ServerRequestInfo(kind, reqId, channelId, retargetedMessage, subject, true);
    }

    @Override
    public String toString() {
        return "ServerRequestInfo{" +
            "reqId=" + reqId +
            ", channelId=" + channelId +
            ", message=" + message +
            ", subject=" + subject +
            ", kind=" + kind +
            ", retargetedByLoadBalancer=" + retargetedByLoadBalancer +
            '}';
    }
}
