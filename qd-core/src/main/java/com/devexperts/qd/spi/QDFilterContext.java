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
package com.devexperts.qd.spi;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.SymbolSetFilter;

/**
 * Defines context of creation operation for {@link QDFilterFactory}.
 */
public enum QDFilterContext {
    /**
     * Default context.
     */
    DEFAULT,

    /**
     * Context of construction of named project-specific filter.
     * This context is used in {@link CompositeFilters#valueOf(String, String, DataScheme)}.
     */
    NAMED,

    /**
     * Records-only filter should be produced in this context.
     * This context is used in {@link RecordOnlyFilter#valueOf(String, DataScheme)}.
     */
    RECORD_ONLY,

    /**
     * Symbols-set-only filter should be produced in this context.
     * This context is used in {@link SymbolSetFilter#valueOf(String, DataScheme)}.
     */
    SYMBOL_SET,

    /**
     * Context of construction of remote filter that was received over network connection.
     */
    REMOTE_FILTER
}
