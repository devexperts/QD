/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.io.File;
import java.io.FileFilter;

public class UnspecialHelpFileFilter implements FileFilter {
	private static final String HELP_FILE_SUFFIX = ".txt";

	@Override
	public boolean accept(File file) {
		String pathname = file.getName();
		return !pathname.startsWith("_") && pathname.endsWith(HELP_FILE_SUFFIX);
	}
}
