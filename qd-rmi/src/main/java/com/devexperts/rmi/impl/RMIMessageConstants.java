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
package com.devexperts.rmi.impl;

class RMIMessageConstants {
    static final int REQUEST_TYPE_MASK = 0x0f;
    static final int REQUEST_WITH_ROUTE = 0x20;
    static final int REQUEST_WITH_TARGET = 0x10;
    static final int REQUEST = 0x30;
    static final int CHANNEL_REQUEST = 0x40;
//  static final int

    private RMIMessageConstants() {} // do not create

}
