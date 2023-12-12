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
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolReceiver;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorListener;
import com.devexperts.qd.util.SymbolSet;
import com.devexperts.services.Services;
import com.devexperts.util.InvalidFormatException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class Tools {

    private static final Logging log = Logging.getLogging(Tools.class);
    private static final List<Class<? extends AbstractTool>> TOOLS = Services.loadServiceClasses(AbstractTool.class, null);

    private static class ToolArgs {
        private final AbstractTool tool;
        private final String[] args;

        ToolArgs(AbstractTool tool, String[] args) {
            this.tool = tool;
            this.args = args;
        }

        public void parse() {
            tool.parse(args);
        }

        void execute() {
            tool.execute();
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Tools <tool> [...]");
            System.err.println("Where <tool> is one of:");
            Help.listAllTools(System.err, Help.DEFAULT_WIDTH);
            System.err.println();
            System.err.println(Help.format(
                "To get detailed help on some tool use \"Help <tool name>\". \n" +
                "To get information about executing several tools under one JVM see \"Help Batch\".",
                Help.DEFAULT_WIDTH));
            return;
        }
        boolean ok = invoke(args);
        System.exit(ok ? 0 : 1); // kill even non-daemon threads and return non-zero code on error
    }

    // It is designed to be used by tests.
    public static boolean invoke(String... args) {
        try {
            List<ToolArgs> tools = new ArrayList<>();
            int i = 0;
            for (int j = 0; j <= args.length; j++) {
                if ((j >= args.length) || (args[j].equals("+"))) {
                    if (i == j) {
                        if (j == args.length) {
                            throw new BadToolParametersException("Wrong format: '+' at the end of arguments list");
                        } else {
                            throw new BadToolParametersException("Wrong format: two consecutive '+' in arguments list");
                        }
                    }
                    String[] singleToolArgs = new String[j - i - 1];
                    System.arraycopy(args, i + 1, singleToolArgs, 0, singleToolArgs.length);
                    String toolName = args[i];
                    AbstractTool tool = getTool(toolName);
                    if (tool == null)
                        throw new BadToolParametersException("Unknown tool \"" + toolName + "\"");
                    tools.add(new ToolArgs(tool, singleToolArgs));
                    i = j + 1;
                }
            }
            // parse all options first
            for (ToolArgs ta : tools)
                try {
                    ta.parse();
                } catch (Throwable t) {
                    handleToolError(ta, t);
                    return false;
                }
            // during parsing we might have modified System Properties.
            // Now we can initialize all startups
            Services.startup();
            // then execute all tools
            for (ToolArgs ta : tools)
                try {
                    ta.execute();
                } catch (Throwable t) {
                    handleToolError(ta, t);
                    return false;
                }
            // wait while tools have active connections
            // Note: this wait may be interrupted (will return from it with interruption flag set)
            boolean waitAgainToMakeSure;
            do {
                waitAgainToMakeSure = false;
                for (ToolArgs ta : tools)
                    if (waitWhileActive(ta.tool.mustWaitWhileActive()))
                        waitAgainToMakeSure = true;
            } while (waitAgainToMakeSure);
            // wait for thread(s) to finish if needed
            // Note: this wait may be interrupted (will return from it with interruption flag set)
            for (ToolArgs ta : tools)
                waitForThread(ta.tool.mustWaitForThread());
            // clean up tools (close all their resources)
            for (ToolArgs ta : tools)
                closeOnExit(ta.tool.closeOnExit());
        } catch (Throwable t) {
            log.error(t.toString(), t);
            return false;
        }
        return true; // success
    }

    private static void handleToolError(ToolArgs ta, Throwable t) {
        String name = ta.tool.getClass().getSimpleName();
        if (t instanceof NoArgumentsException) {
            // Special signal to print detailed help
            System.err.println(name + ": " + ta.tool.generateHelpSummary(Help.DEFAULT_WIDTH));
            System.err.println("Use \"Help " + name + "\" for more detailed help.");
        } else if (t instanceof InvalidFormatException) {
            // Log with stack trace first, then show help message on the screen as last line
            // See [QD-251] Better logging when QD-filter can't be created/loaded
            log.error(t.getMessage(), t);
            System.err.println();
            System.err.println(name + ": " + t.getMessage());
            System.err.println("Use \"Help " + name + "\" for usage info.");
        } else {
            log.error(t.toString(), t);
        }
    }

    private static boolean waitWhileActive(List<MessageConnector> messageConnectors) {
        if (messageConnectors == null)
            return false;
        boolean waitAgainToMakeSure = false;
        for (MessageConnector connector : messageConnectors)
            if (connector.isActive()) {
                Waiter waiter = new Waiter(connector);
                connector.addMessageConnectorListener(waiter);
                waiter.waitWhileActive();
                if (Thread.currentThread().isInterrupted())
                    return false;
                connector.removeMessageConnectorListener(waiter);
                waitAgainToMakeSure = true;
            }
        return waitAgainToMakeSure;
    }

    private static void waitForThread(Thread thread) {
        if (thread != null)
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // reassert interruption flag
            }
    }

    private static void closeOnExit(List<Closeable> list) {
        if (list == null)
            return;
        for (Closeable closeable : list) {
            log.info("Closing " + closeable);
            try {
                closeable.close();
            } catch (IOException e) {
                log.error("Failed to close " + closeable, e);
            }
        }
    }

    public static void executeSingleTool(Class<? extends AbstractTool> tool, String[] args) {
        String[] argsWithToolName = new String[args.length + 1];
        argsWithToolName[0] = tool.getSimpleName();
        System.arraycopy(args, 0, argsWithToolName, 1, args.length);
        main(argsWithToolName);
    }

    /**
     * Creates and returns new instance of a tool with specified name.
     * @param name tool name.
     * @return new instance of a tool with specified name, or null if couldn't
     * find such tool or failed to create instance.
     */
    public static AbstractTool getTool(String name) {
        for (Class<? extends AbstractTool> tool : TOOLS) {
            if (name.equalsIgnoreCase(tool.getSimpleName()))
                try {
                    return tool.newInstance();
                } catch (InstantiationException e) {
                    return null;
                } catch (IllegalAccessException e) {
                    return null;
                }
        }
        return null;
    }

    /**
     * Returns all available tool names.
     * @return all available tool names.
     */
    public static String[] getToolNames() {
        String[] names = new String[TOOLS.size()];
        for (int i = 0; i < TOOLS.size(); i++)
            names[i] = TOOLS.get(i).getSimpleName();
        return names;
    }

    //======== Some static util methods ========

    public static DataRecord[] parseRecords(String recordList, DataScheme scheme) {
        RecordOnlyFilter filter = RecordOnlyFilter.valueOf(recordList, scheme);
        List<DataRecord> result = new ArrayList<>();
        for (int i = 0, n = scheme.getRecordCount(); i < n; i++) {
            DataRecord record = scheme.getRecord(i);
            if (filter.acceptRecord(record))
                result.add(record);
        }
        return result.toArray(new DataRecord[result.size()]);
    }

    public static String[] parseSymbols(String symbolList, DataScheme scheme) {
        SymbolSet set = SymbolSetFilter.valueOf(symbolList, scheme).getSymbolSet();
        final List<String> result = new ArrayList<>();
        final SymbolCodec codec = scheme.getCodec();
        set.examine(new SymbolReceiver() {
            @Override
            public void receiveSymbol(int cipher, String symbol) {
                result.add(codec.decode(cipher, symbol));
            }
        });
        return result.toArray(new String[result.size()]);
    }

    private static class Waiter implements MessageConnectorListener {
        private final MessageConnector connector;
        private final Thread waitingThread;

        Waiter(MessageConnector connector) {
            this.connector = connector;
            this.waitingThread = Thread.currentThread();
        }

        @Override
        public void stateChanged(MessageConnector connector) {
            if (!this.connector.isActive())
                LockSupport.unpark(waitingThread);
        }

        public void waitWhileActive() {
            while (connector.isActive() && !Thread.currentThread().isInterrupted())
                LockSupport.parkNanos(1000000000L);
        }
    }
}
