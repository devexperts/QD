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

import com.devexperts.services.ServiceProvider;

import java.util.Timer;
import java.util.TimerTask;

@ToolSummary(
    info = "Tracks time in various JVMs.",
    argString = "",
    arguments = {}
)
@ServiceProvider
public class Time extends AbstractTool {
    private final Option request = new Option('R', "request", "Request all peers to report deltas.");
    private final Option verbose = new Option('v', "verbose", "Print info for all peers (multiple per host).");
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();

    @Override
    protected Option[] getOptions() {
        return new Option[] { request, stat, verbose, html, rmi, logfile };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length != 0) {
            wrongNumberOfArguments();
        }
        if (!request.isSet() && !stat.isSet()) {
            throw new BadToolParametersException("Either '--request' or '--stat' option must be set");
        }
        TimeSyncTracker.getInstance().start();

        if (request.isSet()) {
            TimeSyncTracker.getInstance().sendRequest(verbose.isSet());
        }

        if (stat.isSet()) {
            long period = stat.getValue().getTime();
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    TimeSyncTracker tst = TimeSyncTracker.getInstance();
                    tst.analyze(true);
                    tst.dumpPeers(verbose.isSet());
                    tst.sendRequest(verbose.isSet());
                }
            }, period, period);
        }
    }

    @Override
    public Thread mustWaitForThread() {
        return Thread.currentThread();
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Time.class, args);
    }
}
