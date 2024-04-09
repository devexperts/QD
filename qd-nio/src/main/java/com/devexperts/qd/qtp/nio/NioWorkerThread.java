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

import com.devexperts.logging.Logging;

/**
 * Base class for all NIO threads.
 * Works until the core is stopped (so there's no need to terminate it manually somehow else).
 */
abstract class NioWorkerThread extends Thread {

    protected final Logging log = Logging.getLogging(getClass());
    final NioCore core;

    protected NioWorkerThread(NioCore core, String name) {
        this.core = core;
        setName(core + "-" + name);
        setDaemon(true);
        setPriority(core.connector.getThreadPriority());
    }

    @Override
    public void run() {
        try {
            while (!isClosed()) {
                try {
                    makeIteration();
                } catch (Throwable t) {
                    if (isClosed()) {
                        if (!(t instanceof InterruptedException))
                            log.warn(t.getMessage(), t);
                    } else {
                        log.error(t.getMessage(), t);
                    }
                }
            }
        } finally {
            if (core.isClosed()) {
                core.closeConnections();
            }
        }
    }

    protected boolean isClosed() {
        return core.isClosed();
    }

    protected abstract void makeIteration() throws Throwable;
}
