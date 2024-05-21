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
package com.devexperts.qd.kit;

import com.devexperts.util.InvalidFormatException;

/**
 * Thrown to indicate that the specification for a filter or striper has syntax errors.
 * See {@link PatternFilter}, {@link CompositeFilters}, etc.
 */
public class FilterSyntaxException extends InvalidFormatException {
    public FilterSyntaxException(String message) {
        super(message);
    }

    public FilterSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
