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
package com.dxfeed.viewer;

import com.devexperts.util.DayUtil;

import java.awt.Color;
import java.util.TimeZone;
import javax.swing.SwingConstants;

class QuoteBoardCellSupport {
    public enum State {
        NOT_AVAILABLE(Color.DARK_GRAY, Color.DARK_GRAY),
        COMMON(new Color(0xA0A0A0), Color.WHITE),
        INCREASED(new Color(0x04CD00), new Color(0x05EE00)),
        DECREASED(new Color(0xBE0000), Color.RED),
        FIRST_TIME(new Color(0xEEBB00), Color.YELLOW),
        INFO(new Color(0x958BA2), Color.WHITE),
        ;

        public final Color color;
        public final Color updatedColor;

        State(Color color, Color updatedColor) {
            this.color = color;
            this.updatedColor = updatedColor;
        }
    }

    static ViewerCellValue priceValue(double value, State state, long updateTime) {
        return textValue(ViewerCellValue.formatPrice(value), Double.isNaN(value) ? State.NOT_AVAILABLE : state, updateTime, SwingConstants.RIGHT);
    }

    static ViewerCellValue sizeValue(double value, long updateTime) {
        return textValue(ViewerCellValue.formatSize(value), Double.isNaN(value) ? State.NOT_AVAILABLE : State.COMMON, updateTime, SwingConstants.RIGHT);
    }

    static ViewerCellValue exchangeValue(char value, long updateTime) {
        return textValue(ViewerCellValue.formatExchange(value), value == Character.MAX_VALUE ? State.NOT_AVAILABLE : State.COMMON, updateTime, SwingConstants.CENTER);
    }

    static ViewerCellValue timeValue(long value, long updateTime, TimeZone timeZone) {
        return textValue(ViewerCellValue.formatTime(value, timeZone), value == 0 || value == Long.MAX_VALUE ? State.NOT_AVAILABLE : State.COMMON, updateTime, SwingConstants.CENTER);
    }

    static ViewerCellValue dayIdValue(int value, long updateTime) {
        String description;
        State state;

        if (value == 0 || value == Integer.MAX_VALUE) {
            description = ViewerCellValue.NA;
            state = State.NOT_AVAILABLE;
        } else {
            description = Integer.toString(DayUtil.getYearMonthDayByDayId(value));
            state = State.COMMON;
        }

        return textValue(description, state, updateTime, SwingConstants.CENTER);
    }

    static ViewerCellValue boolValue(boolean value, long updateTime) {
        return textValue(value ? "Y" : "N", State.COMMON, updateTime, SwingConstants.CENTER);
    }

    static ViewerCellValue textValue(String text, State state, long updateTime, int alignment) {
        return textValue(text, state, System.currentTimeMillis() < updateTime + 200, alignment);
    }

    static ViewerCellValue textValue(String text, State state, boolean isUpdated, int alignment) {
        return new ViewerCellValue(text, isUpdated ? state.updatedColor : state.color, null, alignment);
    }
}
