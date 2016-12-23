/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.news;

import java.io.Serializable;

/**
 * Key to uniquely identify news.
 * <p><b>Warning!</b> Do not rely on the internals of this class - it can be changed for implementation needs.
 */
public class NewsKey implements Serializable {
    private static final long serialVersionUID = 0L;

    public static final NewsKey FIRST_KEY = new NewsKey("");

    private final String code;

    public NewsKey(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof NewsKey) {
            NewsKey that = (NewsKey) o;
            return this.getCode().equals(that.getCode());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getCode().hashCode();
    }

    public String toString() {
        return "NewsKey{" + code + "}";
    }
}
