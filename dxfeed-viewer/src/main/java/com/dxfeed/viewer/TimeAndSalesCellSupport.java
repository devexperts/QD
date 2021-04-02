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

import java.awt.Color;
import java.util.TimeZone;
import javax.swing.SwingConstants;

import static com.dxfeed.viewer.ViewerCellValue.formatExchange;
import static com.dxfeed.viewer.ViewerCellValue.formatPrice;
import static com.dxfeed.viewer.ViewerCellValue.formatSize;
import static com.dxfeed.viewer.ViewerCellValue.formatTime;

class TimeAndSalesCellSupport {
    private static final Color DEFAULT_COLOR = new Color(0xC0C0C0);
    private static final Color UPDATED_COLOR = Color.WHITE;
    private static final Color ALERT_COLOR = new Color(86, 0, 0, 218);

    public static ViewerCellValue textValue(String text, int alignment, boolean isUpdated, boolean isAlert) {
        return new ViewerCellValue(text, (isUpdated ? UPDATED_COLOR : DEFAULT_COLOR), (isAlert? ALERT_COLOR : null), alignment);
    }

    public static ViewerCellValue exchangeValue(char exchange, boolean isUpdated, boolean isAlert) {
        return textValue(formatExchange(exchange), SwingConstants.CENTER, isUpdated, isAlert);
    }

    public static ViewerCellValue priceValue(double price, boolean isUpdated, boolean isAlert) {
        return textValue(formatPrice(price), SwingConstants.RIGHT, isUpdated, isAlert);
    }

    public static ViewerCellValue sizeValue(double size, boolean isUpdated, boolean isAlert) {
        return textValue(formatSize(size), SwingConstants.RIGHT, isUpdated, isAlert);
    }

    public static ViewerCellValue timeValue(long time, boolean isUpdated, boolean isAlert, TimeZone timeZone) {
        return textValue(formatTime(time, timeZone), SwingConstants.CENTER, isUpdated, isAlert);
    }
}
