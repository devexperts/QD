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
package com.devexperts.mars.common;

public class MARSEventFactory {

    private static MARSEventFactory instance = new MARSEventFactory();

    public static MARSEventFactory getInstance() {
        return instance;
    }

    protected static void setInstance(MARSEventFactory instance) {
        MARSEventFactory.instance = instance;
    }

    public MARSEvent createMARSEvent(String name, String value) {
        return new MARSEvent(name, value);
    }

    public MARSEvent createMARSEvent(String name, String value, long timestamp) {
        return new MARSEvent(name, value, timestamp);
    }
}
