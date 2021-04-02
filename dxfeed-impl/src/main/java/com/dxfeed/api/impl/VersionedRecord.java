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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.util.MappingUtil;

public class VersionedRecord extends DefaultRecord {
    private final int iVersion;

    public VersionedRecord(int id, String recordName, boolean hasTime, DataIntField[] intFields, DataObjField[] objFields, String versionFieldName) {
        super(id, recordName, hasTime, intFields, objFields);
        iVersion = MappingUtil.findIntField(this, versionFieldName, true);
    }

    public boolean update(RecordCursor from, RecordCursor to) {
        if (from.getInt(iVersion) >= to.getInt(iVersion))
            return to.updateDataFrom(from);
        return false;
    }
}
