/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.util.*;

import com.devexperts.services.ServiceProvider;
import com.devexperts.services.Services;

/**
 * com.devexperts.tools.Help tool.
 */
@ToolSummary(
	info = "com.devexperts.tools.Help tool.",
	argString = "[<article>]",
	arguments = {
		"<article> -- help article to show."
	}
)
@ServiceProvider
public class Help extends AbstractTool {
	private static final int MIN_WIDTH = 10;
	private static final int DEFAULT_WIDTH = 120;

	private final OptionInteger widthOpt = new OptionInteger('w', "width", "<n>", "Screen width (default is " + DEFAULT_WIDTH + ")");

	private int width = DEFAULT_WIDTH;

	private static final List<Class<? extends HelpProvider>> PROVIDERS = Services.loadServiceClasses(HelpProvider.class, null);

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
			caption = (caption == null) ? word : caption + "_" + word;
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
					System.out.println(article);
				}
			}
			return;
		} else {
			String article = getArticle(caption);
			if (article == null) {
				printFormat("No help article found for \"" + caption + "\"");
			}
		}
	}

	private static SortedSet<String> getContents() {
		SortedSet<String> contents = new TreeSet<>();
		for (Class<? extends HelpProvider> provider : PROVIDERS) {
			try {
				contents.addAll(provider.newInstance().getArticles());
			} catch (InstantiationException e) {
				continue;
			} catch (IllegalAccessException e) {
				continue;
			}
		}
		return contents;
	}

	private static String getArticle(String caption) {
		for (Class<? extends HelpProvider> provider : PROVIDERS) {
			try {
				String article = provider.newInstance().getArticle(caption);
				if (article != null) {
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

	private void printFormat(String paragraph) {
		System.out.print(format(paragraph, width));
	}

	static String format(String text, int width) {
		if (width < MIN_WIDTH) {
			throw new IllegalArgumentException("Width must not be less, than " + MIN_WIDTH + ".");
		}
		text = text.replace("\t", "    ");
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
		if (r.length() != 0) {
			sb.append('\n').append(r);
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		Tools.executeSingleTool(Help.class, args);
	}
}
