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
package com.devexperts.qd;

import com.devexperts.logging.Logging;

@Deprecated
public final class QDLog {
    /**
     * Default logging instance for all QD-related information, warning, and error messages.
     * @deprecated Use specific {@link Logging} instances instead.
     */
    @Deprecated
    public static Logging log = Logging.getLogging("com.devexperts.QD");

    static {
        log.configureDebugEnabled(true);
    }

    private QDLog() {}
}
