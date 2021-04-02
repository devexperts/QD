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
package com.dxfeed.event.market;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.impl.MarketMakerMapping;

import java.util.EnumSet;

public final class OrderByMarketMakerAskDelegate extends OrderBaseDelegateImpl<Order> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final MarketMakerMapping m;

    public OrderByMarketMakerAskDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(MarketMakerMapping.class);
    }

    @Override
    public MarketMakerMapping getMapping() {
        return m;
    }

    @Override
    public Order createEvent() {
        return new Order();
    }

    @Override
    public Order getEvent(Order event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setEventFlags(cursor.getEventFlags());
        event.setIndex(((long) getSource().id() << 48) | ((long) m.getExchangeCode(cursor) << 32) | (m.getMarketMaker(cursor) & 0xFFFFFFFFL));
        event.setExchangeCode(m.getExchangeCode(cursor));
        event.setMarketMaker(m.getMarketMakerString(cursor));
        event.setTime(m.getAskTimeMillis(cursor));
        event.setSequence(0);
        event.setPrice(m.getAskPrice(cursor));
        event.setSizeAsDouble(m.getAskSizeDouble(cursor));
        event.setCount(m.getAskCount(cursor));
        event.setOrderSide(Side.SELL);
        event.setScope(Scope.AGGREGATE);
        return event;
    }

    @Override
    public IndexedEventSource getSource() {
        return OrderSource.AGGREGATE_ASK;
    }
// END: CODE AUTOMATICALLY GENERATED
}
