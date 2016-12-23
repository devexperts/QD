/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.option;

import java.util.EnumSet;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.option.impl.UnderlyingMapping;

public final class UnderlyingDelegate extends EventDelegate<Underlying> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final UnderlyingMapping m;

    public UnderlyingDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(UnderlyingMapping.class);
    }

    @Override
    public UnderlyingMapping getMapping() {
        return m;
    }

    @Override
    public Underlying createEvent() {
        return new Underlying();
    }

    @Override
    public Underlying getEvent(Underlying event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setVolatility(m.getVolatility(cursor));
        event.setFrontVolatility(m.getFrontVolatility(cursor));
        event.setBackVolatility(m.getBackVolatility(cursor));
        event.setPutCallRatio(m.getPutCallRatio(cursor));
        return event;
    }

    @Override
    public RecordCursor putEvent(Underlying event, RecordBuffer buf) {
        RecordCursor cursor = super.putEvent(event, buf);
        m.setVolatility(cursor, event.getVolatility());
        m.setFrontVolatility(cursor, event.getFrontVolatility());
        m.setBackVolatility(cursor, event.getBackVolatility());
        m.setPutCallRatio(cursor, event.getPutCallRatio());
        return cursor;
    }
// END: CODE AUTOMATICALLY GENERATED
}
