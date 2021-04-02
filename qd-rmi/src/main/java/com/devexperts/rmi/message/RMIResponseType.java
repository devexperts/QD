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
package com.devexperts.rmi.message;

public enum RMIResponseType {


    SUCCESS(0),
    ERROR(1);

    public int getId() {
        return id;
    }

    /**
     * Returns the {@link RMIResponseType} by index
     * @param id index
     * @return the type by index
     */
    public static RMIResponseType getById(int id) {
        return id >= 0 && id < TYPE_BY_ID.length ? TYPE_BY_ID[id] : null;
    }

    private static final RMIResponseType[] TYPE_BY_ID = new RMIResponseType[3];

    static {
        for (RMIResponseType type : values()) {
            if (TYPE_BY_ID[type.getId()] != null)
                throw new AssertionError("Duplicate id: " + type.getId());
            TYPE_BY_ID[type.getId()] = type;
        }
    }

    private final int id;

    RMIResponseType(int id) {
        this.id = id;
    }
}
