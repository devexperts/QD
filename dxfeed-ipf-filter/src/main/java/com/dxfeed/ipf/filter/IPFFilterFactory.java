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
package com.dxfeed.ipf.filter;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SpecificSubscriptionFilter;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.ServiceProvider;

@ServiceProvider(order = -200)
public class IPFFilterFactory extends QDFilterFactory {
    private static final Logging log = Logging.getLogging(IPFFilterFactory.class);

    @SpecificSubscriptionFilter("IPF symbol filter for specified <url> (file, ftp or http), " +
        "accepts all records, " +
        "accepts optional symbol parameters like {tho=true} or {price=bid}. " +
        "Additional attributes for this filter can be specified after its <url>, separated by commas:\n" +
        "use '" + IPFSymbolFilter.UPDATE_PROPERTY + "=<period>(s|m|h)' to create dynamic filter that reloads IPF " +
            "on a specified period of time;\n" +
        "use '" + IPFSymbolFilter.SCHEDULE_PROPERTY + "[=<schedule>]' to avoid checks on a specified schedule " +
            "or, by default, on trading hours of symbols in the given IPF file.")
    public static final String IPF_DESC = IPFSymbolFilter.FILTER_NAME_PREFIX + "[<url>]";

    @Override
    public QDFilter createFilter(String spec) {
        return createFilter(spec, QDFilterContext.DEFAULT);
    }

    @Override
    public QDFilter createFilter(String spec, QDFilterContext context) {
        if (context == QDFilterContext.REMOTE_FILTER)
            return null; // do not process remote IPF filters
        IPFSymbolFilter filter = null;
        if (spec.startsWith(IPFSymbolFilter.FILTER_NAME_PREFIX + "[") && spec.endsWith("]"))
            filter = IPFSymbolFilter.create(getScheme(), spec);
        else if (context == QDFilterContext.SYMBOL_SET && spec.contains(".ipf"))
            try {
                filter = IPFSymbolFilter.create(getScheme(), IPFSymbolFilter.FILTER_NAME_PREFIX + "[" + spec + "]");
            } catch (FilterSyntaxException e) {
                log.error("Failed to read potential IPF \"" + spec + "\", treating as individual symbol or pattern", e);
            }
        if (context == QDFilterContext.SYMBOL_SET && filter != null)
            // Convert symbol sets with attributes into plain symbol set in this context
            return new SymbolSetFilter(filter);
        return filter;
    }
}
