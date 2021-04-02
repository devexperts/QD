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

import java.util.ArrayList;
import java.util.Collection;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.ng.RecordMappingFactory;
import com.dxfeed.api.impl.*;

public final class {{CLASS_NAME}} extends EventDelegateFactory implements RecordMappingFactory {
// BEGIN: CODE AUTOMATICALLY GENERATED: THIS IS ONLY A TEMPLATE. CONTENTS BELOW ARE IRRELEVANT
    @Override
    public void buildScheme(SchemeBuilder builder) {}

    @Override
    public Collection<EventDelegate<?>> createDelegates(DataRecord record) {
        Collection<EventDelegate<?>> result = new ArrayList<>();
        return result;
    }

    @Override
    public Collection<EventDelegate<?>> createStreamOnlyDelegates(DataRecord record) {
        Collection<EventDelegate<?>> result = new ArrayList<>();
        return result;
    }

    @Override
    public RecordMapping createMapping(DataRecord record) {
        return null;
    }
// END: CODE AUTOMATICALLY GENERATED
}
