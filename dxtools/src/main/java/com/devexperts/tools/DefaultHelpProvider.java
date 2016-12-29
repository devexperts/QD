/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.io.*;
import java.util.*;

import com.devexperts.services.Services;

public abstract class DefaultHelpProvider implements HelpProvider {
	private static final String HELP_LOCAL_PREFIX = "META-INF/help";
	private static final String HELP_FILE_SUFFIX = ".txt";
	private static final char SPECIAL_SYMBOL = '@';

	private static final String ARTICLE_CAPTION_MARKER = SPECIAL_SYMBOL + "article";
	private static final String TOOL_SUMMARY_MARKER = SPECIAL_SYMBOL + "tool-summary";

	protected final String helpPrefix;
	protected final List<Class<? extends AbstractTool>> tools;

	protected DefaultHelpProvider() {
		helpPrefix = getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + HELP_LOCAL_PREFIX;
		tools = Services.loadLocalServiceClasses(AbstractTool.class, getClass(), null);
	}

	@Override
	public String getArticle(String name) {
		BufferedReader reader = getHelpReader(name);
		if (reader == null) {
			AbstractTool tool = getTool(name);
			if (tool != null) {
				Class<? extends AbstractTool> toolClass = tool.getClass();
				if (toolClass.isAnnotationPresent(ToolHelpArticle.class)) {
					reader = new BufferedReader(new StringReader(toolClass.getAnnotation(ToolHelpArticle.class).value()));
				} else {
					String text = tool.getClass().getSimpleName();
					if (toolClass.isAnnotationPresent(ToolSummary.class)) {
						text += "\n" + TOOL_SUMMARY_MARKER;
					}
					return text;
				}
			} else {
				return null;
			}
		}

		try {
			StringBuilder output = new StringBuilder();
			String line = reader.readLine();
			if (!line.startsWith(ARTICLE_CAPTION_MARKER)) {
				return null;
			}
			line = line.substring(ARTICLE_CAPTION_MARKER.length()).trim();
			output.append(line);

			while ((line = reader.readLine()) != null) {
				output.append("\n").append(line);
			}
			return output.toString();
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
		File[] helpFiles = new File(helpPrefix).listFiles(new UnspecialHelpFileFilter());

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

		for (Class<? extends AbstractTool> tool : tools) {
			if (tool.isAnnotationPresent(ToolHelpArticle.class) || tool.isAnnotationPresent(ToolSummary.class)) {
				String toolName = tool.getSimpleName();
				if (!lowerCaptions.contains(toolName.toLowerCase(Locale.US))) {
					captions.add(toolName);
				}
			}
		}

		return captions;
	}

	@Override
	public String getMetaTag(String name, String caption, int width) {
		switch (name) {
		case "list-tools":
			return Help.listAllTools(width);
		case "tool-summary":
			AbstractTool tool = getTool(caption);
			if (tool == null) {
				return "--- Couldn't generate \"" + caption + "\" tool summary ---\n";
			} else {
				return tool.generateHelpSummary(width);
			}
		default:
			return null;
		}
	}

	protected BufferedReader getHelpReader(String caption) {
		caption = caption.toLowerCase(Locale.US);
		if (!caption.endsWith(HELP_FILE_SUFFIX)) {
			caption += HELP_FILE_SUFFIX;
		}
		String contentFilePath = helpPrefix + "/" + caption.replaceAll(" ", "_");
		try {
			return new BufferedReader(new FileReader(contentFilePath));
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	protected AbstractTool getTool(String name) {
		for (Class<? extends AbstractTool> tool : tools) {
			if (name.equalsIgnoreCase(tool.getSimpleName())) {
				try {
					return tool.newInstance();
				} catch (InstantiationException e) {
					return null;
				} catch (IllegalAccessException e) {
					return null;
				}
			}
		}
		return null;
	}
}
