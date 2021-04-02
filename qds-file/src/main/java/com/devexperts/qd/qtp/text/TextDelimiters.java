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
package com.devexperts.qd.qtp.text;

public enum TextDelimiters {
    TAB_SEPARATED("==", "=", '\t'),
    COMMA_SEPARATED(null, "#=", ',');

    public static final String LEGACY_QD_PREFIX = "QD_";

    public final String messageTypePrefix; // "==" or null
    public final String describePrefix;    // "="  or "#="
    public final char fieldSeparatorChar;  // '\t' or ','

    TextDelimiters(String messageTypePrefix, String describePrefix, char fieldSeparatorChar) {
        this.messageTypePrefix = messageTypePrefix;
        this.describePrefix = describePrefix;
        this.fieldSeparatorChar = fieldSeparatorChar;
    }
}
