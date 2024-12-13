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
package com.devexperts.qd.tools.module;

import com.devexperts.annotation.Experimental;
import com.devexperts.logging.Logging;
import com.devexperts.qd.tools.reporting.ReportBuilder;

import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Represents an event processing facility that can handle some "structured" events.
 * Events supposed to be passed to log and also preserved for the partial extraction.
 *
 * <p>It's supposed that "classification" of events is limited and consistent among modules, so it would be possible to
 * perform cross-module "queries" with consistent results.
 *
 * <p>Only important configuration and lifecycle related events shall be passed to the registry as it's intended to
 * present a quick overview of the module life for the operator in some diagnostic UI (opposing to general logging)
 */
@Experimental
public interface EventLog {

    /**
     * event - arbitrary object that may carry on additional info. As a "last resort" it should provide meaningful
     * {@link Object#toString} implementation.
     */
    public void log(Level level, @Nonnull Object event);

    /**
     * @return wrapped {@link Logging} instance.
     */
    public Logging getLogging();

     public default void info(Object event) {
        log(Level.INFO, event);
    }

    public default void warn(Object event) {
        log(Level.WARNING, event);
    }

    public default void error(Object event) {
        log(Level.SEVERE, event);
    }

    // FIXME: may be augmented with filtering / limit. Or maybe should be moved away
    public void reportEventLog(ReportBuilder reportBuilder);

    // FIXME: probably some kind of traversing API would be more universal (limit, filter?)
    // public Iterable<LogEntry> getLogEvents();

}
