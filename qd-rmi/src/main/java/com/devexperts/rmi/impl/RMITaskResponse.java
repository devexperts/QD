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


import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.message.RMIResponseType;
import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.rmi.task.RMITaskState;

class RMITaskResponse {

    final RMIResponseMessage responseMessage;
    final RMIRequestMessage<?> requestMessage;
    final long channelId;
    final long requestId;
    final RMITaskState state;
    final RMIMessageKind kind;

    RMITaskResponse(RMITaskImpl<?> task) {
        this.responseMessage = task.getResponseMessage();
        this.requestMessage = task.getRequestMessage();
        this.channelId = ((RMIChannelImpl) task.getChannel()).getChannelId();
        this.requestId = task.getRequestId();
        this.state = task.getState();
        boolean nested = task.isNestedTask();
        this.kind = RMIMessageKind.getKind(responseMessage, nested ? task.getChannel().getType() : null);
    }

    RMITaskResponse(RMIResponseMessage responseMessage, long channelId, long requestId, RMIChannelType type) {
        this.responseMessage = responseMessage;
        this.requestMessage = null;
        this.channelId = channelId;
        this.requestId = requestId;
        this.state = responseMessage.getType() == RMIResponseType.ERROR ? RMITaskState.FAILED : RMITaskState.SUCCEEDED;
        this.kind = RMIMessageKind.getKind(responseMessage, type);
    }

    @Override
    public String toString() {
        return "RMITaskResponse{" +
            "responseMessage=" + responseMessage +
            ", requestMessage=" + requestMessage +
            ", channelId=" + channelId +
            ", requestId=" + requestId +
            ", state=" + state +
            ", kind=" + kind +
            '}';
    }
}

