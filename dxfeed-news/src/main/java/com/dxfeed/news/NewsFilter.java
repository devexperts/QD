/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.news;

import java.io.Serializable;

public class NewsFilter implements Serializable {
    private static final long serialVersionUID = 0L;

    // ==================== public static fields ====================

    /**
     * Default (and maximum) limit for the news count returned in one chunk.
     */
    public static final int DEFAULT_LIMIT = 100;

    /**
     * Default (and maximum) time period to wait while polling for news.
     */
    public static final long DEFAULT_TIMEOUT = 60 * 60 * 1000L; // 1 hour

    /**
     * Default filter with {@link #DEFAULT_TIMEOUT default timeout} and {@link #DEFAULT_LIMIT default limit}
     * and with any sources and symbols
     */
    public static final NewsFilter EMPTY = new NewsFilter();

    // ==================== private instance fields ====================

    private final String source;
    private final String symbol;
    private final int limit;
    private final long timeout;

    // ==================== factory methods ====================

    public static NewsFilter createSourceFilter(String source) {
        return new NewsFilter(source, null, DEFAULT_LIMIT, DEFAULT_TIMEOUT);
    }

    public static NewsFilter createSymbolFilter(String symbol) {
        return new NewsFilter(null, symbol, DEFAULT_LIMIT, DEFAULT_TIMEOUT);
    }

    // ==================== public instance methods ====================

    public NewsFilter() {
        this(null, null, DEFAULT_LIMIT, DEFAULT_TIMEOUT);
    }

    public NewsFilter(String source, String symbol, int limit) {
        this(source, symbol, limit, DEFAULT_TIMEOUT);
    }

    public NewsFilter(String source, String symbol, int limit, long timeout) {
        this.source = source;
        this.symbol = symbol;
        if (limit <= 0)
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        this.limit = Math.min(limit, DEFAULT_LIMIT);
        if (timeout <= 0)
            throw new IllegalArgumentException("Timeout must be positive: " + timeout);
        this.timeout = Math.min(timeout, DEFAULT_TIMEOUT);
    }

    public String getSource() {
        return source;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getLimit() {
        return limit;
    }

    /** Timeout in millis to wait for news while polling. */
    public long getTimeout() {
        return timeout;
    }

    public String toString() {
        return "NewsFilter{source=" + source +
            ", symbol=" + symbol +
            ", limit=" + limit +
            "}";
    }
}
