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
import com.devexperts.qd.tools.reporting.ReportBuilder;

/**
 * Self-state reporting facility that can be implemented by a {@link Module}.
 */
@Experimental
public interface StateReportingSupport {
    /**
     * Report self-representation of the component state.
     * FIXME: module self-status reporting is optional and shall be implemented as an introspectable object.
     */
    public void reportCurrentState(ReportBuilder reportBuilder);
}
