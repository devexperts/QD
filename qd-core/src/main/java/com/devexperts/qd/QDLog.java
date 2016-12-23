/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd;

import com.devexperts.logging.Logging;

public class QDLog {
    /**
     * Default logging instance for all QD-related information, warning, and error messages.
     * It should be generally used by all classes from {@code com.devexperts.qd} package.
     * However, some QD classes might use specific {@link Logging} instances whenever appropriate.
     */
    public static Logging log = Logging.getLogging("com.devexperts.QD");

    // workaround for DefaultLogging's default setting of disabled debug
    // todo: implement more general solution inside Logging?
    static {
        log.configureDebugEnabled(true);
    }

    private static QDLog instance = new QDLog();

    protected QDLog() {}

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public static QDLog getInstance() {
        return instance;
    }

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public static void setInstance(QDLog instance) {
        QDLog.instance = instance;
    }

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public void debug(Object msg) {
        log.debug(String.valueOf(msg));
    }

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public void info(Object msg) {
        log.info(String.valueOf(msg));
    }

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public void error(Object msg, Throwable t) {
        log.error(String.valueOf(msg), t);
    }

    /**
     * @deprecated Use {@link #log} and {@link com.devexperts.logging.Logging}
     */
    public void error(Object msg) {
        error(msg, null);
    }
}
