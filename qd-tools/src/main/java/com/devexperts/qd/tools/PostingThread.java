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

import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.qtp.BuiltinFields;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.text.TextQTPParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

class PostingThread extends Thread {

    private static final Logging log = Logging.getLogging(PostingThread.class);

    private final DataScheme scheme;
    private final PostMessageQueue queue;
    private final ChunkedInput input = new ChunkedInput();
    private final TextQTPParser parser;

    PostingThread(DataScheme scheme, PostMessageQueue queue, MessageType defaultType) {
        super("Post");
        this.scheme = scheme;
        this.queue = queue;
        this.parser = new TextQTPParser(scheme, defaultType);
        this.parser.setInput(input);
    }

    @Override
    public void run() {
        try {
            processConsole();
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        }
    }

    private static void consoleHelp() {
        System.err.println("Type one of the following commands on console:");
        System.err.println("  ?               - Print this help message ");
        System.err.println("  ? *             - List all record names in data scheme");
        System.err.println("  ? <record> ...  - List fields for specified record(s)");
        System.err.println("  #<comment>      - Ignore this line");
        System.err.println("  .               - Quit immediately (do not wait for delivery of data)");
        System.err.println("  <record> <symbol> [<fields>]");
        System.err.println("                  - Post specified record");
        System.err.println("                    Omitted fields at the end are filled with default values");
        System.err.println("  ==<data type>   - Change posted data type (RAW data by default). Possible values:");
        System.err.println("                    QD_TICKER_DATA, QD_STREAM_DATA, QD_HISTORY_DATA, QD_RAW_DATA");
        System.err.println("  =<record> Symbol <fields>");
        System.err.println("                  - Redescribe the record");
        System.err.println("                    <fields> are new record's field names");
    }

    private static void helpRecordNotFound(String name) {
        System.err.println("Record " + name + " is not found. Use '?' for help.");
    }

    private void processConsole() throws InterruptedException {
        log.info("Type '?' on console for help");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                processLine(line);
            }
        } catch (IOException e ) {
            log.error("Unexpected IOException in 'Post' tool", e);
        }

        queue.waitDone();
    }

    private void processLine(String line) {
        line = line.trim();
        if (line.startsWith(".")) {
            System.exit(0);
        } else if (line.startsWith("?")) {
            line = line.substring(1);
            processHelp(line);
        } else {
            StringBuilder sb = new StringBuilder(line.length() + 1);
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c >= 0x80)
                    sb.append("\\u").append(Integer.toString(c + 65536, 16).toUpperCase().substring(1));
                else
                    sb.append(c);
            }
            sb.append('\n');
            byte[] bytes = sb.toString().getBytes();
            input.addToInput(Chunk.wrap(bytes, 0, bytes.length, this), this);
            parser.parse(queue);
        }
    }

    private void processHelp(String line) {
        StringTokenizer st = new StringTokenizer(line);
        if (!st.hasMoreTokens()) {
            consoleHelp();
        } else {
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (token.equals("*"))
                    recordsList();
                else
                    recordInfo(token);
            }
        }
    }

    private void recordsList() {
        System.out.print("Available records are: ");
        int n = scheme.getRecordCount();
        for (int i = 0; i < n; i++) {
            if (i > 0)
                System.out.print(", ");
            System.out.print(scheme.getRecord(i).getName());
        }
        System.out.println();
    }

    private void recordInfo(String name) {
        DataRecord record = scheme.findRecordByName(name);
        if (record == null) {
            helpRecordNotFound(name);
            return;
        }
        System.out.print(record.getName() + "\t" + BuiltinFields.EVENT_SYMBOL_FIELD_NAME);
        for (int i = 0; i < record.getIntFieldCount(); i++)
            if (!(record.getIntField(i) instanceof VoidIntField))
                System.out.print("\t" + record.getIntField(i).getPropertyName());
        for (int i = 0; i < record.getObjFieldCount(); i++)
            System.out.print("\t" + record.getObjField(i).getPropertyName());
        System.out.println();
    }

}
