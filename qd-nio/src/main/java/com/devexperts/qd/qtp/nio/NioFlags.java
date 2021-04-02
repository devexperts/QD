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
package com.devexperts.qd.qtp.nio;

class NioFlags {
    // bit 0 - set when not selectable (no reading interest at selector)
    // bit 1 - set when being processing
    // bit 2 - set when application connection is not ready for more data
    // ----- i.e.: -----
    // 0 - selectable, no processing
    // 1 - not selectable, no processing <-> in tasks queue
    // 2 - selectable, processing
    // 3 - not selectable, processing (was already selected again while being processed)
    // 4-7 - same as 0-3, but the application connection is not ready to process data
    static final int RS_NOT_SELECTABLE = 1;
    static final int RS_PROCESSING = 2;
    static final int RS_NOT_READY_FOR_MORE = 4;

    // bit 0 - set when application connection has more data
    // bit 1 - set when being processing
    // ----- i.e.: -----
    // 0 - ready, no app data
    // 1 - in queue, has app data <-> in tasks queue
    // 2 - processing, no more app data
    // 3 - processing, has more app data
    static final int WS_MORE_DATA = 1;
    static final int WS_PROCESSING = 2;
}
