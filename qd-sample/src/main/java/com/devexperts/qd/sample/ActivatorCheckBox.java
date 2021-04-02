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
package com.devexperts.qd.sample;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;

public class ActivatorCheckBox extends JCheckBox implements ActionListener {
    private final ActivatableModel model;

    public ActivatorCheckBox(ActivatableModel model) {
        super("Active", true);
        this.model = model;
        addActionListener(this);
    }

    public void actionPerformed(ActionEvent e) {
        model.setActive(isSelected());
    }
}
