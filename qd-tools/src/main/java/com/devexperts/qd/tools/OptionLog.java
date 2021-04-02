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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;

public class OptionLog extends OptionString {

    private static OptionLog instance;

    public static OptionLog getInstance() {
        if (instance == null) {
            instance = new OptionLog();
        }
        return instance;
    }

    private OptionLog() {
        super('l', "log", "<file>", "Redirect log to a file.");
        setDeprecated("Use -D" + Logging.LOG_FILE_PROPERTY + "=<file> option.");
    }

    public void init() {
        Logging.configureLogFile(getValue());
    }
}
