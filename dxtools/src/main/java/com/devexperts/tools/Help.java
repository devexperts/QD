/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devexperts.services.ServiceProvider;
import com.devexperts.services.Services;

/**
 * com.devexperts.tools.Help tool.
 */
@ToolSummary(
	info = "Main Help tool.",
	argString = "[<article>]",
	arguments = {
		"<article> -- help article to show."
	}
)
@ServiceProvider
public class Help extends AbstractTool {
	public static final int MIN_WIDTH = 10;
	public static final int DEFAULT_WIDTH = 120;

	private static final char SPECIAL_SYMBOL = '@';

	private static final String COMMENT_MARKER = SPECIAL_SYMBOL + "#";
	private static final String TODO_MARKER = SPECIAL_SYMBOL + "todo";
	private static final String LIST_TOOLS = SPECIAL_SYMBOL + "list-tools";

	private static final String AT_MARKER = SPECIAL_SYMBOL + "at;"; // to be replaced with @
	private static final String LINK_MARKER = SPECIAL_SYMBOL + "link";
	private static final String LINEBREAK_MARKER = SPECIAL_SYMBOL + "br;";

	private final OptionInteger widthOpt = new OptionInteger('w', "width", "<n>", "Screen width (default is " + DEFAULT_WIDTH + ")");

	private int width = DEFAULT_WIDTH;

	private static final List<Class<? extends HelpProvider>> PROVIDERS = Services.loadServiceClasses(HelpProvider.class, null);

	private HelpProvider finder;

	@Override
	protected Option[] getOptions() {
		return new Option[] { widthOpt };
	}

	@Override
	protected void executeImpl(String[] args) {
		if (args.length == 0)
			args = new String[] {"com.devexperts.tools.Help"};

		if (widthOpt.isSet()) {
			width = widthOpt.getValue();
			if (width < MIN_WIDTH)
				throw new BadToolParametersException("Screen width must not be less, than " + MIN_WIDTH);
		}

		String caption = null;
		for (String word : args) {
			caption = (caption == null) ? word : caption + " " + word;
		}
		showArticle(caption.trim());
	}

	private void showArticle(String caption) {
		if (caption.equalsIgnoreCase("Contents")) {
			System.out.println("Help articles:");
			for (String caption1 : getContents()) {
				System.out.println("    " + caption1);
			}
			return;
		} else if (caption.equalsIgnoreCase("All")) {
			SortedSet<String> captions = getContents();
			boolean first = true;
			for (String caption1 : captions) {
				if (!first) {
					printArticleSeparator();
				}
				String article = getArticle(caption1);
				if (article == null) {
					System.err.println("Couldn't print an article: " + caption1);
				} else {
					printArticle(article);
					printArticleSeparator();
				}
			}
			return;
		} else {
			String article = getArticle(caption);
			if (article == null) {
				printFormat("No help article found for \"" + caption + "\"", System.out, width);
			} else {
				printArticle(article);
			}
		}
	}

	private void printArticle(String article) {
		String[] lines = article.split("\n");
		String caption = lines[0];
		printCaption(caption);

		StringBuilder small = new StringBuilder();
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.equalsIgnoreCase(LIST_TOOLS)) {
				flush(small);
				System.out.print(listAllTools(width));
			} else if (line.isEmpty()) {
				flush(small);
				System.out.println();
			} else if (!line.startsWith(COMMENT_MARKER) && !line.startsWith(TODO_MARKER)) {
				if (line.startsWith("" + SPECIAL_SYMBOL)) {
					String specialLine = finder.getMetaTag(line.substring(1), caption, width);
					if (specialLine != null) {
						flush(small);
						System.out.print(specialLine);
						continue;
					}
				}
				if (lines[i].startsWith("\t") || lines[i].startsWith(" ")) {
					flush(small);
					small.append('\t');
				} else if (small.length() > 0) {
					small.append(' ');
				}
				small.append(line);
			}
		}
		flush(small);
	}

	private void flush(StringBuilder in) {
		if (in.length() != 0) {
			printFormat(in.toString(), System.out, width);
			in.setLength(0);
		}
	}

	private void printCaption(String caption) {
		System.out.println(caption);
		System.out.println(caption.replaceAll(".", "="));
	}

	static String listAllTools(int width) {
		ArrayList<String[]> table = new ArrayList<>();
		for (String toolName : Tools.getToolNames()) {
			Class<? extends AbstractTool> toolClass = Tools.getTool(toolName).getClass();
			if (!toolClass.isAnnotationPresent(ToolHelpArticle.class) && !toolClass.isAnnotationPresent(ToolSummary.class)) {
				continue;
			}
			ToolSummary annotation = toolClass.getAnnotation(ToolSummary.class);
			String toolDescription = (annotation == null) ?
				"" :
				annotation.info();
			table.add(new String[]{"   ", toolName, "-", toolDescription});
		}
		return formatTable(table, width, " ");
	}

	private static SortedSet<String> getContents() {
		SortedSet<String> captions = new TreeSet<>();
		for (Class<? extends HelpProvider> provider : PROVIDERS) {
			try {
				captions.addAll(provider.newInstance().getArticles());
			} catch (InstantiationException e) {
				continue;
			} catch (IllegalAccessException e) {
				continue;
			}
		}
		return captions;
	}

	private String getArticle(String caption) {
		for (Class<? extends HelpProvider> providerClass : PROVIDERS) {
			try {
				HelpProvider provider = providerClass.newInstance();
				String article = provider.getArticle(caption);
				if (article != null) {
					this.finder = provider;
					return article;
				}
			} catch (InstantiationException e) {
				continue;
			} catch (IllegalAccessException e) {
				continue;
			}
		}
		return null;
	}

	private void printArticleSeparator() {
		char[] sep = new char[width];
		Arrays.fill(sep, '-');
		String articleSeparator = new String(sep);

		System.out.println();
		System.out.println(articleSeparator);
		System.out.println();
	}

	public static void printFormat(String paragraph, PrintStream out, int width) {
		out.print(format(paragraph, width));
	}

	public static String format(String text, int width) {
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
		if (r.length() != 0) {
			sb.append('\n').append(r);
		}
		return sb.toString();
	}

	/**
	 * Formats a table by given screen width.
	 * @param rows rows of a table.
	 * @param screenWidth screen width
	 * @param separator string used to separate columns
	 * @return a String with formatted table.
	 * @throws IllegalArgumentException if rows have different lengths.
	 */
	public static String formatTable(List<String[]> rows, int screenWidth, String separator) {
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

	public static void main(String[] args) {
		Tools.executeSingleTool(Help.class, args);
	}
}
