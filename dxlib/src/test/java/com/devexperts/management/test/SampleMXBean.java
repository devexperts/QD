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
package com.devexperts.management.test;

import com.devexperts.annotation.Description;
import com.devexperts.management.ManagementDescription;
import com.devexperts.management.ManagementParameterDescription;

/**
 * Management interface for abstract market data gate
 * @dgen.annotate method {}
 */
public interface SampleMXBean {
    // ------------------ This is annotated with legacy "ManagementDescription" and "ManagementParameterDescription" annotations ------------------

    @ManagementDescription(
        value = "Removes all data for specified symbol",
        parameters = {@ManagementParameterDescription(name = "symbol", value = "the symbol")})
    public String removeSymbol(String symbol);

    // ------------------ This is annotated with new "Description" annotation ------------------

    @Description("Sets new values for prev day close data")
    public String setPrevDayClose(
        @Description(name = "symbol", value = "the symbol") String symbol,
        @Description(name = "date", value = "the prevDayId in yyyyMMdd format") String date,
        @Description(name = "price", value = "the prevDayClosePrice") String price
    );

    @Description("Removes all data for all symbols that were inactive for a specified time period")
    public String removeDeadSymbols(
        @Description(name = "ttlMillis", value = "inactivity period in milliseconds") long ttlMillis
    );

    @Description("Tests that string arrays are supported")
    public int scan(
        @Description(name = "symbols", value = "the symbols") String[] symbols
    );

    @Description("Tests that primitive arrays are supported")
    public int avoid(
        @Description(name = "indices", value = "the indices") int[] indices
    );
}
