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
package com.dxfeed.ipf.option;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * Set of option series for a single product or underlying symbol.
 *
 * <h3>Threads and clocks</h3>
 *
 * This class is <b>NOT</b> thread-safe and cannot be used from multiple threads without external synchronization.
 *
 * @param <T> The type of option instrument instances.
 */
public final class OptionChain<T> implements Cloneable {
    private final String symbol;

    private final SortedMap<OptionSeries<T>, OptionSeries<T>> seriesMap = new TreeMap<>();

    OptionChain(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns a shall copy of this option chain.
     * All series are copied (cloned) themselves, but option instrument instances are shared with original.
     * @return a shall copy of this option chain.
     */
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public OptionChain<T> clone() {
        OptionChain<T> clone = new OptionChain<>(symbol);
        for (OptionSeries<T> series : seriesMap.values()) {
            OptionSeries<T> seriesClone = series.clone();
            clone.seriesMap.put(seriesClone, seriesClone);
        }
        return clone;
    }

    /**
     * Returns symbol (product or underlying) of this option chain.
     * @return symbol (product or underlying) of this option chain.
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns a sorted set of option series of this option chain.
     * @return sorted set of option series of this option chain.
     */
    public SortedSet<OptionSeries<T>> getSeries() {
        return (SortedSet<OptionSeries<T>>) seriesMap.keySet();
    }

    void addOption(OptionSeries<T> series, boolean isCall, double strike, T option) {
        OptionSeries<T> os = seriesMap.get(series);
        if (os == null) {
            os = new OptionSeries<>(series);
            seriesMap.put(os, os);
        }
        os.addOption(isCall, strike, option);
    }
}
