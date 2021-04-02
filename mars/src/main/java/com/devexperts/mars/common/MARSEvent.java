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
package com.devexperts.mars.common;

import java.util.Date;

/**
 * The single event in a MARS system.
 */
public class MARSEvent {

    public static final String PARAM_TYPE_VALUE = ".value";
    public static final String PARAM_TYPE_STATUS = ".status";
    public static final String PARAM_TYPE_CATEGORY = ".category";
    public static final String PARAM_TYPE_DESCRIPTION = ".description";

    private final String name;
    private final String value;
    private final long timestamp;

    /**
     * Creates new instance of MARS event with specified parameters.
     */
    public MARSEvent(String name, String value) {
        this(name, value, 0);
    }

    public MARSEvent(String name, String value, long timestamp) {
        this.timestamp = timestamp;
        if (name == null || value == null)
            throw new NullPointerException();
        if (name.isEmpty())
            throw new IllegalArgumentException();
        this.name = name;
        this.value = value;
    }

    /**
     * Returns name of a parameter changed by this event.
     *
     * @return name of a parameter changed by this event
     */
    public String getName() {
        return name;
    }

    /**
     * Returns new value for a parameter changed by this event.
     *
     * @return new value for a parameter changed by this event
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns name of the node changed by this event (node name does not include parameter type).
     *
     * @return name of the node changed by this event
     */
    public String getNodeName() {
        if (isValueEvent() || isStatusEvent() || isCategoryEvent() || isDescriptionEvent()) {
            return name.substring(0, name.lastIndexOf('.'));
        }

        return name;
    }

    public boolean isDescriptionEvent() {
        return name.endsWith(PARAM_TYPE_DESCRIPTION);
    }

    public boolean isCategoryEvent() {
        return name.endsWith(PARAM_TYPE_CATEGORY);
    }

    public boolean isValueEvent() {
        return name.endsWith(PARAM_TYPE_VALUE);
    }

    public boolean isStatusEvent() {
        return name.endsWith(PARAM_TYPE_STATUS);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String toString() {
        return timestamp == 0 ? name + "=" + value : name + "=" + timestamp + "|" + value;
    }

    public static String toString(MARSEvent event) {
        if (event == null)
            return "MARSEvent{null}";
        return "MARSEvent{name=" + event.name + ", value=" + event.value + ", timestamp=" + event.timestamp + " -> " + new Date(event.timestamp) + "}";
    }
}
