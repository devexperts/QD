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
package com.dxfeed.annotation;

/**
 * Semantic type of event class field. It is used to instruct the annotation processor to use appropriate serialized form.
 */
public enum EventFieldType {
    /**
     * Auto-detect type.
     */
    DEFAULT,

    /**
     * This field is not mapped to QD.
     */
    TRANSIENT,

    /** @deprecated Use {@link #TIME_SECONDS} instead. */
    @Deprecated
    TIME,

    /**
     * This type can be used for {@code long} properties that store time in milliseconds since Java Epoch.
     * It will be mapped to a QD field that keeps <b>seconds</b> since Java Epoch (milliseconds will be lost).
     */
    TIME_SECONDS,

    /**
     * This type can be used for {@code long} properties that store time in milliseconds since Java Epoch.
     * It will be mapped to a time millis QD field.
     */
    TIME_MILLIS,

    /**
     * This type can be used for {@code int} properties that store number of days since Java Epoch.
     */
    DATE,

    /**
     * This type can be used for all primitive properties and reference types using {@link ClassValueMapping} annotaiton.
     * It will be mapped to an integer QD field.
     */
    INT,

    /**
     * This type can be used for {@code long} properties.
     * By default primitive longs are mapped to {@link #DECIMAL} - use this type with {@link EventFieldMapping}
     * annotation to map to a long QD field.
     */
    LONG,

    /**
     * This type can be used for all primitive properties.
     * It will be mapped to a decimal QD field.
     */
    DECIMAL,

    /**
     * This type can be used for {@code char} properties.
     * It will be mapped to a char QD field.
     */
    CHAR,

    /**
     * This type can be used for {@link String} properties.
     * It will be mapped to a short-string QD field that can store up to four ASCII characters.
     */
    SHORT_STRING,

    /**
     * This type can be used for {@link String} properties.
     * It will be mapped to a string QD field.
     */
    STRING,

    /**
     * This type can be used for all reference properties.
     * It will be mapped to a serial object QD field.
     */
    MARSHALLED,
}
