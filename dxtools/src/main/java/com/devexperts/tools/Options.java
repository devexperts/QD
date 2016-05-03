/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.util.ArrayList;
import java.util.Locale;

public class Options {
	protected final Option[] options;

	public Options(Option... options) {
		this.options = options;
	}

	public String[] parse(String[] args) throws OptionParseException {
		ArrayList<String> rest = new ArrayList<>(args.length);
		for (int i = 0; i < args.length; i++) {
			String s = args[i];
			if (s.startsWith("-D")) {
				// JVM system option
				int eqPos = s.indexOf('=');
				if (eqPos < 0) {
					eqPos = s.length();
					s += '=';
				}
				System.setProperty(s.substring(2, eqPos), s.substring(eqPos + 1));
			} else if (s.startsWith("-")) {
				if (s.startsWith("--")) {
					String fullName = s.substring(2).toLowerCase(Locale.US);
					Option opt = searchFull(fullName);
					if (opt != null)
						i = opt.parse(i, args);
					else
						throw new OptionParseException("Unrecognized option: " + fullName);
				} else if (searchFull(s.substring(1)) != null) {
					i = searchFull(s.substring(1)).parse(i, args);
				} else {
					String shortNames = s.substring(1);
					for (int j = 0; j < shortNames.length(); j++) {
						char shortName = shortNames.charAt(j);
					search:
						{
							for (Option opt : options)
								if (shortName == opt.getShortName()) {
									i = opt.parse(i, args);
									break search;
								}
							throw new OptionParseException("Unrecognized option: " + shortName);
						}
					}
				}
			} else
				rest.add(s);
		}
		// want on deprecation
		for (Option opt : options) {
			if (opt.isSet()) {
				opt.init();
			}
		}
		// return
		return rest.toArray(new String[rest.size()]);
	}

	private Option searchFull(String fullName) {
		for (Option opt : options)
			if (fullName.equals(opt.getFullName()))
				return opt;
		return null;
	}

	public String generateHelp(int screenWidth) {
		ArrayList<String[]> table = new ArrayList<>(options.length);
		ArrayList<String[]> deprecated = new ArrayList<>();
		for (Option opt : options) {
			if (opt.getDeprecated() == null)
				table.add(new String[] {"   ", opt.toString(), "-", opt.getDescription()});
			else
				deprecated.add(new String[] {"   ", opt.toString(), "-",
					opt.getDescription() + "\n" + opt.getDeprecated()});
		}

		StringBuilder sb = new StringBuilder();
		sb.append("QDOptions:\n");
		sb.append(Help.formatTable(table, screenWidth, " "));

		if (!deprecated.isEmpty()) {
			sb.append("\nDeprecated options:\n");
			sb.append(Help.formatTable(deprecated, screenWidth, " "));
		}

		return sb.toString();
	}

	public int getCount() {
		return options.length;
	}
}
