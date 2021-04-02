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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.qd.impl.matrix.CollectorDebug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Command-line interface for {@link DebugDumpReader}.
 */
public class DebugDumpCLI {

    private final PrintStream out = System.out;

    private enum Cmd {
        INFO,
        VERIFY,
        SUB,
        DATA,
        QUEUE,
        SYMBOL;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.US);
        }
    }

    private final DebugDumpReader reader;
    private final BufferedReader in;

    public DebugDumpCLI(DebugDumpReader reader) {
        this.reader = reader;
        in = new BufferedReader(new InputStreamReader(System.in));
    }

    private void help() {
        out.println("--- Supported commands ---");
        out.println(Cmd.INFO + "                         - Print dump info");
        out.println(Cmd.VERIFY + "                       - Verify dump's integrity");
        out.println(Cmd.SUB + " [<symbol> [<record>]]    - Print total & agent subscription");
        out.println(Cmd.DATA + " [<symbol> [<record>]]   - Print stored data");
        out.println(Cmd.QUEUE + " [<symbol> [<record>]]  - Analyze queue");
        out.println(Cmd.SYMBOL + " [<symbol> [<record>]] - Analyze symbol refs inside core");
    }

    public void interactive() throws IOException {
        reader.dumpInfo();
        help();
        String line;
        while ((line = readLine()) != null)
            execute(line.split("\\s+"));
    }

    private String readLine() throws IOException {
        out.print("? ");
        return in.readLine();
    }

    public void execute(String[] args) {
        // support multiple commands separated with "+"
        int plusIndex;
        while (true) {
            plusIndex = Arrays.asList(args).indexOf("+");
            if (plusIndex <= 0 || plusIndex >= args.length - 1)
                break; // just one command or starts/ends with "+", which is an error
            executeOne(Arrays.copyOf(args, plusIndex));
            args = Arrays.copyOfRange(args, plusIndex + 1, args.length);
        }
        executeOne(args);
    }

    private void executeOne(String[] args) {
        // execute a single command
        Cmd cmd;
        try {
            cmd = Cmd.valueOf(args[0].toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            out.println("Unknown command '" + args[0] + "'");
            help();
            return;
        }
        final String filterSymbol = args.length > 1 ? args[1] : null;
        final String filterRecord = args.length > 2 ? args[2] : null;
        final CollectorDebug.RehashCrashInfo rci = reader.getRehashCrashInfo();
        switch (cmd) {
        case INFO:
            reader.dumpInfo();
            break;
        case VERIFY:
            reader.visit(reader.getOwner(), collector ->
                collector.verify(CollectorDebug.CONSOLE, rci));
            break;
        case SUB:
            reader.visit(reader.getOwner(), new DumpSubscriptionVisitor(out, filterSymbol, filterRecord));
            break;
        case DATA:
            reader.visit(reader.getOwner(), new DumpDataVisitor(out, filterSymbol, filterRecord));
            break;
        case QUEUE:
            reader.visit(reader.getOwner(), collector ->
                collector.analyzeQueue(CollectorDebug.CONSOLE, filterSymbol, filterRecord));
            break;
        case SYMBOL:
            reader.visit(reader.getOwner(), collector ->
                collector.analyzeSymbolRefs(CollectorDebug.CONSOLE, filterSymbol, filterRecord, rci));
            break;
        }
    }
}
