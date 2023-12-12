/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.services.Service;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all QDS tools.
 */
@Service
public abstract class AbstractTool {
    protected final Logging log = Logging.getLogging(getClass());

    private Options options;
    private String[] args;

    protected AbstractTool() {
        // Force QDS version print to log
        QDFactory.showVersion();
    }

    /**
     * Parses arguments for this tool.
     * @param args arguments and options.
     * @throws BadToolParametersException if couldn't parse tool arguments or options.
     */
    public void parse(String[] args) throws BadToolParametersException {
        options = new Options(getOptions());
        this.args = options.parse(args); // remaining arguments (parameters)
    }

    /**
     * Executes this tool.
     */
    public void execute() {
        executeImpl(args);
    }

    private String getToolName() {
        return this.getClass().getSimpleName();
    }

    protected QDEndpoint.Builder getEndpointBuilder() {
        return options.getEndpointBuilder();
    }

    protected static void wrongNumberOfArguments() {
        throw new BadToolParametersException("Wrong number of arguments");
    }

    protected static void noRequiredOptions() {
        throw new BadToolParametersException("No required options");
    }

    protected static void noArguments() {
        // signal to print a detailed help
        throw new NoArgumentsException();
    }

    /**
     * Executes this concrete tool.
     * @param args the actual parameters to the tools after parsing and removing all options.
     */
    protected abstract void executeImpl(String[] args);

    /**
     * Returns array with all options used by this tool.
     * @return array with all options used by this tool.
     */
    protected Option[] getOptions() {
        return new Option[0];
    }

    /**
     * Generates basic help on this tool.
     *
     * @param screenWidth width of generated info (in characters).
     * @return String with generated help message.
     */
    protected String generateHelpSummary(int screenWidth) {
        String toolName = getToolName();
        Options opts = (options == null) ? new Options(getOptions()) : options;

        StringBuilder res = new StringBuilder();
        ToolSummary annotation = this.getClass().getAnnotation(ToolSummary.class);
        if (annotation == null) {
            return "No annotation found";
        }
        res.append(Help.format(annotation.info(), screenWidth));

        String usagePrefix = "\t" + toolName + " ";
        if (opts.getCount() > 0) {
            usagePrefix += "[<options>] ";
        }
        res.append("\nUsage:\n");
        for (String usage : annotation.argString()) {
            res.append(Help.format(usagePrefix + usage, screenWidth));
        }

        if (annotation.arguments().length > 0) {
            res.append("\nArguments:\n");
            ArrayList<String[]> argsTable = new ArrayList<String[]>();
            for (String arg : annotation.arguments()) {
                String[] argSplit = arg.split("--", 2);
                if (argSplit.length < 2) {
                    argSplit = arg.split(" ", 2);
                }
                if (argSplit.length < 2) {
                    argSplit = new String[]{arg, ""};
                }
                argsTable.add(new String[]{"   ", argSplit[0].trim(), "-", argSplit[1].trim()});
            }
            res.append(Help.formatTable(argsTable, screenWidth, " "));
        }

        if (opts.getCount() > 0) {
            res.append("\n").append(opts.generateHelp(screenWidth));
        }

        return res.toString();
    }

    /**
     * Returns a list of connectors that must become inactive before the tool can terminate.
     */
    public List<MessageConnector> mustWaitWhileActive() {
        return null;
    }

    /**
     * Returns a thread that must be joined to wait until tool finished its job.
     * Returns null if the tool does not need to wait for any thread and the end (just quit).
     * Returns {@code Thread.currentThread()} if the tools needs to wait forever.
     */
    public Thread mustWaitForThread() {
        return null;
    }

    /**
     * Returns a list of resources that must be closed before the exit from JVM.
     */
    public List<Closeable> closeOnExit() {
        return null;
    }
}
