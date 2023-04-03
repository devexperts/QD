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
package com.devexperts.test;

import com.devexperts.logging.TraceLogging;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

@RunListener.ThreadSafe
class TraceListener extends RunListener {
    @Override
    public void testStarted(Description description) throws Exception {
        TraceLogging.restart();
    }

    @Override
    public void testFailure(Failure f) throws Exception {
        TraceLogging.logAndStop(TraceRunner.class, "Test stopped with failure: " + f.getMessage(), f.getException());
        dump(f.getDescription());
    }

    @Override
    public void testFinished(Description description) throws Exception {
        TraceLogging.stop();
        if (TraceRunner.DUMP_ALWAYS)
            dump(description);
    }

    private void dump(Description description) {
        TraceLogging.dump(System.out, description.getDisplayName());
    }
}
