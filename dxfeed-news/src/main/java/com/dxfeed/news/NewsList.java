/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.news;

import com.dxfeed.news.impl.RemoteNewsService;

import java.io.Serializable;
import java.util.List;

/**
 * News list with the last available news key. Last news key is used to not receive duplicate news.
 * @see RemoteNewsService#findNewsForFilter(NewsFilter, NewsKey)
 */
public class NewsList implements Serializable {
    private static final long serialVersionUID = 0L;

    private final List<NewsSummary> news;
    private final NewsKey lastKey;

    public NewsList(List<NewsSummary> news, NewsKey lastKey) {
        this.news = news;
        this.lastKey = lastKey;
    }

    /**
     * Returns list of news summaries.
     * @return list of summaries.
     */
    public List<NewsSummary> getNews() {
        return news;
    }

    /**
     * Returns last news key for which the news list is valid.
     * @return last news key
     */
    public NewsKey getLastKey() {
        return lastKey;
    }
}
