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

public class BuiltinFields {
    /**
     * The name of virtual field that stores Symbol (text formats only).
     * @deprecated Use {@link #EVENT_SYMBOL_FIELD_NAME}
     */
    public static final String SYMBOL_FIELD_NAME = "Symbol";

    /**
     * All built-in event fields are prefixed with this string.
     * When built-in field are described in {@link MessageType#DESCRIBE_RECORDS DESCRIBE_RECORDS} message as first one, they
     * are always transmitted even when special message flags like {@link com.devexperts.qd.ng.EventFlag#REMOVE_EVENT REMOVE_EVENT} are used.
     */
    public static final String EVENT_FIELDS_PREFIX = "Event";

    /**
     * This pseudo field name is reserved to represent the symbol and cannot
     * be used as any kind of field name.
     */
    public static final String EVENT_SYMBOL_FIELD_NAME = "EventSymbol";

    /**
     * This pseudo field name is reserved to represent event flags and cannot
     * be used as any kind of field name.
     */
    public static final String EVENT_FLAGS_FIELD_NAME = "EventFlags";

    /**
     * The name of virtual field that stores EventTime.
     */
    public static final String EVENT_TIME_FIELD_NAME = "EventTime";

    /**
     * The name of virtual field that stores EventSequence.
     */
    public static final String EVENT_SEQUENCE_FIELD_NAME = "EventSequence";

    private BuiltinFields() {} // do not create
}
