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
import com.devexperts.util.TimeFormat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses text file with GC log and replaces seconds from start to absolute time.
 */
@ToolSummary(
    info = "Parses text file with GC log and replaces seconds from start to absolute time.",
    argString = "<file>",
    arguments = {
        "<file> -- files to parse"
    }
)
@ServiceProvider
public class GCTimeTransformer extends AbstractTool {
    private final OptionString time = new OptionString('t', "time", "time", "Start time.");
    private final OptionString output = new OptionString('o', "output", "<file>", "Output file, by default 'gctimes.txt'.");
    private final Matcher dateTimeMatcher = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}).*").matcher("");
    private final Matcher gcLineMatcher = Pattern.compile("^(\\d+)\\.\\d{3}: \\[.*").matcher("");

    @Override
    protected Option[] getOptions() {
        return new Option[] {time, output};
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();
        try {
            String fileName = output.isSet() ? output.getValue() : "gctimes.txt";
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName), 100000));
            Date globalTimeStamp = time.isSet() ? TimeFormat.DEFAULT.parse(time.getValue()) : null;
            for (String file : args) {
                BufferedReader in = new BufferedReader(new FileReader(file), 100000);
                Date baseTimeStamp = globalTimeStamp;
                boolean firstLine = true;
                for (String line; (line = in.readLine()) != null;) {
                    if (firstLine) {
                        dateTimeMatcher.reset(line);
                        if (dateTimeMatcher.matches() && baseTimeStamp == null)
                            baseTimeStamp = TimeFormat.DEFAULT.parse(dateTimeMatcher.group(1));
                        if (baseTimeStamp == null)
                            throw new BadToolParametersException("No time is specified nor there is time in first line of file");
                        firstLine = false;
                    }
                    gcLineMatcher.reset(line);
                    if (gcLineMatcher.matches()) {
                        long time = baseTimeStamp.getTime() + Integer.parseInt(gcLineMatcher.group(1)) * 1000L;
                        out.print(TimeFormat.DEFAULT.format(time));
                        out.println(line.substring(gcLineMatcher.group(1).length() + 4));
                    } else {
                        out.println(line);
                    }
                }
                in.close();
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(GCTimeTransformer.class, args);
    }
}
