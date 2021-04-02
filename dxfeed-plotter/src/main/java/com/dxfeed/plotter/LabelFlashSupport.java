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
package com.dxfeed.plotter;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.Timer;

class LabelFlashSupport {
    private JLabel label;
    private Color baseColor;
    private Color darkerColor;
    private boolean darker = false;

    LabelFlashSupport(JLabel label) {
        setLabel(label);
    }

    void updateColor(Color color) {
        label.setForeground(color);
        this.baseColor = color;
        this.darkerColor = color.darker();
    }

    void startFlashing() {
        timer.start();
    }

    void stopFlashing() {
        timer.stop();
    }

    boolean isFlashing() {
        return timer.isRunning();
    }

    private void setLabel(JLabel label) {
        this.label = label;
        updateColor(label.getForeground());
    }

    private final Timer timer = new Timer(200, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            Color c;
            if (darker) {
                c = darkerColor;
            } else {
                c = baseColor;
            }
            darker = !darker;
            label.setForeground(c);
        }
    });
}
