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

import com.devexperts.qd.qtp.MessageType;

import java.util.Locale;

public class OptionPostCollector extends OptionEnum {
    public static final String TICKER = "ticker";
    public static final String STREAM = "stream";
    public static final String HISTORY = "history";
    public static final String RAW = "raw";

    public OptionPostCollector(String default_value) {
        super('c', "collector", "One of {values}.", default_value, TICKER, STREAM, HISTORY, RAW);
    }

    public MessageType getMessageType() {
        return MessageType.valueOf(getValueOrDefault().toUpperCase(Locale.US) + "_DATA");
    }
}
