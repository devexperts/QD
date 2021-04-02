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

import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.TestWithParameters;

public class TraceRunnerWithParameters extends BlockJUnit4ClassRunnerWithParameters {
    private final RunListener listener = new TraceListener();

    public TraceRunnerWithParameters(TestWithParameters test) throws InitializationError {
        super(test);
    }

    @Override
    public void run(RunNotifier notifier) {
        notifier.addFirstListener(listener);
        super.run(notifier);
        notifier.removeListener(listener);
    }
}
