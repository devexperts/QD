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
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.market.impl.SummaryMapping;

public final class SummaryDelegate extends MarketEventDelegateImpl<Summary> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final SummaryMapping m;

    public SummaryDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(SummaryMapping.class);
    }

    @Override
    public SummaryMapping getMapping() {
        return m;
    }

    @Override
    public Summary createEvent() {
        return new Summary();
    }

    @Override
    public Summary getEvent(Summary event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setDayId(m.getDayId(cursor));
        event.setDayOpenPrice(m.getDayOpenPrice(cursor));
        event.setDayHighPrice(m.getDayHighPrice(cursor));
        event.setDayLowPrice(m.getDayLowPrice(cursor));
        event.setDayClosePrice(m.getDayClosePrice(cursor));
        event.setPrevDayId(m.getPrevDayId(cursor));
        event.setPrevDayClosePrice(m.getPrevDayClosePrice(cursor));
        event.setPrevDayVolume(m.getPrevDayVolumeDouble(cursor));
        event.setOpenInterest(m.getOpenInterest(cursor));
        event.setFlags(m.getFlags(cursor));
        return event;
    }

    @Override
    public RecordCursor putEvent(Summary event, RecordBuffer buf) {
        RecordCursor cursor = super.putEvent(event, buf);
        m.setDayId(cursor, event.getDayId());
        m.setDayOpenPrice(cursor, event.getDayOpenPrice());
        m.setDayHighPrice(cursor, event.getDayHighPrice());
        m.setDayLowPrice(cursor, event.getDayLowPrice());
        m.setDayClosePrice(cursor, event.getDayClosePrice());
        m.setPrevDayId(cursor, event.getPrevDayId());
        m.setPrevDayClosePrice(cursor, event.getPrevDayClosePrice());
        m.setPrevDayVolumeDouble(cursor, event.getPrevDayVolume());
        m.setOpenInterest(cursor, (int) event.getOpenInterest());
        m.setFlags(cursor, event.getFlags());
        return cursor;
    }
// END: CODE AUTOMATICALLY GENERATED
}
