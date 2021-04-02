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

/**
 * Processes a batch of records from {@link RecordSource}.
 */
public interface RecordConsumer {
    /**
     * Processes all records from specified record source.
     * Note, that this method does not have to actually call {@link RecordSource#next() next} to retrieve each
     * record from the source. It may simply return if it needs to ignore this source. It may also scan the
     * source as many times as it needs using the methods of {@link RecordSource}.
     * The {@link RecordSource#getPosition() position} of the source after return of this method may be arbitrary.
     *
     * @param source the record source.
     */
    public void process(RecordSource source);
}
