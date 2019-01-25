/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

/**
 * Thrown to indicate that subscription filter pattern for
 * {@link PatternFilter}, {@link CompositeFilters} or other filters has syntax errors.
 */
public class FilterSyntaxException extends IllegalArgumentException {
    public FilterSyntaxException(String message) {
        super(message);
    }

    public FilterSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
