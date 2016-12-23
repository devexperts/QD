/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.impl;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.devexperts.qd.QDLog;
import com.devexperts.util.TimeUtil;

/**
 * Simple default implemention for QDLog class.
 * @deprecated Use {@link com.devexperts.logging.Logging}
 */
public class QDLogImpl extends QDLog {
	private final PrintStream out;
	private final PrintStream err;

	private int last_minute;
	private String last_minute_str;

	public QDLogImpl(PrintStream out, PrintStream err) {
		this.out = out;
		this.err = err;
	}

	public QDLogImpl(PrintStream out) {
		this.out = out;
		this.err = out;
	}

	public void debug(Object msg) {
		log(out, 'D', msg);
	}

	public void info(Object msg) {
		log(out, 'I', msg);
	}

	public void error(Object msg, Throwable t) {
		log(err, 'E', msg);
		if (t != null)
			t.printStackTrace(err);
	}

	protected void log(PrintStream out, char level, Object msg) {
		StringBuffer sb = new StringBuffer();
		sb.append(level);
		sb.append(' ');
		appendTime(sb);
		sb.append(" ");
		String s = String.valueOf(msg);
		if (s.startsWith("\b")) {
			sb.append(s.substring(1));
		} else {
			sb.append(compactName(Thread.currentThread().getName()));
			sb.append(" - ");
			sb.append(s);
		}
		// Print each line separately
		synchronized (out) {
			int start = 0;
			for (int i = 0; i < sb.length(); i++)
				if (sb.charAt(i) == '\n') {
					int end = i > 0 && sb.charAt(i - 1) == '\r' ? i - 1 : i;
					out.println(sb.substring(start, end));
					start = i + 1;
				}
			out.println(sb.substring(start));
		}
	}

	private String compactName(String name) {
		StringBuffer sb = new StringBuffer();
		boolean skiplower = false;
		int n = name.length();
		for (int i = 0; i < n; i++) {
			char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
				sb.append(c);
				skiplower = true;
			} else if (c >= 'a' && c <= 'z') {
				if (!skiplower)
					sb.append(c);
			} else {
				sb.append(c);
				skiplower = false;
			}
		}
		return sb.toString();
	}

	private void appendTime(StringBuffer sb) {
		long this_time = System.currentTimeMillis();
		int this_minute = (int)(this_time / TimeUtil.MINUTE);
		if (this_minute != last_minute) {
			last_minute = this_minute;
			last_minute_str = new SimpleDateFormat("yyyyMMdd HHmm").format(new Date(this_time));
		}
		sb.append(last_minute_str);
		int secms = (int)(this_time % TimeUtil.MINUTE);
		sb.append((char)('0' + (secms / 10000)));
		sb.append((char)('0' + (secms / 1000) % 10));
		sb.append('.');
		sb.append((char)('0' + (secms / 100) % 10));
		sb.append((char)('0' + (secms / 10) % 10));
		sb.append((char)('0' + secms % 10));
	}
}
