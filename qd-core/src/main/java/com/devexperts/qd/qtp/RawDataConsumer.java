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
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataIterator;

/**
 * Marks {@link MessageConsumer} implementation as being able to directly handle {@link MessageType#RAW_DATA} messages\
 * with {@link #processData(DataIterator, MessageType)} methods.
 */
public interface RawDataConsumer {
    public void processData(DataIterator iterator, MessageType message);
}
