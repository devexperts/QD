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

import com.devexperts.services.Service;

@Service
public interface HelpProvider {
	String getArticle(String name);
	List<String> getArticles();
//	public String getMetaTag(String name);
}
