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
package com.devexperts.qd.tools.reporting;

import com.devexperts.annotation.Experimental;
import com.devexperts.qd.tools.module.Module;

import java.util.List;

/**
 * Builder for logs and status reports emitted by a {@link Module}.
 * For the moment, a report is a simple list of messages in tabular form.
 *
 * @apiNote elements of the report provided as arbitrary objects. By default, a value will be represented by its
 *     {@link Object#toString()} value, null-values will be treated as empty cell. Some implementations may provide
 *     additional support for formatting particular types.
 */
@Experimental
public interface ReportBuilder {

    // TODO: Possible way of the extension in
    // public StatusReportBuilder beginTable();
    // public StatusReportBuilder endTable();
    // public StatusReportBuilder addMessage(Object message);

    public ReportBuilder addHeaderRow(Object... values);

    public ReportBuilder addHeaderRow(List<?> values);

    public ReportBuilder addRow(Object... values);

    public ReportBuilder addRow(List<?> values);

}
