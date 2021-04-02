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
package com.devexperts.util;

/**
 * A typed key for {@link TypedMap}.
 * The instance of this class serves as a unique token (key) form storing values in the map.
 */
public final class TypedKey<T> {

    private final String description;

    /**
     * Creates new typed key. Key description is generated from the current position of the key in the class.
     */
    public TypedKey() {
        String description = getNameFromStackTrace();
        if (description == null)
            description = super.toString();
        this.description = description;
    }

    /**
     * Creates new typed key with the specified description.
     * Description is used only for debugging purposes when printing {@link TypedMap}.
     *
     * @param description description of the key.
     */
    public TypedKey(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }

    private static String getNameFromStackTrace() {
        Exception e = new RuntimeException();
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length < 3)
            return null;
        // First 2 stack traces contain this method and this class's constructor.
        return stackTrace[2].toString();
    }
}
