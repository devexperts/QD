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

import java.io.Serializable;

/**
 * Container for news full text content along with the summary.
 */
public class News implements Serializable {
    private static final long serialVersionUID = 0L;

    public static final int MAX_BODY_LENGTH = 32700;

    private NewsSummary summary;
    private String content;

    /**
     * Creates news content.
     */
    public News(NewsSummary summary, String content) {
        if (summary == null)
            throw new NullPointerException("summary is null");
        if (content == null)
            throw new NullPointerException("content is null");
        this.summary = summary;
        this.content = (content.length() > MAX_BODY_LENGTH) ? content.substring(0, MAX_BODY_LENGTH) : content;
    }

    /**
     * Convenient method for {@code getSummary().getKey()}.
     * @return news key
     */
    public NewsKey getKey() {
        return getSummary().getKey();
    }

    /**
     * Returns news summary.
     * @return news summary
     */
    public NewsSummary getSummary() {
        return summary;
    }

    /**
     * Returns news content or empty string if the news contained only summary.
     * @return news content
     */
    public String getContent() {
        return content;
    }

    public String toString() {
        return summary.toString() + "[" + content.length() + "]";
    }
}
