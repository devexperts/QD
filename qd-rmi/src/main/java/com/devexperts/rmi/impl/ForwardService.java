/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.impl;

import com.devexperts.rmi.*;
import com.devexperts.rmi.task.*;

class ForwardService extends RMIService<Object> {

    protected final RMIClientPort clientPort;
    protected RMIRequest<?> request;

    ForwardService(String serviceName, RMIClientPort clientPort) {
        super(serviceName);
        this.clientPort = clientPort;
    }

    @Override
    public void processTask(RMITask<Object> task) {
        RMIRequest<?> request = createRequest(task);
        task.setCancelListener(task1 -> request.cancelWith(task1.getState().needsConfirmation()));
        request.setListener(request1 -> task.completeResponse(request1.getResponseMessage()));
        request.send();
    }

    RMIRequest<?> createRequest(RMITask<?> task) {
        assert task.getSubject().equals(clientPort.getSubject());
        return clientPort.createRequest(task.getRequestMessage());
    }
}
