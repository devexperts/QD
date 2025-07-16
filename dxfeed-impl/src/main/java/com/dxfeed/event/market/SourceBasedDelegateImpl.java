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
package com.dxfeed.event.market;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEventSource;

import java.util.EnumSet;

public abstract class SourceBasedDelegateImpl<T extends MarketEvent> extends MarketEventDelegateImpl<T> {
    protected SourceBasedDelegateImpl(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
    }

    @Override
    public Object getSubscriptionSymbolByQDSymbolAndTime(String qdSymbol, long time) {
        return new IndexedEventSubscriptionSymbol<>(getEventSymbolByQDSymbol(qdSymbol), getSource());
    }

    /**
     * Source of this delegate (zero by default).
     * It is overridden in OrderXXXDelegates and OrderImbalanceXXXDelegates.
     */
    public abstract IndexedEventSource getSource();
}
