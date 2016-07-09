/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.tools;

import java.util.List;

public abstract class AbstractToolHelper {
	protected abstract List<Class<? extends AbstractTool>> getToolsList();

	AbstractTool getToolFromHelper(String name) {
		final List<Class<? extends AbstractTool>> tools = getToolsList();
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

	String[] getToolNamesFromHelper() {
		final List<Class<? extends AbstractTool>> tools = getToolsList();
		String[] names = new String[tools.size()];
		for (int i = 0; i < tools.size(); i++)
			names[i] = tools.get(i).getSimpleName();
		return names;
	}
}
