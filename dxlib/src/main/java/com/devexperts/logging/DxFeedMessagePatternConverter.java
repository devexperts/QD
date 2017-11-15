/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.*;
import org.apache.logging.log4j.util.PerformanceSensitive;

/**
 * Converter that removes backspace character from message beginning.
 */
@Plugin(name = "dxFeedMessagePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"dxm", "dxmsg", "dxMessage"})
@PerformanceSensitive("allocation")
@Order(50)
@SuppressWarnings("unused") //used by Log4j2
public final class DxFeedMessagePatternConverter extends LogEventPatternConverter {

    private DxFeedMessagePatternConverter() {
        super("dxMessage", "message");
    }

    public static DxFeedMessagePatternConverter newInstance() {
        return new DxFeedMessagePatternConverter();
    }

    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        String msg = event.getMessage().getFormat();
        int start = msg.isEmpty() || msg.charAt(0) != '\b' ? 0 : 1;
        toAppendTo.append(msg, start, msg.length());
    }
}
