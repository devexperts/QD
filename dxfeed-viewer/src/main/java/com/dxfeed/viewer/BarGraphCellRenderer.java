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
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

class BarGraphCellRenderer extends ViewerCellRenderer implements Icon {
    private double maxValue = 0; // max value for the whole book to zoom bars correctly
    private boolean useParentColor = true;
    private int graphAlignment = LEFT;

    BarGraphCellRenderer(boolean useParentColor) {
        this.useParentColor = useParentColor;
        this.setIcon(this);
        this.graphAlignment = LEFT;
    }

    public void toggleHorizontalAlignment() {
        if (graphAlignment == RIGHT)
            graphAlignment = LEFT;
        else
            graphAlignment = RIGHT;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g;

        Color col = new Color(0, 49, 91);
        Color bg = getBackground();
        if (useParentColor)
            col = bg.brighter();

        if (!col.equals(bg)) {
            g2d.setColor(col);

            int w = getWidth();
            double v = getValue();
            double zoomFactor = w / (maxValue);
            int d = (int) (v * zoomFactor);
            d = Math.min(d, w);
            g2d.fillRect((graphAlignment == RIGHT) ? (w - d) : 0, 0, d, getHeight());
        }
    }

    public int getIconWidth() {
        return 1;
    }

    public int getIconHeight() {
        return 1;
    }

    boolean setMaxValue(double maxValue) {
        if (this.maxValue != maxValue) {
            this.maxValue = maxValue;
            return true;
        }
        return false;
    }
}
