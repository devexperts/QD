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
package com.dxfeed.api.codegen.event;

import com.dxfeed.annotation.ClassValueMapping;

/**
 * Testing wrapped values serialization
 */
public class WrappedInt {
    private final int value;

    @ClassValueMapping
    public WrappedInt(int value) {
        this.value = value;
    }

    @ClassValueMapping
    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "WrappedInt{" +
            "value=" + value +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WrappedInt that = (WrappedInt) o;

        if (value != that.value) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return value;
    }
}
