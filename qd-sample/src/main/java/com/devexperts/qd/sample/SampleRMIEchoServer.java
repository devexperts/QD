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
package com.devexperts.qd.sample;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;

public class SampleRMIEchoServer {
    private static final Logging log = Logging.getLogging(SampleRMIEchoServer.class);

    public static void main(String[] args) throws InterruptedException {
        RMIEndpoint endpoint = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        endpoint.getServer().export(new EchoService());
        endpoint.connect(args[0]);
        Thread.sleep(Long.MAX_VALUE);
    }

    private static class EchoService extends RMIService<Object[]> {
        private EchoService() {
            super("echo");
        }

        @Override
        public void processTask(RMITask<Object[]> task) {
            RMIRequestMessage<Object[]> req = task.getRequestMessage();
            log.info("Received echo task for " + req.getParameters() + " from " + req.getRoute());
            task.complete(req.getParameters().getObject());
        }
    }
}
