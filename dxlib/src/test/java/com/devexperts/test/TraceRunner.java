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
package com.devexperts.test;

import com.devexperts.logging.TraceLogging;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * Extended JUnit4 runner that dumps {@link TraceLogging} to the console if test crashes.
 */
public class TraceRunner extends BlockJUnit4ClassRunner {
    static final boolean DUMP_ALWAYS = System.getProperty("TraceRunner.DumpAlways") != null;

    private final RunListener listener = new TraceListener();

    public TraceRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public void run(RunNotifier notifier) {
        notifier.addFirstListener(listener);
        super.run(notifier);
        notifier.removeListener(listener);
    }
}
