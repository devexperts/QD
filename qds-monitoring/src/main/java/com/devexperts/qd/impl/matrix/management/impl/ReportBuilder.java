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
package com.devexperts.qd.impl.matrix.management.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

class ReportBuilder {
    static final String CSV = "csv";
    static final String TEXT = "text";

    static final int HEADER_LEVEL_COLLECTOR = 1;
    static final int HEADER_LEVEL_SECTION = 2;

    private boolean html;
    private boolean csv;
    private boolean text;

    private boolean firstLine;
    private final StringWriter string = new StringWriter();
    private final PrintWriter out = new PrintWriter(string);
    private int lineCount;

    private List<List<String>> table;

    ReportBuilder(String format) {
        // html is the default format
        if (format != null && format.equalsIgnoreCase(CSV)) {
            csv = true;
        } else if (format != null && format.equalsIgnoreCase(TEXT)) {
            text = true;
        } else
            html = true;
        firstLine = true;
    }

    int getLineCount() {
        return lineCount;
    }

    @Override
    public String toString() {
        return string.toString();
    }

    ReportBuilder header(Object obj, int level) {
        if (table != null)
            throw new IllegalStateException();
        newLine();
        if (html) {
            int fontSize = level == HEADER_LEVEL_COLLECTOR ? 130 : 110;
            int paddingTop = level == HEADER_LEVEL_COLLECTOR ? 18 : 6;
            out.print("<div style='font-weight:bold; font-size:" + fontSize + "%; padding-top: " + paddingTop + "pt'>");
        }
        out.print(convert(obj));
        if (html)
            out.print("</div>");
        return this;
    }

    ReportBuilder message(Object obj) {
        if (table != null)
            throw new IllegalStateException();
        newLine();
        out.print(convert(obj));
        if (html)
            out.print("<br>");
        return this;
    }

    ReportBuilder beginTable() {
        if (table != null)
            throw new IllegalStateException();
        table = new ArrayList<>();
        return this;
    }

    ReportBuilder endTable() {
        if (table == null)
            throw new IllegalStateException();
        if (html)
            flushHtmlTable();
        else
            flushTextOrCSVTable();
        table = null;
        return this;
    }

    ReportBuilder newRow() {
        table.add(new ArrayList<String>());
        return this;
    }

    ReportBuilder endTR() {
        return this;
    }

    ReportBuilder td(Object obj) {
        table.get(table.size() - 1).add(convert(obj));
        return this;
    }

    // --------------------- private ---------------------

    private void newLine() {
        lineCount++;
        if (firstLine)
            firstLine = false;
        else
            out.println();
    }

    private String convert(Object obj) {
        String s = String.valueOf(obj);
        if (html)
            s = quoteHtml(s);
        if (csv)
            s = quoteCsv(s);
        return s;
    }

    private String quoteHtml(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            default:
                sb.append(c);
            }
        }
        s = sb.toString();
        return s;
    }

    private String quoteCsv(String s) {
        StringBuilder sb = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                sb.append("\"");
                // fall trough
            case ' ':
                quote = true;
                // fall trough
            default:
                sb.append(c);
            }
        }
        if (quote) {
            sb.insert(0, '"');
            sb.append('"');
        }
        return sb.toString();
    }

    private void flushTextOrCSVTable() {
        List<Integer> widths = new ArrayList<>();
        if (text) {
            // decorate headers
            List<String> headerRow = table.get(0);
            for (int i = 0; i < headerRow.size(); i++)
                headerRow.set(i, "[" + headerRow.get(i) + "]");
            // compute widths
            for (List<String> row : table) {
                while (widths.size() < row.size())
                    widths.add(0);
                for (int j = 0; j < row.size(); j++) {
                    String td = row.get(j);
                    if (td.length() > widths.get(j))
                        widths.set(j, td.length());
                }
            }
            // special case for 2 column table
            if (widths.size() <= 2) {
                flushTextTableInline();
                return;
            }
        }
        // actually print table
        for (List<String> row : table) {
            newLine();
            for (int j = 0; j < row.size(); j++) {
                if (j > 0)
                    out.print(text ? " " : ",");
                String td = row.get(j);
                if (text) {
                    StringBuilder sb = new StringBuilder(td);
                    while (sb.length() < widths.get(j))
                        sb.append(' ');
                    td = sb.toString();
                }
                out.print(td);
            }
        }
    }

    private void flushTextTableInline() {
        for (List<String> row : table) {
            out.print(' ');
            out.print(row.get(0));
            if (row.size() > 1) {
                out.print('=');
                out.print(row.get(1));
            }
        }
    }

    private void flushHtmlTable() {
        newLine();
        out.print("<table border=1>");
        for (int i = 0; i < table.size(); i++) {
            List<String> row = table.get(i);
            newLine();
            out.print("<tr>");
            for (String td : row) {
                out.print(i == 0 ? "<th>" : "<td>");
                out.print(td);
                out.print(i == 0 ? "</th>" : "</td>");
            }
            out.print("</tr>");
        }
        out.print("</table>");
    }
}
