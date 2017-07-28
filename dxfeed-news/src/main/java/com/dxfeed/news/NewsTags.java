/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.news;

/**
 * Common news tags names.
 */
public class NewsTags {
    public static final String SYMBOLS = "Symbols";
    public static final String KEYWORDS = "Keywords";
    public static final String CORRECTION = "Correction";

    // Special field that contains raw news meta information (RDF)
    public static final String CONTENT_META = "_meta";
    // Special field that contains raw news data (NITF XML)
    public static final String CONTENT_NEWS = "_news";

    private NewsTags() {} // do not create, utility class only
}
