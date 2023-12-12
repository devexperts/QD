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
package com.devexperts.qd.tools.analysis;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.file.FileFormat;
import com.devexperts.qd.qtp.file.FileReader;
import com.devexperts.qd.qtp.file.FileReaderParams;
import com.devexperts.qd.tools.AbstractTool;
import com.devexperts.qd.tools.BadToolParametersException;
import com.devexperts.qd.tools.Option;
import com.devexperts.qd.tools.OptionString;
import com.devexperts.qd.tools.ToolSummary;
import com.devexperts.qd.tools.Tools;
import com.devexperts.services.ServiceProvider;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.GlobListUtil;
import com.devexperts.util.LogUtil;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Properties;

@ToolSummary(
    info = "Analyses binary file and distribution of various structures in it.",
    argString = "<file-or-url>",
    arguments = {
        "<file-or-url> -- binary file to analyze."
    }
)
@ServiceProvider
public class FileAnalysis extends AbstractTool {
    private final Option symbols = new Option('s', "symbols", "Analyze symbols and their character distributions");
    private final OptionString groups = new OptionString('g', "group", "<group-file>",
        "Define custom field groups. Supplied file is a property file with <group-name>=<pattern> lines, " +
        "with glob-like patterns (only '*' and ',' are supported as pattern chars).");
    private final OptionString compression = new OptionString('c', "compression", "<pattern>",
        "Analyze compression options for types, fields, and custom field groups " +
        "whose name matches a specified glob-like pattern (only '*' and ',' are supported as pattern chars). " +
        "Note, that matching of pattern is performed against '<field-name>:<field-type>' string.");
    private final Option typeOverride = new Option('t', "type-override", "Override type information with defaults.");
    private final OptionString outFile = new OptionString('o', "out", "<file>", "Write output to a file.");

    private Parser parser;

    @Override
    protected Option[] getOptions() {
        return new Option[] { symbols, groups, compression, typeOverride, outFile };
    }

    @Override
    protected void executeImpl(String[] args) throws BadToolParametersException {
        if (args.length == 0)
            noArguments();
        if (args.length != 1)
            wrongNumberOfArguments();

        // analyze url
        String url = args[0];
        log.info("Analyzing " + LogUtil.hideCredentials(url));

        FileReaderParams.Default params = new FileReaderParams.Default();
        String dataFilePath = FileReader.parseParameters(url, params);
        params.setSpeed(FileReaderParams.MAX_SPEED);

        // create reader
        ConnectionStats connectionStats = new ConnectionStats();
        FileReader reader = new FileReader(dataFilePath, connectionStats, params) {
            @Override
            protected AbstractQTPParser createParser(FileFormat format, DataScheme scheme) {
                if (format != FileFormat.BINARY)
                    throw new IllegalArgumentException("File analysis supports only BINARY files");
                if (parser == null) {
                    parser = new Parser(scheme);
                    configureParser(parser);
                }
                return parser;
            }
        };

        // read file(s)
        try {
            reader.readInto(new Consumer());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (parser == null) {
            System.out.println("No files found.");
            return;
        }
        long readBytes = connectionStats.getReadBytes();
        System.out.printf(Locale.US, "%nParsed %,d bytes from %s%n", readBytes, url);

        // write report
        PrintWriter out;
        if (outFile.isSet())
            try {
                out = new PrintWriter(new FileWriter(outFile.getValue()));
            } catch (IOException e) {
                throw new BadToolParametersException("Cannot open file \"" + outFile.getValue() + "\"", e);
            }
        else
            out = new PrintWriter(System.out);
        try {
            parser.print(out, readBytes);
        } finally {
            out.close();
        }
    }

    private void configureParser(Parser parser) {
        parser.setAnalyzeSymbols(symbols.isSet());
        if (groups.isSet()) {
            Properties props;
            try {
                props = loadProps(groups.getValue());
            } catch (IOException e) {
                throw new BadToolParametersException("Cannot read groups from " + groups.getValue());
            }
            for (Object key : props.keySet()) {
                String groupName = (String) key;
                parser.defineGroup(groupName, GlobListUtil.compile(props.getProperty(groupName)));
            }
        }
        if (compression.isSet())
            parser.setAnalyzeCompression(GlobListUtil.compile(compression.getValue()));
        parser.setTypeOverride(typeOverride.isSet());
    }

    private static Properties loadProps(String fileName) throws IOException {
        Properties props = new Properties();
        InputStream in = new FileInputStream(fileName);
        try {
            props.load(in);
        } finally {
            in.close();
        }
        return props;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(FileAnalysis.class, args);
    }

    private static class Consumer extends MessageConsumerAdapter {
        Consumer() {}

        @Override
        public void handleUnknownMessage(int messageTypeId) {
            // show warning if it is really unknown
            if (MessageType.findById(messageTypeId) == null)
                super.handleUnknownMessage(messageTypeId);
        }
    }
}
