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
package com.dxfeed.ondemand.impl;

import com.devexperts.util.TimeUtil;

class CacheConfig implements Cloneable {
    private static final long MB = 1024 * 1024;

    String address = "";
    long cacheLimit = Math.min(Math.max(8 * MB, Runtime.getRuntime().maxMemory() / 4), 128 * MB);
    long fileCacheLimit = 64 * MB;
    String fileCachePath = "";
    long fileCacheDumpPeriod = 10 * TimeUtil.MINUTE;
    long timeToLive = 10 * TimeUtil.DAY;

    @Override
    public CacheConfig clone()  {
        try {
            return (CacheConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e); // cannot happen
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheConfig)) return false;
        CacheConfig that = (CacheConfig) o;
        if (cacheLimit != that.cacheLimit) return false;
        if (fileCacheDumpPeriod != that.fileCacheDumpPeriod) return false;
        if (fileCacheLimit != that.fileCacheLimit) return false;
        if (timeToLive != that.timeToLive) return false;
        if (!address.equals(that.address)) return false;
        if (!fileCachePath.equals(that.fileCachePath)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + (int) (cacheLimit ^ (cacheLimit >>> 32));
        result = 31 * result + (int) (fileCacheLimit ^ (fileCacheLimit >>> 32));
        result = 31 * result + fileCachePath.hashCode();
        result = 31 * result + (int) (fileCacheDumpPeriod ^ (fileCacheDumpPeriod >>> 32));
        result = 31 * result + (int) (timeToLive ^ (timeToLive >>> 32));
        return result;
    }
}
