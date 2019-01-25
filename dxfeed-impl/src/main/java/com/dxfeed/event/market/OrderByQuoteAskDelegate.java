/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import java.util.EnumSet;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.impl.QuoteMapping;

public final class OrderByQuoteAskDelegate extends OrderBaseDelegateImpl<Order> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final QuoteMapping m;

    public OrderByQuoteAskDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
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
        event.setIndex(((long) getSource().id() << 48) | ((long) m.getRecordExchange() << 32));
        event.setTime(m.getAskTimeMillis(cursor));
        event.setSequence(0);
        event.setPrice(m.getAskPrice(cursor));
        event.setSizeAsDouble(m.getAskSizeDouble(cursor));
        event.setExchangeCode(m.getRecordExchange() == 0 ? m.getAskExchangeCode(cursor) : m.getRecordExchange());
        event.setOrderSide(Side.SELL);
        event.setScope(m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL);
        event.setMarketMaker(null);
        return event;
    }

    @Override
    public IndexedEventSource getSource() {
        return m.getRecordExchange() == 0 ? OrderSource.COMPOSITE_ASK : OrderSource.REGIONAL_ASK;
    }
// END: CODE AUTOMATICALLY GENERATED

    @Override
    public char getExchangeCode() {
        return 0;
    }
}
