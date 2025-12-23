/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.qd.util.RateLimiter;

import java.util.Map;

/**
 * Stores information about {@link NetTestSide} configuration.
 */
class NetTestConfig {
    String name;
    String[] addresses; // addresses for each instance
    int instanceCount = 1;
    int totalSymbols = 100000;
    int symbolsPerEntity = totalSymbols;
    boolean sliceSelection = false;
    int minLength = -1;
    int maxLength = -1;
    String ipfPath = null;
    OptionCollector optionCollector;
    OptionStat optionStat;
    boolean wildcard;
    Map<String, RateLimiter> rateLimiters;
}
