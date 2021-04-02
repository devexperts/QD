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

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.ServiceProvider;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Help tool.
 */
@ToolSummary(
    info = "Help tool.",
    argString = "[<article>]",
    arguments = {
        "<article> -- help article to show."
    }
)
@ServiceProvider
public class Help extends AbstractTool {
    public static final int MIN_WIDTH = 10;
    public static final int DEFAULT_WIDTH = 120;

    private static final String HELP_DATA_RESOURCE = "qdshelp.txt";
    private static final char SPECIAL_SYMBOL = '@';

    private static final String COMMENT_MARKER = SPECIAL_SYMBOL + "#";
    private static final String TODO_MARKER = SPECIAL_SYMBOL + "todo";
    private static final String ARTICLE_CAPTION_MARKER = SPECIAL_SYMBOL + "article";
    private static final String TOOL_SUMMARY_MARKER = SPECIAL_SYMBOL + "tool-summary";
    private static final String MESSAGECONNECTOR_SUMMARY_MARKER = SPECIAL_SYMBOL + "messageconnector-summary";
    private static final String LIST_SPECIFIC_FILTERS_MARKER = SPECIAL_SYMBOL + "list-specific-filters";
    private static final String LIST_TOOLS = SPECIAL_SYMBOL + "list-tools";
    private static final String LIST_CONNECTORS = SPECIAL_SYMBOL + "list-connectors";
    private static final String CONNECTOR_SUFFIX = "Connector";

    private static final String AT_MARKER = SPECIAL_SYMBOL + "at;"; // to be replaced with @
    private static final String LINK_MARKER = SPECIAL_SYMBOL + "link";
    private static final String LINEBREAK_MARKER = SPECIAL_SYMBOL + "br;";

    private final OptionInteger widthOpt = new OptionInteger('w', "width", "<n>", "Screen width (default is " + DEFAULT_WIDTH + ")");

    private int width = DEFAULT_WIDTH;

    @Override
    protected Option[] getOptions() {
        return new Option[] { widthOpt };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            args = new String[] {"Help"};

        if (widthOpt.isSet()) {
            width = widthOpt.getValue();
            if (width < MIN_WIDTH)
                throw new BadToolParametersException("Screen width must not be less, than " + MIN_WIDTH);
        }

        String caption = null;
        for (String word : args) {
            caption = (caption == null) ? word : caption + " " + word;
        }
        BufferedReader dataReader = new BufferedReader(new InputStreamReader(getHelpData()));
        try {
            showArticle(dataReader, caption);
        } finally {
            try {
                dataReader.close();
            } catch (IOException ignored) {}
        }
    }

    private static InputStream getHelpData() {
        InputStream content = Help.class.getResourceAsStream(HELP_DATA_RESOURCE);
        if (content == null)
            throw new RuntimeException("Couldn't find the help data resource.");
        return content;
    }

    private void showArticle(BufferedReader reader, String caption) {
        if (caption.equalsIgnoreCase("Contents")) {
            printHelpContents(reader);
            return;
        }
        if (caption.equalsIgnoreCase("All")) {
            printAllArticles(reader);
            return;
        }

        try {
            // Skip until caption met
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    AbstractTool tool = Tools.getTool(caption);
                    if (tool != null) {
                        printCaption(caption);
                        System.out.print(tool.generateHelpSummary(width));
                    } else
                        printFormat("No help article found for \"" + caption + "\"");
                    return;
                }
                if (line.startsWith(ARTICLE_CAPTION_MARKER)) {
                    line = line.substring(ARTICLE_CAPTION_MARKER.length()).trim();
                    if (line.equalsIgnoreCase(caption)) {
                        caption = line;
                        break;
                    }
                }
            }

