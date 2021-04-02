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
package com.dxfeed.event.candle;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.impl.EventDelegateSet;
import com.dxfeed.event.candle.impl.CandleEventMapping;

import java.util.EnumSet;

public abstract class CandleEventDelegateImpl<T extends Candle> extends EventDelegate<T> {
    protected CandleEventDelegateImpl(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
    }

    @Override
    public abstract CandleEventMapping getMapping();

    @Override
    public EventDelegateSet<T, ? extends EventDelegate<T>> createDelegateSet() {
        return new CandleEventDelegateSet<>(eventType);
    }

    @Override
    public long getFetchTimeHeuristicByEventSymbolAndFromTime(Object eventSymbol, long fromTime) {
        CandlePeriod period = getMapping().getRecordPeriod();
        if (period == null)
            period = ((CandleSymbol) eventSymbol).getPeriod();
        if (period.getPeriodIntervalMillis() > TimeUtil.DAY) {
            // special handling of very large periods -- fetch two period before
            return fromTime - 2 * period.getPeriodIntervalMillis();
        } else
            // default behavior -- beginning of the previous trading day
            return super.getFetchTimeHeuristicByEventSymbolAndFromTime(eventSymbol, fromTime);
    }
}
