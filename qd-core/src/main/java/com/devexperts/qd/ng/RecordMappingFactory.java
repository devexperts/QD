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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataRecord;

public interface RecordMappingFactory {
    /**
     * Creates mapping for a specified record.
     * @return created mapping or {@code null} if record name is not recognized
     * @throws IllegalArgumentException if record name is recognized by some required fields are missing or
     *   there are other irregularities that make it impossible to create a mapping class.
     */
    public RecordMapping createMapping(DataRecord record);
}
