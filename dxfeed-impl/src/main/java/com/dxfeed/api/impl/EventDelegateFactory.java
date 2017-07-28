/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import java.util.Collection;

import com.devexperts.qd.DataRecord;

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
}
