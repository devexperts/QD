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
package com.devexperts.qd.ng;

import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Event flags.
 */
public enum EventFlag {
    /* ========== AHTUNG ==========
     * The constants below must be synchronized with similar constants in dxFeed API IndexedEvent class!!!
     */

    TX_PENDING(0x01, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    REMOVE_EVENT(0x02, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    SNAPSHOT_BEGIN(0x04, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    SNAPSHOT_END(0x08, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    SNAPSHOT_SNIP(0x10, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    // 0x20 is reserved. This flag will fit into 1-byte on the wire in QTP protocol
    SNAPSHOT_MODE(0x40, ProtocolOption.HISTORY_SNAPSHOT,
        MessageType.TICKER_DATA, MessageType.STREAM_DATA, MessageType.HISTORY_DATA, MessageType.RAW_DATA),
    REMOVE_SYMBOL(0x80, null);

    // ======================= instance =======================

    private final int flag;
    private final ProtocolOption opt;
    private final MessageType[] messageTypes;

    EventFlag(int flag, ProtocolOption opt, MessageType... messageTypes) {
        this.flag = flag;
        this.opt = opt;
        this.messageTypes = messageTypes;
        Arrays.sort(this.messageTypes);
    }

    public int flag() {
        return flag;
    }

    public int of(boolean b) {
        return b ? flag : 0;
    }

    public boolean in(int eventFlags) {
        return (eventFlags & flag) != 0;
    }

    public int set(int eventFlags) {
        return eventFlags | flag;
    }

    public int clear(int eventFlags) {
        return eventFlags & ~flag;
    }

    // ======================= static =======================

    private static final EventFlag[][] VALUES_BY_MESSAGE_TYPE;
    private static final int[] MASKS_BY_MESSAGE_TYPE;
    private static final EventFlag[] ALL_FLAGS = values();
    private static final int ALL_FLAGS_MASK;
    private static final String[] FLAGS_STRING_CACHE;

    static {
        MessageType[] messageTypes = MessageType.values();
        VALUES_BY_MESSAGE_TYPE = new EventFlag[messageTypes.length][];
        for (EventFlag flag : values()) {
            for (MessageType messageType : flag.messageTypes) {
                if (VALUES_BY_MESSAGE_TYPE[messageType.ordinal()] == null)
                    VALUES_BY_MESSAGE_TYPE[messageType.ordinal()] = collectValuesByMessageType(messageType);
            }
        }
        MASKS_BY_MESSAGE_TYPE = new int[VALUES_BY_MESSAGE_TYPE.length];
        for (int i = 0; i < MASKS_BY_MESSAGE_TYPE.length; i++) {
            if (VALUES_BY_MESSAGE_TYPE[i] != null) {
                for (EventFlag flag : VALUES_BY_MESSAGE_TYPE[i])
                    MASKS_BY_MESSAGE_TYPE[i] |= flag.flag;
            }
        }
        int allFlags = 0;
        for (EventFlag flag : values())
            allFlags |= flag.flag;
        ALL_FLAGS_MASK = allFlags;
        FLAGS_STRING_CACHE = new String[allFlags + 1];
        FLAGS_STRING_CACHE[0] = "";
        for (EventFlag flag : values())
            FLAGS_STRING_CACHE[flag.flag] = flag.name();
    }

    private static EventFlag[] collectValuesByMessageType(MessageType messageType) {
        List<EventFlag> list = new ArrayList<>();
        for (EventFlag flag : values()) {
            if (Arrays.binarySearch(flag.messageTypes, messageType) >= 0)
                list.add(flag);
        }
        return list.toArray(new EventFlag[list.size()]);
    }

    /**
     * Returns a mask of supported event flags for a given set of protocol options and message type.
     * @param optSet the set of protocol options.
     * @param messageType the message type.
     * @return mask of supported event flags.
     */
    public static int getSupportedEventFlags(ProtocolOption.Set optSet, MessageType messageType) {
        EventFlag[] flags = VALUES_BY_MESSAGE_TYPE[messageType.ordinal()];
        if (flags == null)
            return 0;
        int result = 0;
        for (EventFlag flag : flags) {
            if (optSet.contains(flag.opt))
                result |= flag.flag;
        }
        return result;
    }

    /**
     * Converts integer event flags bit mask into an event flags set string.
     * Flags that are not supported for this message types are omitted.
     *
     * @param eventFlags event flags bit mask.
     * @param messageType the message type.
     * @return event flags set string.
     */
    public static String formatEventFlags(int eventFlags, MessageType messageType) {
        if (eventFlags == 0)
            return "";
        return formatEventFlags(eventFlags & MASKS_BY_MESSAGE_TYPE[messageType.ordinal()]);
    }

    /**
     * Converts integer event flags bit mask into an event flags set string.
     * All flags (even non-supported) are output.
     *
     * @param eventFlags event flags bit mask.
     * @return event flags set string.
     */
    public static String formatEventFlags(int eventFlags) {
        if (eventFlags == 0)
            return "";
        int knownFlags = eventFlags & ALL_FLAGS_MASK;
        String s = FLAGS_STRING_CACHE[knownFlags];
        if (s != null && eventFlags == knownFlags)
            return s;
        return formatEventFlagsImpl(eventFlags);
    }

    private static String formatEventFlagsImpl(int eventFlags) {
        StringBuilder sb = new StringBuilder();
        for (EventFlag flag : ALL_FLAGS) {
            if ((eventFlags & flag.flag) != 0) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(flag);
            }
        }
        int knownFlags = eventFlags & ALL_FLAGS_MASK;
        if (FLAGS_STRING_CACHE[knownFlags] == null)
            FLAGS_STRING_CACHE[knownFlags] = sb.toString();
        if (eventFlags != knownFlags) {
            // output unsupported flags
            if (sb.length() > 0)
                sb.append(",");
            sb.append("0x").append(Integer.toHexString(eventFlags ^ knownFlags));
        }
        return sb.toString();
    }

    /**
     * Parses string set of event flags.
     * The result is an empty set when s is null, empty, or cannot be parsed.
     *
     * @param s string set of event flags.
     * @param messageType the message type.
     * @return set of event flags.
     */
    public static int parseEventFlags(String s, MessageType messageType) {
        if (s == null || s.isEmpty())
            return 0;
        EventFlag[] flags = VALUES_BY_MESSAGE_TYPE[messageType.ordinal()];
        if (flags == null)
            return 0;
        int eventFlags = 0;
        for (StringTokenizer st = new StringTokenizer(s, ","); st.hasMoreTokens();) {
            String t = st.nextToken();
            for (EventFlag flag : flags) {
                if (t.equals(flag.toString())) {
                    eventFlags |= flag.flag;
                    break;
                }
            }
        }
        return eventFlags;
    }
}
