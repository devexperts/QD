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
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.market.impl.MarketMakerMapping;

import java.util.EnumSet;

public final class MarketMakerDelegate extends EventDelegate<MarketMaker> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final MarketMakerMapping m;

    public MarketMakerDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(MarketMakerMapping.class);
    }

    @Override
    public MarketMakerMapping getMapping() {
        return m;
    }

    @Override
    public MarketMaker createEvent() {
        return new MarketMaker();
    }

    @Override
    public MarketMaker getEvent(MarketMaker event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setEventFlags(cursor.getEventFlags());
        event.setIndex(((long) m.getExchangeCode(cursor) << 32) | (m.getMarketMaker(cursor) & 0xFFFFFFFFL));
        event.setBidTime(m.getBidTimeMillis(cursor));
        event.setBidPrice(m.getBidPrice(cursor));
        event.setBidSize(m.getBidSizeDouble(cursor));
        event.setBidCount(m.getBidCount(cursor));
        event.setAskTime(m.getAskTimeMillis(cursor));
        event.setAskPrice(m.getAskPrice(cursor));
        event.setAskSize(m.getAskSizeDouble(cursor));
        event.setAskCount(m.getAskCount(cursor));
        return event;
    }

    @Override
    public RecordCursor putEvent(MarketMaker event, RecordBuffer buf) {
        RecordCursor cursor = super.putEvent(event, buf);
        cursor.setEventFlags(event.getEventFlags());
        long index = event.getIndex();
        m.setExchangeCode(cursor, (char) (index >>> 32));
        m.setMarketMaker(cursor, (int) index);
        m.setBidTimeMillis(cursor, event.getBidTime());
        m.setBidPrice(cursor, event.getBidPrice());
        m.setBidSizeDouble(cursor, event.getBidSize());
        m.setBidCount(cursor, (int) event.getBidCount());
        m.setAskTimeMillis(cursor, event.getAskTime());
        m.setAskPrice(cursor, event.getAskPrice());
        m.setAskSizeDouble(cursor, event.getAskSize());
        m.setAskCount(cursor, (int) event.getAskCount());
        if (index < 0)
            throw new IllegalArgumentException("Invalid index to publish");
        if ((event.getEventFlags() & (MarketMaker.SNAPSHOT_END | MarketMaker.SNAPSHOT_SNIP)) != 0 && index != 0)
            throw new IllegalArgumentException("SNAPSHOT_END and SNAPSHOT_SNIP MarketMaker event must have index == 0");
        return cursor;
    }
// END: CODE AUTOMATICALLY GENERATED
}