/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps {@link String} symbol to the link {@link Set} of subscribed
 * {@link DataRecord}.
 */
class SubscriptionMap extends AbstractRecordSink {
    private final DataScheme scheme;
    private final HashMap<String, Long>[] maps;
    private final boolean hasTime;

    SubscriptionMap(DataScheme scheme) {
        this(scheme, false);
    }

    @SuppressWarnings("unchecked")
    SubscriptionMap(DataScheme scheme, boolean hasTime) {
        this.scheme = scheme;
        this.hasTime = hasTime;
        maps = (HashMap<String, Long>[]) new HashMap[scheme.getRecordCount()];
        for (int rid = 0; rid < maps.length; rid++)
            maps[rid] = new HashMap<String, Long>();
    }

    SubscriptionMap(DataScheme scheme, RecordProvider provider) {
        this(scheme, provider.getMode() == RecordMode.HISTORY_SUBSCRIPTION);
        provider.retrieve(this);
    }

    SubscriptionMap(SubscriptionMap other) {
        this(other.scheme, other.hasTime);
        addAll(other);
    }

    @Override
    public void append(RecordCursor cursor) {
        DataRecord record = cursor.getRecord();
        assert scheme == record.getScheme();
        maps[record.getId()].put(scheme.getCodec().decode(cursor.getCipher(), cursor.getSymbol()),
            hasTime ? cursor.getTime() : 0L);
    }

    public void removeAll(SubscriptionMap other) {
        assert scheme == other.scheme;
        for (int rid = 0; rid < maps.length; rid++)
            maps[rid].keySet().removeAll(other.maps[rid].keySet());
    }

    public void addAll(SubscriptionMap other) {
        assert scheme == other.scheme;
        for (int rid = 0; rid < maps.length; rid++)
            maps[rid].putAll(other.maps[rid]);
    }

    public boolean containsAll(SubscriptionMap other) {
        assert scheme == other.scheme;
        for (int rid = 0; rid < maps.length; rid++) {
            for (Map.Entry<String, Long> e : other.maps[rid].entrySet()) {
                Long thisTime = maps[rid].get(e.getKey());
                if (thisTime == null || thisTime > e.getValue())
                    return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        for (HashMap<String, Long> map : maps) {
            if (!map.isEmpty())
                return false;
        }
        return true;
    }

    public int getSubscriptionSize() {
        int size = 0;
        for (HashMap<String, Long> map : maps)
            size += map.size();
        return size;
    }

    public Set<String> getSymbols() {
        Set<String> symbols = new TreeSet<String>();
        for (HashMap<String, Long> map : maps)
            symbols.addAll(map.keySet());
        return symbols;
    }

    public void clear() {
        for (HashMap<String, Long> map : maps)
            map.clear();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof SubscriptionMap))
            return false;
        SubscriptionMap other = (SubscriptionMap) obj;
        assert scheme == other.scheme;
        for (int rid = 0; rid < maps.length; rid++) {
            if (!maps[rid].equals(other.maps[rid]))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 0;
        for (HashMap<String, Long> map : maps)
            result = result * 31 + map.hashCode();
        return result;
    }

    public String toString() {
        return getSymbols().toString();
    }
}
