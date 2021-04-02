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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class SwingExecutor implements Executor {
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<Runnable>();
    private final int delay;

    public SwingExecutor(int delay) {
        this.delay = delay;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createTimer();
            }
        });
    }

    private void createTimer() {
        Timer timer = new Timer(delay, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Runnable command;
                while ((command = commands.poll()) != null)
                    command.run();
            }
        });
        timer.start();
    }

    public void execute(Runnable command) {
        commands.add(command);
    }
}
