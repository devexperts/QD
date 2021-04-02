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
package com.devexperts.qd.tools;

/**
 * Stores information about {@link NetTestSide} configuration.
 */
class NetTestConfig {
    String address;
    int connectionsNum = 1;
    int totalSymbols = 100000;
    int symbolsPerEntity = totalSymbols;
    OptionCollector optionCollector;
    OptionStat optionStat;
    boolean wildcard;
}