            List<String> lines = new ArrayList<>();
            // Reading the article
            while (true) {
                String line = reader.readLine();
                if ((line == null) || (line.startsWith(ARTICLE_CAPTION_MARKER)))
                    break;
                lines.add(line);
            }
            printArticle(caption, lines);
        } catch (IOException e) {
            System.err.println("IO error occurred while reading help data:");
            e.printStackTrace();
        }
    }

    private void printAllArticles(BufferedReader reader) {
        try {
            char[] sep = new char[width];
            Arrays.fill(sep, '-');
            String articleSeparator = new String(sep);

            String caption = null;
            List<String> lines = new ArrayList<>();
            do {
                lines.clear();
                String newCaption;
                while (true) {
                    String line = reader.readLine();
                    if ((line == null) || (line.startsWith(ARTICLE_CAPTION_MARKER))) {
                        newCaption = (line == null) ?
                            null :
                            line.substring(ARTICLE_CAPTION_MARKER.length()).trim();
                        break;
                    }
                    lines.add(line);
                }
                if (caption != null)
                    printArticle(caption, lines);
                caption = newCaption;

                System.out.println();
                System.out.println(articleSeparator);
                System.out.println();
            } while (caption != null);
        } catch (IOException e) {
            System.err.println("IO error occurred while reading help data:");
            e.printStackTrace();
        }
    }

    private static class PropDescContainer {
        private final PropertyDescriptor propertyDescriptor;
        private final String deprecated;

        PropDescContainer(PropertyDescriptor propertyDescriptor, String deprecated) {
            this.propertyDescriptor = propertyDescriptor;
            this.deprecated = deprecated;
        }
    }

    private void printMessageConnectorHelpSummary(Class<? extends MessageConnector> connector) {
        MessageConnectorSummary annotation = connector.getAnnotation(MessageConnectorSummary.class);
        if (annotation == null) {
            printFormat("\t--- No annotation found for connector \"" + getConnectorName(connector) + "\" ---");
            return;
        }
        printFormat("\t" + getConnectorName(connector) + " - " + annotation.info());
        System.out.println();
        printFormat("Address format: " + annotation.addressFormat());
        System.out.println();
        Map<String, PropDescContainer> properties = getAnnotatedConnectorProperties(connector);
        if (properties.isEmpty()) {
            printFormat("This connector has no special properties.");
        } else try {
            printFormat("Properties:");
            ArrayList<String[]> table = new ArrayList<>();
            table.add(new String[]{"  ", "[type]", "[name]", "[description]"});
            List<String[]> deprecatedProperties = new ArrayList<>();
            deprecatedProperties.add(new String[]{"  ", "[type]", "[name]", "[description]"});
            for (PropDescContainer prop : properties.values()) {
                String name = prop.propertyDescriptor.getName();
                String desc = prop.propertyDescriptor.getWriteMethod().getAnnotation(MessageConnectorProperty.class).value();
                String type = prop.propertyDescriptor.getPropertyType().getSimpleName();
                String deprecated = prop.deprecated;
                if (deprecated.isEmpty())
                    table.add(new String[]{"", type, name, desc});
                else
                    deprecatedProperties.add(new String[]{"", type, name, desc + "\n" + deprecated});
            }
            System.out.println(formatTable(table, width, "  "));
            if (deprecatedProperties.size() > 1) {
                System.out.println("\nDeprecated properties:");
                System.out.println(formatTable(deprecatedProperties, width, "  "));
            }
        } catch (IllegalArgumentException e) {
            printFormat("\t--- Error occurred while generating properties information ---");
        }
    }

    private Map<String, PropDescContainer> getAnnotatedConnectorProperties(Class<? extends MessageConnector> connector) {
        Map<String, PropDescContainer> result = new TreeMap<>();
        try {
            BeanInfo bi = Introspector.getBeanInfo(connector);
            for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
                Method wm = pd.getWriteMethod();
                if (wm != null) {
                    MessageConnectorProperty annotation = wm.getAnnotation(MessageConnectorProperty.class);
                    if (annotation != null)
                        result.put(pd.getName(), new PropDescContainer(pd, annotation.deprecated()));
                }
            }
        } catch (IntrospectionException e) {
            // just ignore a return empty result
        }
        return result;
    }

    private void printHelpContents(BufferedReader reader) {
        // List all help article names
        ArrayList<String> captions = new ArrayList<>();
        Set<String> lowerCaptions = new HashSet<>();
        try {
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                if (line.startsWith(ARTICLE_CAPTION_MARKER)) {
                    line = line.substring(ARTICLE_CAPTION_MARKER.length()).trim();
                    captions.add(line);
                    lowerCaptions.add(line.toLowerCase(Locale.US));
                }
            }
        } catch (IOException e) {
            System.err.println("IO error occurred while reading help data:");
            e.printStackTrace();
        }
        // Add all tool names to the list if specific article not found.
        for (String tool : Tools.getToolNames())
            if (!lowerCaptions.contains(tool.toLowerCase(Locale.US)))
                captions.add(tool);
        Collections.sort(captions);
        printFormat("Help articles:");
        for (String s : captions)
            System.out.println("    " + s);
    }

    private void printArticle(String caption, List<String> lines) {
        printCaption(caption);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith(COMMENT_MARKER))
                continue;
            if (line.trim().startsWith(TODO_MARKER))
                continue;
            if (line.trim().equalsIgnoreCase(TOOL_SUMMARY_MARKER)) {
                flush(sb);
                AbstractTool tool = Tools.getTool(caption);
                if (tool == null) {
                    printFormat("--- Couldn't generate \"" + caption + "\" tool summary ---");
                    continue;
                }
                String summary = tool.generateHelpSummary(width);
                System.out.print(summary);
                continue;
            }
            if (line.trim().equalsIgnoreCase(MESSAGECONNECTOR_SUMMARY_MARKER)) {
                flush(sb);
                Class<? extends MessageConnector> connector = MessageConnectors.findMessageConnector(caption + CONNECTOR_SUFFIX, getHelpClassLoader());
                if (connector == null)
                    printFormat("--- Couldn't find connector \"" + caption + "\" ---");
                else
                    printMessageConnectorHelpSummary(connector);
                continue;
            }
            if (line.trim().equalsIgnoreCase(LIST_SPECIFIC_FILTERS_MARKER)) {
                flush(sb);
                printSubscriptionFilters(QDFactory.getDefaultScheme().getService(QDFilterFactory.class));
                continue;
            }
            if (line.trim().equalsIgnoreCase(LIST_TOOLS)) {
                flush(sb);
                // List all tools
                listAllTools(System.out, width);
                continue;
            }
            if (line.trim().equalsIgnoreCase(LIST_CONNECTORS)) {
                flush(sb);
                ArrayList<String[]> table = new ArrayList<>();
                table.add(new String[]{"  ", "[name]", "[address format]", "[description]"});
                for (Class<? extends MessageConnector> connector : MessageConnectors.listMessageConnectors(getHelpClassLoader())) {
                    MessageConnectorSummary annotation = connector.getAnnotation(MessageConnectorSummary.class);
                    String name = getConnectorName(connector);
                    String address = "";
                    String description = "";
                    if (annotation != null) {
                        address = annotation.addressFormat();
                        description = annotation.info();
                    }
                    table.add(new String[]{"", name, address, description});
                }
                System.out.println(formatTable(table, width, "  "));
                continue;
            }
            if (line.trim().isEmpty()) {
                // New paragraph
                flush(sb);
                System.out.println();
                continue;
            }
            if (line.startsWith("\t") || line.startsWith(" ")) {
                flush(sb);
                sb.append('\t');
            } else if (sb.length() > 0)
                sb.append(' ');
            sb.append(line.trim());
        }
        flush(sb);
    }

    static void listAllTools(PrintStream out, int width) {
        ArrayList<String[]> table = new ArrayList<>();
        for (String toolName : Tools.getToolNames()) {
            Class<? extends AbstractTool> toolClass = Tools.getTool(toolName).getClass();
            ToolSummary annotation = toolClass.getAnnotation(ToolSummary.class);
            String toolDescription = (annotation == null) ?
                "" :
                annotation.info();
            table.add(new String[]{"   ", toolName, "-", toolDescription});
        }
        out.print(formatTable(table, width, " "));
    }

    private String getConnectorName(Class<? extends MessageConnector> connector) {
        String name = connector.getSimpleName();
        if (name.endsWith(CONNECTOR_SUFFIX))
            name = name.substring(0, name.length() - CONNECTOR_SUFFIX.length());
        return name;
    }

    private void printCaption(String caption) {
        System.out.println(caption);
        System.out.println(caption.replaceAll(".", "="));
    }

    private void printSubscriptionFilters(QDFilterFactory filterFactory) {
        ArrayList<String[]> table = new ArrayList<>();
        table.add(new String[]{"[name]", "[description]"});
        if (filterFactory != null)
            for (Map.Entry<String, String> entry : filterFactory.describeFilters().entrySet())
                if (entry.getValue().length() > 0)
                    table.add(new String[] { entry.getKey(), entry.getValue() });
        if (table.size() > 1)
            System.out.println(formatTable(table, width, "  "));
        else
            printFormat("--- No project-specific filters found ---");
    }

    private void flush(StringBuilder sb) {
        if (sb.length() != 0) {
            printFormat(sb.toString());
            sb.setLength(0);
        }
    }

    private void printFormat(String paragraph) {
        System.out.print(format(paragraph, width));
    }

    /**
     * Formats a table by given screen width.
     * @param rows rows of a table.
     * @param screenWidth screen width
     * @param separator string used to separate columns
     * @return a String with formatted table.
     * @throws IllegalArgumentException if rows have different lengths.
     */
    static String formatTable(List<String[]> rows, int screenWidth, String separator) {
        if (rows.isEmpty())
            return "";
        int n = rows.get(0).length;
        int[] w = new int[n];
        for (String[] row : rows) {
            if (row.length != n)
                throw new IllegalArgumentException("Rows in a table have different lengths");
            for (int i = 0; i < n; i++) {
                int l = row[i].length();
                if (w[i] < l)
                    w[i] = l;
            }
        }

        int totalWidth = separator.length() * (n - 1);
        for (int wid : w)
            totalWidth += wid;
        int allExceptLastWidth = totalWidth - w[n - 1];

        StringBuilder result = new StringBuilder();
        if (screenWidth - allExceptLastWidth < MIN_WIDTH) {
            // Width is too small. Don't try to format it beautifully.
            StringBuilder sb = new StringBuilder();
            for (String[] row : rows) {
                sb.setLength(0);
                for (String cell : row) {
                    if (sb.length() != 0)
                        sb.append(separator);
                    sb.append(cell);
                }
                result.append(format(sb.toString(), screenWidth));
            }
        } else {
            int lastColumnWidth = screenWidth - allExceptLastWidth;

            char[] c = new char[allExceptLastWidth];
            Arrays.fill(c, ' ');
            int pos = 0;
            char[] sepChars = separator.toCharArray();
            for (int i = 0; i < n - 1; i++) {
                pos += w[i];
                System.arraycopy(sepChars, 0, c, pos, sepChars.length);
                pos += sepChars.length;
            }
            String empty = String.valueOf(c);

            for (String[] row : rows) {
                Arrays.fill(c, ' ');
                pos = 0;
                for (int i = 0; i < n - 1; i++) {
                    char[] cell = row[i].toCharArray();
                    System.arraycopy(cell, 0, c, pos, cell.length);
                    pos += w[i];
                    System.arraycopy(sepChars, 0, c, pos, sepChars.length);
                    pos += sepChars.length;
                }

                String[] lastCell = format(row[n - 1], lastColumnWidth).split("\n");
                boolean first = true;
                for (String s : lastCell) {
                    if (first) {
                        result.append(c);
                        first = false;
                    } else
                        result.append(empty);
                    result.append(s).append('\n');
                }
            }
        }
        return result.toString();
    }

    /**
     * Formats the text with given screen width and performs some
     * other prepossessing (in particular processes '@link{...}').
     *
     * @param text text to format.
     * @param width screen width.
     * @return formatted text.
     */
    static String format(String text, int width) {
        if (width < MIN_WIDTH)
            throw new IllegalArgumentException("Width must not be less, than " + MIN_WIDTH + ".");
        text = replaceSpecialMarkers(text);
        String[] paragraphs = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String p : paragraphs)
            sb.append(formatParagraph(p, width)).append("\n");
        return sb.toString();
    }

    private static String replaceSpecialMarkers(String text) {
        // process '@link{...}'
        StringBuilder sb = new StringBuilder();
        Pattern pattern = Pattern.compile(LINK_MARKER + "\\{[^\\}]*\\}");
        Matcher matcher = pattern.matcher(text);
        int pos = 0;
        while (matcher.find()) {
            sb.append(text.substring(pos, matcher.start()));
            String linkText = matcher.group().substring(LINK_MARKER.length() + 1);
            linkText = linkText.substring(0, linkText.length() - 1);
            sb.append("\"Help ").append(linkText).append("\"");
            pos = matcher.end();
        }
        sb.append(text.substring(pos));
        text = sb.toString();

        text = text.replace("\t", "    "); // in order to be able to measure line width correctly
        text = text.replace(LINEBREAK_MARKER, "\n");
        text = text.replace(AT_MARKER, "@");
        return text;
    }

    private static String formatParagraph(String paragraph, int width) {
        if (paragraph.length() <= width) {
            return paragraph;
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        char[] p = paragraph.toCharArray();
        while (p.length - i > width) {
            int j = i + width;
            boolean longWord = false;
            while (p[j] != ' ') {
                j--;
                if (j == i) {
                    longWord = true;
                    break;
                }
            }
            if (sb.length() > 0)
                sb.append('\n');
            if (longWord) {
                sb.append(p, i, width);
                i = i + width;
            } else {
                sb.append(p, i, j - i);
                i = j + 1;
            }
        }
        String r = new String(p, i, p.length - i).trim();
        if (r.length() != 0)
            sb.append('\n').append(r);
        return sb.toString();
    }

    private ClassLoader getHelpClassLoader() {
        return Help.class.getClassLoader();
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Help.class, args);
    }
}
