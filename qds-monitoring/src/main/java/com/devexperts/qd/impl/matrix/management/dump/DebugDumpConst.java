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
package com.devexperts.qd.impl.matrix.management.dump;

class DebugDumpConst {
    static final int UNKNOWN = 0;
    static final int REFERENCE = 0;
    static final int STRING = -1;
    static final int BOOLEAN = -2;
    static final int BYTE = -3;
    static final int SHORT = -4;
    static final int CHAR = -5;
    static final int INT = -6;
    static final int LONG = -7;
    static final int FLOAT = -8;
    static final int DOUBLE = -9;

    static final int OWNER = 1;
    static final int VERSION = 2;
    static final int SYSTEM_PROPERTIES = 3;
    static final int EXCEPTION = 4;

    static final int FIRST_CLASS_ID = -16;
    static final int FIRST_OBJECT_ID = 16;
}
