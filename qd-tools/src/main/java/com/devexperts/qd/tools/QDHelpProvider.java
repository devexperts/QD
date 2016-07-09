/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

import java.beans.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.tools.*;

@ServiceProvider
public class QDHelpProvider implements HelpProvider {
	public static final int MIN_WIDTH = 10;
	public static final int DEFAULT_WIDTH = 120;

	private static final String HELP_FOLDER = "META-INF/qdshelp/";
	private static final String HELP_FILE_SUFFIX = ".txt";
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

	private int width = DEFAULT_WIDTH;

	
	@Override
	public String getArticle(String name) {
		BufferedReader reader = getHelpReader(name);
		if (reader == null) {
			AbstractTool tool = Tools.getTool(name);
			if (tool != null) {
				Class<? extends AbstractTool> toolClass = tool.getClass();
				if (toolClass.isAnnotationPresent(ToolHelpArticle.class)) {
					reader = new BufferedReader(new StringReader(toolClass.getAnnotation(ToolHelpArticle.class).value()));
				} else {
					return printCaption(tool.getClass().getSimpleName()) + tool.generateHelpSummary(width) + "\n";
				}
			} else {
				return null;
			}
		}

		try {
			String line = reader.readLine();
			line = line.substring(ARTICLE_CAPTION_MARKER.length()).trim();
			name = line;

			List<String> lines = new ArrayList<>();
			// Reading the article
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			return printArticle(name, lines);
		} catch (IOException e) {
			System.err.println("IO error occurred while reading help data:");
			e.printStackTrace();
			return null;
		} finally {
			try {
				reader.close();
			} catch (IOException ignored) {}
		}
	}

	@Override
	public List<String> getArticles() {
		ArrayList<String> captions = new ArrayList<>();
		Set<String> lowerCaptions = new HashSet<>();

		URL url = Help.class.getClassLoader().getResource(HELP_FOLDER);
		File[] helpFiles = new File(url.getPath()).listFiles(new UnspecialHelpFileFilter());

		for (File helpFile : helpFiles) {
			try (BufferedReader reader = getHelpReader(helpFile.getName())) {
				if (reader == null) {
					continue;
				}

				String caption = reader.readLine().substring(ARTICLE_CAPTION_MARKER.length()).trim();
				captions.add(caption);
				lowerCaptions.add(caption.toLowerCase(Locale.US));
			} catch (IOException e) {
				return null;
			}
		}

		for (String tool : Tools.getToolNames()) {
			if (!lowerCaptions.contains(tool.toLowerCase(Locale.US))) {
				captions.add(tool);
			}
		}
		Collections.sort(captions);
		return captions;
	}

	@Override
	public String getMetaTag(String name) {
		switch (name) {
		case "list-connectors":
			return listConnectors();
		case "list-specific-filters":
			return listSpecificFilters();
		case "list-tools":
			return listAllTools(width);
		default:
			return null;
		}
	}


