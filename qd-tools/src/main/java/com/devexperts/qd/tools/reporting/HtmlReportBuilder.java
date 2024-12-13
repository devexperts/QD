/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.reporting;

import com.devexperts.annotation.Experimental;
import com.devexperts.logging.Logging;
import com.devexperts.qd.tools.launcher.Launcher;
import com.devexperts.qd.tools.module.StructuredLogging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A simple implementation of {@link ReportBuilder} used by {@link Launcher}.
 * <p>
 * FIXME: current implementation is very simple and inefficient, shall be improved later.
 */
@Experimental
public class HtmlReportBuilder implements ReportBuilder {

    private static final Logging log = Logging.getLogging(HtmlReportBuilder.class);

    // FIXME: whole current styling approach fragile and should be reworked
    static final Map<String, String> CELL_STYLES = new HashMap<>();

    static {
        // HtmlStatusReportBuilder.CELL_STYLES.put(Registry.Action.add.name(), " style=\"color:green;\"");
        HtmlReportBuilder.CELL_STYLES.put(StructuredLogging.Action.WARNING.tag(), " style=\"color:red;\"");
        // FIXME: Hub-specific styles in common class
        // HtmlStatusReportBuilder.CELL_STYLES.put(Registry.Action.remove.tag(), " style=\"color:magenta;\"");
        // HtmlStatusReportBuilder.CELL_STYLES.put(Registry.Action.passivate.tag(), " style=\"color:magenta;\"");
        // HtmlStatusReportBuilder.CELL_STYLES.put(Registry.Action.stop.tag(), " style=\"color:magenta;\"");
    }

    List<String> header = null;
    List<List<String>> contents = new ArrayList<>();

    @Override
    public ReportBuilder addHeaderRow(Object... values) {
        if (header != null)
            throw new IllegalStateException("Current implementation expects only one header row");
        header = convertValues(values);
        return this;

    }

    @Override
    public ReportBuilder addHeaderRow(List<?> values) {
        if (header != null)
            throw new IllegalStateException("Current implementation expects only one header row");
        header = convertValues(values);
        return this;
    }

    @Override
    public ReportBuilder addRow(Object... values) {
        contents.add(convertValues(values));
        return this;
    }

    @Override
    public ReportBuilder addRow(List<?> values) {
        contents.add(convertValues(values));
        return this;
    }

    public String convert(Object o) {
        return o == null ? "" : o.toString();
    }

    public String buildFilteredHtmlReport(String title, String regex) {
        if (header == null)
            throw new IllegalStateException("Current implementation expects one header row");
        List<List<String>> rows = new ArrayList<>(contents.size() + 1);
        rows.add(header);
        filter(contents, regex, rows);
        return buildHtmlReport(title, rows, getMatcher(regex));
    }

    private String buildHtmlReport(String title, List<List<String>> data, Matcher highlight) {
        // TODO: filtering can be combined with report generation
        StringBuilder sb = new StringBuilder();
        BitSet markStart = new BitSet();
        BitSet markEnd = new BitSet();
        int cols = data.stream().mapToInt(List::size).max().orElse(0);
        sb.append("<p><h1>").append(title).append("</h1>\r\n");
        sb.append("<p><table cols=").append(cols).append(" border=1 cellpadding=2 cellspacing=0 style=\"white-space:pre;\">\r\n");
        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            sb.append("<tr>");
            // noinspection ForLoopReplaceableByForEach
            for (int j = 0; j < row.size(); j++) {
                String td = row.get(j);
                if (i == 0) {
                    sb.append("<th>");
                } else {
                    sb.append("<td").append(CELL_STYLES.getOrDefault(td, "")).append(">");
                }
                markStart.clear();
                markEnd.clear();
                if (i != 0 && highlight != null) {
                    highlight.reset(td);
                    while (highlight.find()) {
                        markStart.set(highlight.start());
                        markEnd.set(highlight.end());
                    }
                }
                boolean closingCurlyBracket = false;
                for (int k = 0; k < td.length(); k++) {
                    char c = td.charAt(k);
                    if (closingCurlyBracket && c == ' ')
                        sb.append("<wbr>");
                    if (markStart.get(k))
                        sb.append("<mark>");
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
                    if (markEnd.get(k + 1))
                        sb.append("</mark>");
                    if (closingCurlyBracket && c == ',')
                        sb.append("<wbr>");
                    closingCurlyBracket = c == '}';
                }
                sb.append(i == 0 ? "</th>" : "</td>");
            }
            sb.append("</tr>\r\n");
        }
        sb.append("</table>\r\n");
        return sb.toString();
    }

    @Nullable
    private static Matcher getMatcher(String regex) {
        return (regex == null || regex.isEmpty() || regex.equals("*") || regex.equals(".*")) ? null :
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher("");
    }

    private List<String> convertValues(Object... values) {
        return values == null || values.length == 0 ? Collections.emptyList() :
            Arrays.stream(values).map(this::convert).collect(Collectors.toList());
    }

    private List<String> convertValues(List<?> values) {
        return values == null || values.isEmpty() ? Collections.emptyList() :
            values.stream().map(this::convert).collect(Collectors.toList());
    }

    private List<List<String>> filter(List<List<String>> data, String regex, List<List<String>> target) {
        Matcher m = getMatcher(regex);
        if (m == null) {
            target.addAll(data);
        } else {
            data.stream().filter(row -> row.stream().anyMatch(s -> m.reset(s).find())).forEach(target::add);
        }
        return target;
    }
}
