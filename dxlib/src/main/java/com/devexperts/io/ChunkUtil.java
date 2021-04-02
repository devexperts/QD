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
package com.devexperts.io;

import com.devexperts.util.SystemProperties;

class ChunkUtil {
    private ChunkUtil() {} // do not create

    static final int INITIAL_CHUNK_LIST_CAPACITY =
        SystemProperties.getIntProperty("com.devexperts.io.initialChunkListCapacity", 15, 0, Integer.MAX_VALUE);

    static final Object READ_ONLY_OWNER = new Object(); // for read-only chunks
    static final Object GARBAGE_OWNER = new Object(); // for chunks that are to be collected by GC and may no longer be used anywhere

    static String ownerString(Object owner) {
        return owner == null ? "null" : owner.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(owner));
    }
}
