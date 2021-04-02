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

import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.devexperts.qd.qtp.MessageConstants.MAX_SUPPORTED_MESSAGE_TYPE;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_PROTOCOL;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_RECORDS;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_RESERVED;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_HEARTBEAT;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_HISTORY_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_HISTORY_DATA;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_HISTORY_REMOVE_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_PART;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RAW_DATA;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_ADVERTISE_SERVICES;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_CANCEL;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_DESCRIBE_OPERATION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_DESCRIBE_SUBJECT;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_ERROR;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_REQUEST;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_RESPONSE;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_RMI_RESULT;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_STREAM_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_STREAM_DATA;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_STREAM_REMOVE_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_TEXT_FORMAT;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_TICKER_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_TICKER_DATA;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_TICKER_REMOVE_SUBSCRIPTION;

/**
 * Type of QTP message.
 *
 * @see MessageConstants
 */
public enum MessageType {

    HEARTBEAT(MESSAGE_HEARTBEAT),

    DESCRIBE_PROTOCOL(MESSAGE_DESCRIBE_PROTOCOL),
    DESCRIBE_RECORDS(MESSAGE_DESCRIBE_RECORDS),
    DESCRIBE_RESERVED(MESSAGE_DESCRIBE_RESERVED),

    PART(MESSAGE_PART),

    RAW_DATA(MESSAGE_RAW_DATA, Flag.DATA),

    TICKER_DATA(MESSAGE_TICKER_DATA, Flag.TICKER, Flag.DATA),
    TICKER_ADD_SUBSCRIPTION(MESSAGE_TICKER_ADD_SUBSCRIPTION, Flag.TICKER, Flag.ADDSUB),
    TICKER_REMOVE_SUBSCRIPTION(MESSAGE_TICKER_REMOVE_SUBSCRIPTION, Flag.TICKER, Flag.REMSUB),

    STREAM_DATA(MESSAGE_STREAM_DATA, Flag.STREAM, Flag.DATA),
    STREAM_ADD_SUBSCRIPTION(MESSAGE_STREAM_ADD_SUBSCRIPTION, Flag.STREAM, Flag.ADDSUB),
    STREAM_REMOVE_SUBSCRIPTION(MESSAGE_STREAM_REMOVE_SUBSCRIPTION, Flag.STREAM, Flag.REMSUB),

    HISTORY_DATA(MESSAGE_HISTORY_DATA, Flag.HISTORY, Flag.DATA),
    HISTORY_ADD_SUBSCRIPTION(MESSAGE_HISTORY_ADD_SUBSCRIPTION, Flag.HISTORY, Flag.ADDSUB),
    HISTORY_REMOVE_SUBSCRIPTION(MESSAGE_HISTORY_REMOVE_SUBSCRIPTION, Flag.HISTORY, Flag.REMSUB),

    RMI_ADVERTISE_SERVICES(MESSAGE_RMI_ADVERTISE_SERVICES, Flag.RMIADS),
    RMI_DESCRIBE_SUBJECT(MESSAGE_RMI_DESCRIBE_SUBJECT, Flag.RMIREQ, Flag.RMICHAN),
    RMI_DESCRIBE_OPERATION(MESSAGE_RMI_DESCRIBE_OPERATION, Flag.RMIREQ, Flag.RMICHAN),
    RMI_REQUEST(MESSAGE_RMI_REQUEST, Flag.RMIREQ, Flag.RMICHAN),
    RMI_CANCEL(MESSAGE_RMI_CANCEL, Flag.RMIREQ), // <<LEGACY>>
    RMI_RESPONSE(MESSAGE_RMI_RESPONSE, Flag.RMIREQ, Flag.RMICHAN),
    RMI_RESULT(MESSAGE_RMI_RESULT, Flag.RMIRES), // <<LEGACY>>
    RMI_ERROR(MESSAGE_RMI_ERROR, Flag.RMIRES), // <<LEGACY>>

    /*
     * This value is reserved and can not be used as any message type. Point is that when serialized to a file
     * values 61 corresponds to ASCII symbol '=', which is used in text data format. So, when we read this values
     * in binary format it means that we are actually reading data not in binary, but in text format.
     */
    TEXT_FORMAT(MESSAGE_TEXT_FORMAT);

    private static final MessageType[] types; // cached copy of "values()" - to avoid garbage.
    private static final MessageType[] typeById; // [id] -> type for that id. will fail for crazy ids.

    static {
        types = values();
        int maxId = 0;
        for (MessageType type : types)
            maxId = Math.max(maxId, type.id);
        typeById = new MessageType[maxId + 1];
        for (MessageType type : types)
            typeById[type.id] = type;
    }


    public enum Flag { TICKER, STREAM, HISTORY, DATA, ADDSUB, REMSUB, RMIREQ, RMIRES, RMIADS, RMICHAN }

    private final int id;

    private final Collection<Flag> flags;
    private final boolean data;
    private final boolean subscriptionAdd;
    private final boolean subscriptionRemove;
    private final boolean hasRecords;
    private final boolean stream;
    private final boolean history;
    private final boolean historySubscriptionAdd;
    private final boolean rmiReq;
    private final boolean rmiRes;
    private final boolean rmiChan;
    private final boolean rmiAds;
    private final QDContract contract;
    private final RecordMode recordMode;

