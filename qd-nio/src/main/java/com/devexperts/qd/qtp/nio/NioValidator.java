/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.nio;

/**
 * Thread that periodically validates existing client connections.
 */
class NioValidator extends NioWorkerThread {

    NioValidator(NioCore core) {
        super(core, "Validator");
    }

    protected void makeIteration() throws InterruptedException {
        sleep(100);
        boolean closed = false;
        long time = System.currentTimeMillis();
        for (NioConnection connection : core.connections) {
            if (connection.isClosed()) {
                core.removeConnection(connection);
                closed = true;
                continue;
            }
            try {
                connection.applicationConnection.examine(time);
            } catch (Throwable t) {
                log.error("unexpected error while examining connection", t);
            }
        }
        if (closed)
            core.connector.notifyMessageConnectorListeners();
    }
}
