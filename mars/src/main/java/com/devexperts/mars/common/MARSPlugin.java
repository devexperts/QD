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
package com.devexperts.mars.common;

/**
 * Plugin that is attached to {@link MARSEndpoint} and provides extra information nodes.
 * MARS plugins should not allocate any external resources like threads and JMX beans (MARS nodes are Ok)
 * during construction. The {@link #start()} method will be called at most once by {@link MARSEndpoint} for this purpose and
 * {@link #stop()} method will be called at most once when resources are no longer needed. The instance will not be reused
 * after that.
 */
public interface MARSPlugin {
    public void start();
    public void stop();
    public String toString(); // must return nice string for log

    /**
     * Factory for MARS Plugins that provide additional features for {@link MARSEndpoint}.
     */
    public abstract class Factory {
        public abstract MARSPlugin createPlugin(MARSEndpoint marsEndpoint);
    }
}
