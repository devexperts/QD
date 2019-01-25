/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.news.impl;

import com.dxfeed.news.*;

/**
 * Service to provide synchronous access to news.
 */
public interface RemoteNewsService {
    /**
     * Returns news contents for the specified news key.
     *
     * @param newsKey news key to lookup news body for.
     * @return news body contents
     * @throws NewsNotFoundException if news is not found
     */
    public String getNewsContent(NewsKey newsKey) throws NewsNotFoundException;

    /**
     * Long-polling method to find news updates since last known news for the specified filter.
     * If there are news available on the server side this method will return immediately,
     * otherwise it will be blocked until news updates arrive.
     *
     * @param filter news filter to filter news on the server-side.
     * @param lastKey last received news key, or {@link NewsKey#FIRST_KEY} for first time.
     * @return news list along with the last news key.
     */
    public NewsList findNewsForFilter(NewsFilter filter, NewsKey lastKey);
}
