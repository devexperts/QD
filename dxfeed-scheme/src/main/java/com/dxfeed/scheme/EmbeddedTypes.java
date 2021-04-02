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
package com.dxfeed.scheme;

import com.devexperts.qd.SerialFieldType;

/**
 * Interface to provide mapping between types' names and internal field type implementation for
 * embedded, non-custom types.
 */
public interface EmbeddedTypes {
    /**
     * Checks if type name is known.
     *
     * @param type name of type.
     * @return {@code true} if type is known and supported, {@code false} otherwise.
     */
    public default boolean isKnownType(String type) {
        return getSerialType(type) != null;
    }

    /**
     * Converts type's name to internal type representation.
     *
     * @param type name of type.
     * @return internal representation if type is known, {@code null} otherwise.
     */
    public SerialFieldType getSerialType(String type);

    /**
     * Checks if type is integer which could be subdivided into bitfields.
     *
     * @param type name of type.
     * @return {@code true} if type is known and it can be subdivided into bitfields, {@code false} otherwise.
     */
    public boolean canHaveBitfields(String type);
}
