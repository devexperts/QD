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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;

public class OptionWrite extends OptionTimePeriod {

    private static final Logging log = Logging.getLogging(OptionWrite.class);

    private final OptionFile file;

    public OptionWrite(OptionFile file) {
        super('w', "write", "<n>", "Write storage to file on specified periods (in seconds by default).");
        this.file = file;
    }

    public void init() {
        if (!file.isSet())
            throw new IllegalArgumentException(this + " option cannot be used without " + file + " option.");
    }

    public void initFileWrite(FeedFileHandler handler) {
        if (!isSet())
            return;
        startFileWriterThread(handler, getValue().getTime());
    }

    private static void startFileWriterThread(final FeedFileHandler handler, final long period) {
        Thread thread = new Thread("FileWriter") {
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        Thread.sleep(period);
                        try {
                            handler.writeFile();
                        } catch (Throwable t) {
                            log.error("Fatal exception in writer thread. Will try to continue anyway", t);
                        }
                    }
                } catch (InterruptedException e) {
                    // ignore - done
                }
            }
        };
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }
}
