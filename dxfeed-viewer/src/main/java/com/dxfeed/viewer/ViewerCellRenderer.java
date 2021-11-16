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
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;

class ViewerCellRenderer extends DefaultTableCellRenderer {
    public static final ViewerCellRenderer INSTANCE = new ViewerCellRenderer();

    public static final Color DARK_SCHEME_GRID_COLOR = new Color(0x2F2F2F);
    public static final Color DARK_SCHEME_BACKGROUND_COLOR_1 = Color.BLACK;
    public static final Color DARK_SCHEME_BACKGROUND_COLOR_2 = new Color(0x202020);

    public static final Color LIGHT_SCHEME_GRID_COLOR = new Color(0xE0E0E0);
    public static final Color LIGHT_SCHEME_BACKGROUND_COLOR_1 = new Color(0xF5F5F5);
    public static final Color LIGHT_SCHEME_BACKGROUND_COLOR_2 = Color.WHITE;

    public static final Color SELECTED_BG_COLOR = new Color(0xFFCC00);
    public static final Color SELECTED_FG_COLOR = Color.BLACK;
    public static final Color EDIT_BG_COLOR = new Color(0xAA8800);
    public static final Color EDIT_FG_COLOR = Color.WHITE;

    public static final Border EMPTY_BORDER = new EmptyBorder(0, 2, 0, 2);
    public static final Border FOCUS_BORDER = new LineBorder(EDIT_BG_COLOR, 2);

    public static final int DARK_SCHEME = 0;
    public static final int LIGHT_SCHEME = 1;
    public static final int DEFAULT_SCHEME = DARK_SCHEME;

    private int scheme = DEFAULT_SCHEME;
    private double value;

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
        int row, int column)
    {
        ViewerCellValue cellValue = (ViewerCellValue) value;
        setText(cellValue.getText());
        setValue(cellValue.getValue());

        if (isSelected) {
            setForeground(SELECTED_FG_COLOR);
            setBackground(SELECTED_BG_COLOR);
        } else {
            setForeground(cellValue.getColor());

            if (cellValue.getBackground() == null)
                if (scheme == DARK_SCHEME)
                    setBackground((row & 1) == 0 ? DARK_SCHEME_BACKGROUND_COLOR_1 : DARK_SCHEME_BACKGROUND_COLOR_2);
                else
                    setBackground((row & 1) == 0 ? LIGHT_SCHEME_BACKGROUND_COLOR_1 : LIGHT_SCHEME_BACKGROUND_COLOR_2);
            else
                setBackground(cellValue.getBackground());
        }

        setBorder(hasFocus ? FOCUS_BORDER : EMPTY_BORDER);
        setFont(table.getFont());
        setHorizontalAlignment(cellValue.getAlignment());

        return this;
    }

    void setValue(double value) {
        this.value = value;
    }

    double getValue() {
        return value;
    }

    public void setScheme(int scheme) {
        this.scheme = scheme;
    }
}
