/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.text;

/**
 * Utility class that encodes/decodes any strings into special text format.
 */
public class TextCoding {
	private static final int MIN_GOOD_SYMBOL = 32;
	private static final int MAX_GOOD_SYMBOL = 126;

	/**
	 * Converts unicode chars to encoded &#92;uxxxx and escapes
	 * special characters with a preceding slash.
	 *
	 * @param s string to convert
	 * @return converted string.
	 */
	public static String encode(String s) {
		if (s == null)
			return "\\NULL";
		StringBuilder sb = new StringBuilder(s.length() << 1);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= MIN_GOOD_SYMBOL) && (c <= MAX_GOOD_SYMBOL)) {
				if ((c == '\\') || (c == '\"')) {
					sb.append('\\');
				}
				sb.append(c);
			} else {
				switch (c) {
				case 0:
					sb.append("\\0");
					break;
				case'\t':
					sb.append("\\t");
					break;
				case'\n':
					sb.append("\\n");
					break;
				case'\r':
					sb.append("\\r");
					break;
				case'\f':
					sb.append("\\f");
					break;
				default:
					sb.append("\\u");
					sb.append(Integer.toString(c + 65536, 16).toUpperCase().substring(1));
				}
			}
		}
		return sb.toString();
	}

	static void checkCharRange(byte c) {
		if (c < MIN_GOOD_SYMBOL || c > MAX_GOOD_SYMBOL)
			throw new CorruptedTextFormatException("Invalid character");
	}
}
