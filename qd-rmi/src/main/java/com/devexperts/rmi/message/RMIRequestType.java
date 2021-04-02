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

import com.devexperts.io.BufferedInput;

import java.io.IOException;

/**
 * Types of {@link RMIRequestMessage reqeusts}.
 */
public enum RMIRequestType {

    /**
     * Default two-way request with response from the server.
     */
    DEFAULT(0),

    /**
     * One-way request that does not wait response from the server.
     */
    ONE_WAY(1);

    /**
     * Returns protocol identifier of this request type.
     * @return protocol identifier of this request type.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the {@link RMIRequestType} by index
     * @param id index
     * @return the type by index
     */
    public static RMIRequestType getById(int id) {
        return id >= 0 && id < TYPE_BY_ID.length ? TYPE_BY_ID[id] : null;
    }

    public static RMIRequestType readFromRequest(BufferedInput in) throws IOException {
        int id = in.readCompactInt();
        RMIRequestType type = getById(id);
        if (type == null)
            throw new IOException("Invalid request type: " + Integer.toHexString(id));
        return type;
    }

    private static final RMIRequestType[] TYPE_BY_ID = new RMIRequestType[3];

    static {
        for (RMIRequestType type : values()) {
            if (TYPE_BY_ID[type.getId()] != null)
                throw new AssertionError("Duplicate id: " + type.getId());
            TYPE_BY_ID[type.getId()] = type;
        }
    }

    private final int id;

    RMIRequestType(int id) {
        this.id = id;
    }
}
