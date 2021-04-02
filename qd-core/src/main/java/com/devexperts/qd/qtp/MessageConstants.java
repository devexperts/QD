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
package com.devexperts.qd.qtp;

/**
 * The <code>MessageConstants</code> defines constants for standard QTP messages.
 */
@SuppressWarnings("ALL")
interface MessageConstants {

    // ========== Message Types ==========

    public static final int MESSAGE_HEARTBEAT = 0;

    public static final int MESSAGE_DESCRIBE_PROTOCOL = 1;
    public static final int MESSAGE_DESCRIBE_RECORDS = 2;
    public static final int MESSAGE_DESCRIBE_RESERVED = 3;

    // Message parts are used to disassemble large messages into small parts so that they can be multiplexed
    // with other messages or message parts. Message parts are assembled on receiving side into original message.
    // Disassembled messages are considered "arrived" when all their parts are received and are ready for assembling.
    // Disassembled messages are assigned unique message sequence numbers which have no meaning themselves.
    // Particular sequence of disassembled message parts may be cancelled before being completely sent by sending
    // a part message consisting only of corresponding message sequence number (with empty message body part).
    // In such case the receiving party behaves as if it has never received any parts of the corresponding message.
    public static final int MESSAGE_PART = 4; // compact long message sequence number, part of wrapped message body including length and id (in first part only)

    public static final int MESSAGE_RAW_DATA = 5;

    public static final int MESSAGE_TICKER_DATA = 10;
    public static final int MESSAGE_TICKER_ADD_SUBSCRIPTION = 11;
    public static final int MESSAGE_TICKER_REMOVE_SUBSCRIPTION = 12;

    public static final int MESSAGE_STREAM_DATA = 15;
    public static final int MESSAGE_STREAM_ADD_SUBSCRIPTION = 16;
    public static final int MESSAGE_STREAM_REMOVE_SUBSCRIPTION = 17;

    public static final int MESSAGE_HISTORY_DATA = 20;
    public static final int MESSAGE_HISTORY_ADD_SUBSCRIPTION = 21;
    public static final int MESSAGE_HISTORY_REMOVE_SUBSCRIPTION = 22;

    // This message is supported for versions of RMI protocol with channels
    public static final int MESSAGE_RMI_ADVERTISE_SERVICES = 49; // byte[] + int RMIServiceId, int distance, int nInterNodes, byte[] + int EndpointIdNode, int nProps, byte[] keyProp, byte[] keyValue
    public static final int MESSAGE_RMI_DESCRIBE_SUBJECT = 50; // int id, byte[] subject
    public static final int MESSAGE_RMI_DESCRIBE_OPERATION = 51; // int id, byte[] operation (e.g. "service_name.ping()void")
    public static final int MESSAGE_RMI_REQUEST = 52; // int request_id, int RMIKind, [optional channel_id], int request_type, RMIRoute route, RMIServiceId target, int subject_id, int operation_id, Object[] parameters
    public static final int MESSAGE_RMI_CANCEL = 53; // <<LEGACY>> int request_id, int flags
    public static final int MESSAGE_RMI_RESULT = 54; // <<LEGACY>> int request_id, Object result, [optional route]
    public static final int MESSAGE_RMI_ERROR = 55; // <<LEGACY>> int request_id, int RMIExceptionType#getId(), byte[] causeMessage, byte[] serializedCause, [optional route]
    public static final int MESSAGE_RMI_RESPONSE = 56; // int request_id, int RMIKind, [optional channel_id],  RMIRoute route, byte[] serializedResult

    public static final int MAX_SUPPORTED_MESSAGE_TYPE = 63; // must fit into long mask

    /*
     * This value is reserved and can not be used as any message type. Point is that when serialized to a file
     * values 61 corresponds to ASCII symbol '=', which is used in text data format. So, when we read this values
     * in binary format it means that we are actually reading data not in binary, but in text format.
     */
    public static final int MESSAGE_TEXT_FORMAT = 0x3d; // 61 decimal, '=' char

    /**
     * This value is reserved and can not be used as any message type. It is the second byte of zip file compression.
     */
    public static final int MESSAGE_ZIP_COMPRESSION = 0x4b; // 75 decimal, 'K' char

    /**
     * This value is reserved and can not be used as any message type. It is the second byte of gzip file compression.
     */
    public static final int MESSAGE_GZIP_COMPRESSION = 0x8b; // 139 decimal, '<' char
}
