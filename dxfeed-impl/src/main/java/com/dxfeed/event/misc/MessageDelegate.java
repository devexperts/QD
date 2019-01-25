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
package com.dxfeed.event.misc;

import java.util.EnumSet;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.misc.impl.MessageMapping;

public final class MessageDelegate extends EventDelegate<Message> {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final MessageMapping m;

    public MessageDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
        m = record.getMapping(MessageMapping.class);
    }

    @Override
    public MessageMapping getMapping() {
        return m;
    }

    @Override
    public Message createEvent() {
        return new Message();
    }

    @Override
    public Message getEvent(Message event, RecordCursor cursor) {
        super.getEvent(event, cursor);
        event.setMarshalledAttachment(m.getMessage(cursor));
        return event;
    }

    @Override
    public RecordCursor putEvent(Message event, RecordBuffer buf) {
        RecordCursor cursor = super.putEvent(event, buf);
        m.setMessage(cursor, event.getMarshalledAttachment());
        return cursor;
    }
// END: CODE AUTOMATICALLY GENERATED
}
