/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.impl.EventDelegateSet;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEventSource;

import java.util.EnumSet;

public abstract class OrderBaseDelegateImpl<T extends OrderBase> extends MarketEventDelegateImpl<T> {
    public static final String DXSCHEME_UNITARY_ORDER_SOURCE = "dxscheme.unitaryOrderSource";

    protected OrderBaseDelegateImpl(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
    }

    @Override
    public EventDelegateSet<T, ? extends EventDelegate<T>> createDelegateSet() {
        return new OrderBaseDelegateSet<>(getEventType());
    }

    /**
     * Source of this delegate (zero by default).
     * It is overriden in OrderXXXDelegates.
     */
    public abstract IndexedEventSource getSource();

    @Override
    public Object getSubscriptionSymbolByQDSymbolAndTime(String qdSymbol, long time) {
        return new IndexedEventSubscriptionSymbol<>(getEventSymbolByQDSymbol(qdSymbol), getSource());
    }
}
