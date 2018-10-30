/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import java.util.Collection;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;

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

    protected SerialFieldType select(SerialFieldType type, String... typeSelectors) {
        if ("true".equalsIgnoreCase(System.getProperty("dxscheme.wide")))
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
}
