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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.util.SystemProperties;
import com.dxfeed.event.market.MarketEventSymbols;

import java.util.Collection;

public abstract class EventDelegateFactory {
    public void buildScheme(SchemeBuilder builder) {}

    public Collection<EventDelegate<?>> createDelegates(DataRecord record) {
        return null;
    }

    public Collection<EventDelegate<?>> createStreamOnlyDelegates(DataRecord record) {
        return null;
    }

    protected String getBaseRecordName(String recordName) {
        return recordName;
    }

    protected SerialFieldType selectDecimal(SerialFieldType type, String... typeSelectors) {
        if (SystemProperties.getBooleanProperty("dxscheme.wide", true))
            type = SerialFieldType.WIDE_DECIMAL;
        for (int i = typeSelectors.length; --i >= 0;) {
            String selector = System.getProperty(typeSelectors[i]);
            if ("wide".equalsIgnoreCase(selector))
                type = SerialFieldType.WIDE_DECIMAL;
            if ("tiny".equalsIgnoreCase(selector) || "decimal".equalsIgnoreCase(selector))
                type = SerialFieldType.DECIMAL;
            if ("int".equalsIgnoreCase(selector))
                type = SerialFieldType.COMPACT_INT;
        }
        return type;
    }

    protected SerialFieldType selectTime(SerialFieldType type, String... typeSelectors) {
        // opposing to decimal fields, we don't have a scheme-wide property for the moment
        for (int i = typeSelectors.length; --i >= 0;) {
            String selector = System.getProperty(typeSelectors[i]);
            if ("millis".equalsIgnoreCase(selector))
                type = SerialFieldType.TIME_MILLIS;
            if ("seconds".equalsIgnoreCase(selector))
                type = SerialFieldType.TIME_SECONDS;
            // FIXME: Doesn't work in DXFeed API
            // if ("none".equalsIgnoreCase(selector))
            //    type = SerialFieldType.VOID;
        }
        return type;
    }

    protected static char[] getExchanges(String recordProperty) {
        String patternStr = SystemProperties.getProperty(recordProperty,
            SystemProperties.getProperty("dxscheme.exchanges", MarketEventSymbols.DEFAULT_EXCHANGES));
        return MarketEventSymbols.getExchangesByPattern(patternStr).toCharArray();
    }
}
