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

import com.devexperts.io.BufferedInput;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.message.RMIResponseType;
import com.devexperts.rmi.task.RMIChannelType;

import java.io.IOException;
import javax.annotation.Nonnull;

enum RMIMessageKind {
    /*
         Kinds are numbered from x40 to make sure they are single-byte in compact encoding, but do not intersect
         with legacy request and cancel types in any ways for the easiness for protocol-oblivious parsing.
     */
    REQUEST(0x40),
    SERVER_CHANNEL_REQUEST(0x41),
    CLIENT_CHANNEL_REQUEST(0x42),
    SUCCESS_RESPONSE(0x43),
    SERVER_CHANNEL_SUCCESS_RESPONSE(0x44),
    CLIENT_CHANNEL_SUCCESS_RESPONSE(0x45),
    ERROR_RESPONSE(0x46),
    SERVER_CHANNEL_ERROR_RESPONSE(0x47),
    CLIENT_CHANNEL_ERROR_RESPONSE(0x48),
    DESCRIBE_SUBJECT(0x49),
    DESCRIBE_OPERATION(0x4a),
    ADVERTISE(0x4b);

    private static final int KIND_BASE = 0x40;
    private static final int KIND_CNT = 12;

    static RMIMessageKind getById(int id) {
        return id >= KIND_BASE && id < KIND_BASE + KIND_CNT ? KIND_BY_ID[id - KIND_BASE] : null;
    }

    static RMIMessageKind getKind(RMIResponseMessage message, RMIChannelType type) {
        if (type == null)
            return message.getType() == RMIResponseType.SUCCESS ? SUCCESS_RESPONSE : ERROR_RESPONSE;
        if (type == RMIChannelType.CLIENT_CHANNEL)
            return message.getType() == RMIResponseType.SUCCESS
                ? CLIENT_CHANNEL_SUCCESS_RESPONSE : CLIENT_CHANNEL_ERROR_RESPONSE;
        return message.getType() == RMIResponseType.SUCCESS
            ? SERVER_CHANNEL_SUCCESS_RESPONSE : SERVER_CHANNEL_ERROR_RESPONSE;

    }

    @Nonnull
    public static RMIMessageKind readFromRequest(BufferedInput in) throws IOException {
        int id = in.readCompactInt();
        RMIMessageKind kind = getById(id);
        if (kind == null || !kind.isRequest())
            throw new IOException("Invalid request kind: " + Integer.toHexString(id));
        return kind.getOppositeSide();
    }

    public static RMIMessageKind readFromResponse(BufferedInput in) throws IOException {
        int id = in.readCompactInt();
        RMIMessageKind kind = getById(id);
        if (kind == null || !(kind.isSuccess() || kind.isError()))
            throw new IOException("Invalid response kind: " + Integer.toHexString(id));
        return kind.getOppositeSide();
    }

    private RMIMessageKind getOppositeSide() {
        switch (this) {
        case CLIENT_CHANNEL_ERROR_RESPONSE:
            return SERVER_CHANNEL_ERROR_RESPONSE;
        case CLIENT_CHANNEL_SUCCESS_RESPONSE:
            return SERVER_CHANNEL_SUCCESS_RESPONSE;
        case CLIENT_CHANNEL_REQUEST:
            return SERVER_CHANNEL_REQUEST;
        case SERVER_CHANNEL_REQUEST:
            return CLIENT_CHANNEL_REQUEST;
        case SERVER_CHANNEL_SUCCESS_RESPONSE:
            return CLIENT_CHANNEL_SUCCESS_RESPONSE;
        case SERVER_CHANNEL_ERROR_RESPONSE:
            return CLIENT_CHANNEL_ERROR_RESPONSE;
        default:
            return this;
        }
    }

    private static final RMIMessageKind[] KIND_BY_ID = new RMIMessageKind[KIND_CNT];

    static {
        for (RMIMessageKind kind : values()) {
            if (KIND_BY_ID[kind.getId() - KIND_BASE] != null)
                throw new AssertionError("Duplicate id: " + kind.getId());
            KIND_BY_ID[kind.getId() - KIND_BASE] = kind;
        }
    }

    private final int id;

    RMIMessageKind(int id) {
        this.id = id;
    }

    boolean isRequest() {
        return this == REQUEST || this == SERVER_CHANNEL_REQUEST || this == CLIENT_CHANNEL_REQUEST;
    }

    boolean isError() {
        return this == CLIENT_CHANNEL_ERROR_RESPONSE  || this == SERVER_CHANNEL_ERROR_RESPONSE ||  this == ERROR_RESPONSE;
    }

    boolean isSuccess() {
        return this == CLIENT_CHANNEL_SUCCESS_RESPONSE || this == SERVER_CHANNEL_SUCCESS_RESPONSE || this == SUCCESS_RESPONSE;
    }

    boolean hasChannel() {
        return (this == CLIENT_CHANNEL_REQUEST || this == CLIENT_CHANNEL_ERROR_RESPONSE
            || this == CLIENT_CHANNEL_SUCCESS_RESPONSE) || (this == SERVER_CHANNEL_REQUEST
            || this == SERVER_CHANNEL_ERROR_RESPONSE || this == SERVER_CHANNEL_SUCCESS_RESPONSE);
    }

    boolean hasServer() {
        return this == SERVER_CHANNEL_REQUEST || this == SERVER_CHANNEL_ERROR_RESPONSE || this == SERVER_CHANNEL_SUCCESS_RESPONSE;
    }

    boolean hasClient() {
        return this == CLIENT_CHANNEL_REQUEST || this == CLIENT_CHANNEL_ERROR_RESPONSE || this == CLIENT_CHANNEL_SUCCESS_RESPONSE;
    }

    int getId() {
        return id;
    }
}
