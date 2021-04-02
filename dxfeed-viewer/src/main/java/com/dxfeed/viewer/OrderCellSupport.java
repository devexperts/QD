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

class OrderCellSupport {
    public enum State {
        NOT_AVAILABLE(new Color(0x8C8C8C), new Color(0xC0C0C0)),
        COMMON(new Color(0xE0E0E0), Color.WHITE),
        ;

        public final Color color;
        public final Color updatedColor;

        State(Color color, Color updatedColor) {
            this.color = color;
            this.updatedColor = updatedColor;
        }
    }

    public static final int NO_SCHEME = -1;
    public static final int DEPTH_SCHEME = 0;
    public static final int ZEBRA_SCHEME = 1;
    public static final int COLORFUL_SCHEME = 2;
    public static final int MONOCHROME_SCHEME = 3;

    private static final Color[] depthScheme = {
        new Color(0, 127, 192),
        new Color(0, 107, 172),
        new Color(0, 87, 152),
        new Color(0, 67, 132),
        new Color(0, 47, 112),
        new Color(0, 27, 92),
        new Color(0, 7, 72),
        new Color(0, 0, 52),
        new Color(0, 0, 32),
        new Color(0, 0, 12)
    };

    private static final Color[] zebraScheme = {
        new Color(-13159371),
        new Color(-11514800)
    };

    private static final Color[] colorfulScheme = {
        new Color(158, 0, 0),
        new Color(172, 95, 0),
        new Color(152, 0, 115),
        new Color(64, 0, 132),
        new Color(0, 112, 111),
        new Color(0, 92, 9),
        new Color(122, 119, 0),
        new Color(0, 122, 90),
        new Color(122, 50, 0),
        new Color(43, 0, 107),
        new Color(107, 37, 103),
        new Color(34, 107, 107),
        new Color(124, 77, 54),
        new Color(4, 124, 2),
        new Color(124, 12, 119)
    };

    private static final Color[] monochromeScheme = {
        new Color(0, 67, 132)
    };

    private static Color selectColor(boolean isUpdated, boolean isDisabled, State state) {
        return isDisabled ? State.NOT_AVAILABLE.color : isUpdated ? state.updatedColor : state.color;
    }

    public static Color selectBackground(int priceGroup, int scheme) {
        Color clr = Color.BLACK;
        switch (scheme) {
            case DEPTH_SCHEME: clr = priceGroup <= depthScheme.length && priceGroup > 0 ? depthScheme[priceGroup - 1] : Color.BLACK; break;
            case ZEBRA_SCHEME: clr = priceGroup % 2 == 0 ? zebraScheme[0] : zebraScheme[1]; break;
            case COLORFUL_SCHEME: clr = priceGroup <= colorfulScheme.length && priceGroup > 0 ? colorfulScheme[priceGroup - 1] : Color.BLACK; break;
            case MONOCHROME_SCHEME: clr = monochromeScheme[0]; break;
        }
        return clr;
    }

    public static ViewerCellValue textValue(String text, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme, int alignment, double value) {
        return new ViewerCellValue(text, selectColor(isUpdated, isDisabled, State.COMMON), selectBackground(priceGroup, scheme), alignment, value);
    }

    public static ViewerCellValue textValue(String text, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme, int alignment) {
        return textValue(text, isUpdated, isDisabled, priceGroup, scheme, alignment, 0);
    }

    public static ViewerCellValue exchangeValue(char exchange, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme) {
        return textValue(formatExchange(exchange), isUpdated, isDisabled, priceGroup, scheme, SwingConstants.CENTER);
    }

    public static ViewerCellValue priceValue(double price, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme) {
        return textValue(formatPrice(price), isUpdated, isDisabled, priceGroup, scheme, SwingConstants.RIGHT, price);
    }

    public static ViewerCellValue sizeValue(double size, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme) {
        return textValue(formatSize(size), isUpdated, isDisabled, priceGroup, scheme, SwingConstants.CENTER, size);
    }

    public static ViewerCellValue timeValue(long time, boolean isUpdated, boolean isDisabled, int priceGroup, int scheme, TimeZone tz) {
        return textValue(formatTime(time, tz), isUpdated, isDisabled, priceGroup, scheme, SwingConstants.CENTER);
    }
}
