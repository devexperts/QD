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
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.impl.QuoteMapping;

import java.util.EnumSet;

public final class OrderByQuoteBidUnitaryDelegate extends OrderBaseDelegateImpl<Order> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final QuoteMapping m;

    public OrderByQuoteBidUnitaryDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(QuoteMapping.class);
    }

    @Override
    public QuoteMapping getMapping() {
        return m;
    }

    @Override
    public Order createEvent() {
        return new Order();
    }

    @Override
    public Order getEvent(Order event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setEventFlags((cursor.getEventFlags() & ~(Order.SNAPSHOT_END | Order.SNAPSHOT_SNIP)) | Order.TX_PENDING);
        event.setIndex(((long) getSource().id() << 48) | 1L << 47 | ((long) (m.getRecordExchange() & 0x7FFF) << 32));
        event.setTime(m.getBidTimeMillis(cursor));
        event.setSequence(0);
        event.setPrice(m.getBidPrice(cursor));
        event.setSizeAsDouble(m.getBidSizeDouble(cursor));
        event.setExchangeCode(m.getRecordExchange() == 0 ? m.getBidExchangeCode(cursor) : m.getRecordExchange());
        event.setOrderSide(Side.BUY);
        event.setScope(m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL);
        event.setMarketMaker(null);
        return event;
    }

    @Override
    public IndexedEventSource getSource() {
        return m.getRecordExchange() == 0 ? OrderSource.COMPOSITE : OrderSource.REGIONAL;
    }
// END: CODE AUTOMATICALLY GENERATED

    @Override
    public char getExchangeCode() {
        return 0;
    }
}
