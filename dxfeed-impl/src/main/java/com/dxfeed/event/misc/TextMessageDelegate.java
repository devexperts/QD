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
package com.dxfeed.event.misc;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.misc.impl.TextMessageMapping;

import java.util.EnumSet;

public final class TextMessageDelegate extends EventDelegate<TextMessage> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final TextMessageMapping m;

    public TextMessageDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(TextMessageMapping.class);
    }

    @Override
    public TextMessageMapping getMapping() {
        return m;
    }

    @Override
    public TextMessage createEvent() {
        return new TextMessage();
    }

    @Override
    public TextMessage getEvent(TextMessage event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setTimeSequence((((long) m.getTimeSeconds(cursor)) << 32) | (m.getSequence(cursor) & 0xFFFFFFFFL));
        event.setText(m.getText(cursor));
        return event;
    }

    @Override
    public RecordCursor putEvent(TextMessage event, RecordBuffer buf) {
        RecordCursor cursor = super.putEvent(event, buf);
        m.setTimeSeconds(cursor, (int) (event.getTimeSequence() >>> 32));
        m.setSequence(cursor, (int) event.getTimeSequence());
        m.setText(cursor, event.getText());
        return cursor;
    }
// END: CODE AUTOMATICALLY GENERATED
}