    int cannotReorderWithMask;

    static {
        if (types.length > 32)
            throw new AssertionError("Only 32 messages types are supported in cannotReorderWithMask");
        // initialize cannotReorderWithMask
        for (MessageType type1 : types) {
            for (MessageType type2 : types) {
                if (type1 == type2)
                    continue;
                if (type1.isData() && type2.isData() && (type1 == RAW_DATA || type2 == RAW_DATA) ||
                    type1.isSubscription() && type2.isSubscription() && type1.getContract() == type2.getContract())
                {
                    type1.cannotReorderWithMask |= 1 << type2.ordinal();
                }
            }
        }
    }

    MessageType(int id, Flag... flags) {
        if (id > MAX_SUPPORTED_MESSAGE_TYPE)
            throw new IllegalArgumentException();
        this.id = id;
        Collection<Flag> f = Arrays.asList(flags);
        this.flags = Collections.unmodifiableCollection(f);
        data = f.contains(Flag.DATA);
        subscriptionAdd = f.contains(Flag.ADDSUB);
        subscriptionRemove = f.contains(Flag.REMSUB);
        hasRecords = data || subscriptionAdd || subscriptionRemove;
        stream = f.contains(Flag.STREAM);
        history = f.contains(Flag.HISTORY);
        historySubscriptionAdd = history && subscriptionAdd;
        rmiReq = f.contains(Flag.RMIREQ);
        rmiRes = f.contains(Flag.RMIRES);
        rmiChan = f.contains(Flag.RMICHAN);
        rmiAds = f.contains(Flag.RMIADS);
        contract = f.contains(Flag.TICKER) ? QDContract.TICKER :
            f.contains(Flag.STREAM) ? QDContract.STREAM :
            f.contains(Flag.HISTORY) ? QDContract.HISTORY : null;
        recordMode = f.contains(Flag.DATA) ?
                // Data modes -- always read flags in any data mode
                RecordMode.FLAGGED_DATA :
                // Add sub modes
                f.contains(Flag.ADDSUB) ?
                    (f.contains(Flag.HISTORY) ? RecordMode.HISTORY_SUBSCRIPTION : RecordMode.SUBSCRIPTION) :
                // Remove sub modes
                f.contains(Flag.REMSUB) ?
                    RecordMode.SUBSCRIPTION :
                // Message types without records
                null;
    }

    public Collection<Flag> getFlags() {
        return flags;
    }

    /**
     * Returns message identified in QTP protocol.
     */
    public int getId() {
        return id;
    }

    public boolean isData() {
        return data;
    }

    public boolean isSubscription() {
        return subscriptionAdd || subscriptionRemove;
    }

    public boolean isSubscriptionAdd() {
        return subscriptionAdd;
    }

    public boolean isSubscriptionRemove() {
        return subscriptionRemove;
    }

    public boolean hasRecords() {
        return hasRecords;
    }

    public boolean isStream() {
        return stream;
    }

    public boolean isHistory() {
        return history;
    }

    public boolean isHistorySubscriptionAdd() {
        return historySubscriptionAdd;
    }

    public boolean isRMIReq() {
        return rmiReq;
    }

    public boolean isRMIRes() {
        return rmiRes;
    }

    public boolean isRMIChan() {
        return rmiChan;
    }

    public boolean isRMIAds() {
        return rmiAds;
    }

    public QDContract getContract() {
        return contract;
    }

    public RecordMode getRecordMode() {
        return recordMode;
    }

    /**
     * Returns message type with a given id or {@code null} if the message type is not known.
     */
    public static MessageType findById(int id) {
        return id >= 0 && id < typeById.length ? typeById[id] : null;
    }

    public static MessageType findByName(String name) {
        for (MessageType type : types)
            if (type.name().equals(name))
                return type;
        return null;
    }

    // ---------- Message types by for QD contracts ----------

    private static final MessageType[] DATA_BY_CONTRACT = new MessageType[QDContract.values().length];
    private static final MessageType[] ADD_SUB_BY_CONTRACT = new MessageType[QDContract.values().length];
    private static final MessageType[] REMOVE_SUB_BY_CONTRACT = new MessageType[QDContract.values().length];
    static {
        for (MessageType type : types)
            if (type.contract != null)
                if (type.flags.contains(Flag.DATA))
                    DATA_BY_CONTRACT[type.contract.ordinal()] = type;
                else if (type.flags.contains(Flag.ADDSUB))
                    ADD_SUB_BY_CONTRACT[type.contract.ordinal()] = type;
                else if (type.flags.contains(Flag.REMSUB))
                    REMOVE_SUB_BY_CONTRACT[type.contract.ordinal()] = type;
    }

    public static MessageType forData(QDContract contract) {
        return DATA_BY_CONTRACT[contract.ordinal()];
    }

    public static MessageType forAddSubscription(QDContract contract) {
        return ADD_SUB_BY_CONTRACT[contract.ordinal()];
    }

    public static MessageType forRemoveSubscription(QDContract contract) {
        return REMOVE_SUB_BY_CONTRACT[contract.ordinal()];
    }
}
