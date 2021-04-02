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
package {{PACKAGE}};

import java.util.EnumSet;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegateFlags;

public final class {{CLASS_NAME}} extends {{SUPER_CLASS}} {
// BEGIN: CODE AUTOMATICALLY GENERATED: THIS IS ONLY A TEMPLATE. CONTENTS BELOW ARE IRRELEVANT
    public {{CLASS_NAME}}(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
    }

    @Override
    public RecordMapping getMapping() {
        return null;
    }

    @Override
    public EventType<?> createEvent() {
        return null;
    }

    @Override
    public EventType<?> getEvent(EventType<?> event, RecordCursor cursor) {
        return null;
    }
// END: CODE AUTOMATICALLY GENERATED
}