	private static BufferedReader getHelpReader(String caption) {
		caption = caption.toLowerCase(Locale.US);
		if (!caption.endsWith(HELP_FILE_SUFFIX)) {
			caption += HELP_FILE_SUFFIX;
		}
		URL contentLink = Help.class.getResource("/" + HELP_FOLDER + caption.replaceAll(" ", "_"));
		try {
			return (contentLink != null) ? new BufferedReader(new InputStreamReader(contentLink.openStream())) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private String printCaption(String caption) {
		return caption + "\n" + caption.replaceAll(".", "=") + "\n";
	}

	private String printArticle(String caption, List<String> lines) {
		StringBuilder small = new StringBuilder();
		StringBuilder big = new StringBuilder();
		big.append(printCaption(caption));
		for (String line : lines) {
			if (line.trim().equalsIgnoreCase(TOOL_SUMMARY_MARKER)) {
				flush(small, big);
				AbstractTool tool = Tools.getTool(caption);
				if (tool == null) {
					printFormat("--- Couldn't generate \"" + caption + "\" tool summary ---", big);
				} else {
					big.append(tool.generateHelpSummary(width));
				}
			} else if (line.trim().equalsIgnoreCase(MESSAGECONNECTOR_SUMMARY_MARKER)) {
				flush(small, big);
				Class<? extends MessageConnector> connector = MessageConnectors.findMessageConnector(caption + CONNECTOR_SUFFIX, getHelpClassLoader());
				if (connector == null) {
					printFormat("--- Couldn't find connector \"" + caption + "\" ---", big);
				} else {
					printMessageConnectorHelpSummary(connector, big);
				}
			} else if (line.trim().equalsIgnoreCase(LIST_SPECIFIC_FILTERS_MARKER)) {
				flush(small, big);
				big.append(listSpecificFilters()).append("\n");
			} else if (line.trim().equalsIgnoreCase(LIST_TOOLS)) {
				flush(small, big);
				// List all tools
				big.append(listAllTools(width)).append("\n");
			} else if (line.trim().equalsIgnoreCase(LIST_CONNECTORS)) {
				flush(small, big);
				big.append(listConnectors()).append("\n");
			} else if (line.trim().isEmpty()) {
				// New paragraph
				flush(small, big);
				big.append("\n");
			} else if (!line.trim().startsWith(COMMENT_MARKER) && !line.trim().startsWith(TODO_MARKER)) {
				if (line.startsWith("\t") || line.startsWith(" ")) {
					flush(small, big);
					small.append('\t');
				} else if (small.length() > 0) {
					small.append(' ');
				}
				small.append(line.trim());
			}
		}
		flush(small, big);
		return big.toString();
	}

	private void flush(StringBuilder in, StringBuilder out) {
		if (in.length() != 0) {
			printFormat(in.toString(), out);
			in.setLength(0);
		}
	}

	private void printFormat(String paragraph, StringBuilder out) {
		 out.append(format(paragraph, width));
	}

	private static String format(String text, int width) {
		if (width < MIN_WIDTH) {
			throw new IllegalArgumentException("Width must not be less, than " + MIN_WIDTH + ".");
		}
		text = replaceSpecialMarkers(text);
		String[] paragraphs = text.split("\n");
		StringBuilder sb = new StringBuilder();
		for (String p : paragraphs) {
			sb.append(formatParagraph(p, width)).append("\n");
		}
		return sb.toString();
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
			if (sb.length() > 0) {
				sb.append('\n');
			}
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
			sb.append("\"com.devexperts.tools.Help ").append(linkText).append("\"");
			pos = matcher.end();
		}
		sb.append(text.substring(pos));
		text = sb.toString();

		text = text.replace("\t", "    "); // in order to be able to measure line width correctly
		text = text.replace(LINEBREAK_MARKER, "\n");
		text = text.replace(AT_MARKER, "@");
		return text;
	}

	private void printMessageConnectorHelpSummary(Class<? extends MessageConnector> connector, StringBuilder out) {
		MessageConnectorSummary annotation = connector.getAnnotation(MessageConnectorSummary.class);
		if (annotation == null) {
			printFormat("\t--- No annotation found for connector \"" + getConnectorName(connector) + "\" ---", out);
			return;
		}
		printFormat("\t" + getConnectorName(connector) + " - " + annotation.info(), out);
		out.append("\n");
		printFormat("Address format: " + annotation.addressFormat(), out);
		out.append("\n");
		Map<String, PropertyDescriptor> properties = getAnnotatedConnectorProperties(connector);
		if (properties.isEmpty()) {
			printFormat("This connector has no special properties.", out);
		} else try {
			printFormat("Properties:", out);
			ArrayList<String[]> table = new ArrayList<>();
			table.add(new String[]{"  ", "[type]", "[name]", "[description]"});
			for (PropertyDescriptor pd : properties.values()) {
				String name = pd.getName();
				String desc = pd.getWriteMethod().getAnnotation(MessageConnectorProperty.class).value();
				String type = pd.getPropertyType().getSimpleName();
				table.add(new String[]{"", type, name, desc});
			}
			out.append(formatTable(table, width, "  ")).append("\n");
		} catch (IllegalArgumentException e) {
			printFormat("\t--- Error occurred while generating properties information ---", out);
		}
	}

	private String printSubscriptionFilters(QDFilterFactory filterFactory) {
		ArrayList<String[]> table = new ArrayList<>();
		table.add(new String[]{"[name]", "[description]"});
		if (filterFactory != null) {
			for (Map.Entry<String, String> entry : filterFactory.describeFilters().entrySet()) {
				if (entry.getValue().length() > 0) {
					table.add(new String[]{entry.getKey(), entry.getValue()});
				}
			}
		}
		if (table.size() > 1) {
			return formatTable(table, width, "  ");
		} else {
			return format("--- No project-specific filters found ---", width);
		}
	}

	private static String listAllTools(int width) {
		ArrayList<String[]> table = new ArrayList<>();
		for (String toolName : Tools.getToolNames()) {
			Class<? extends AbstractTool> toolClass = Tools.getTool(toolName).getClass();
			ToolSummary annotation = toolClass.getAnnotation(ToolSummary.class);
			String toolDescription = (annotation == null) ?
				"" :
				annotation.info();
			table.add(new String[]{"   ", toolName, "-", toolDescription});
		}
		return formatTable(table, width, " ");
	}

	private ClassLoader getHelpClassLoader() {
		return Help.class.getClassLoader();
	}

	private String getConnectorName(Class<? extends MessageConnector> connector) {
		String name = connector.getSimpleName();
		if (name.endsWith(CONNECTOR_SUFFIX)) {
			name = name.substring(0, name.length() - CONNECTOR_SUFFIX.length());
		}
		return name;
	}

	static String formatTable(List<String[]> rows, int screenWidth, String separator) {
		if (rows.isEmpty()) {
			return "";
		}
		int n = rows.get(0).length;
		int[] w = new int[n];
		for (String[] row : rows) {
			if (row.length != n) {
				throw new IllegalArgumentException("Rows in a table have different lengths");
			}
			for (int i = 0; i < n; i++) {
				int l = row[i].length();
				if (w[i] < l) {
					w[i] = l;
				}
			}
		}

		int totalWidth = separator.length() * (n - 1);
		for (int wid : w) {
			totalWidth += wid;
		}
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
					} else {
						result.append(empty);
					}
					result.append(s).append('\n');
				}
			}
		}
		return result.toString();
	}

	private Map<String, PropertyDescriptor> getAnnotatedConnectorProperties(Class<? extends MessageConnector> connector) {
		Map<String, PropertyDescriptor> result = new TreeMap<>();
		try {
			BeanInfo bi = Introspector.getBeanInfo(connector);
			for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
				Method wm = pd.getWriteMethod();
				if (wm != null && wm.getAnnotation(MessageConnectorProperty.class) != null) {
					result.put(pd.getName(), pd);
				}
			}
		} catch (IntrospectionException e) {
			// just ignore a return empty result
		}
		return result;
	}

	private String listConnectors() {
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
		return formatTable(table, width, "  ");
	}

	private String listSpecificFilters() {
		return printSubscriptionFilters(QDFactory.getDefaultScheme().getService(QDFilterFactory.class));
	}
}
