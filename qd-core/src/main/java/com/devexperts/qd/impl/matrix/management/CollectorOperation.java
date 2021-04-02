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
package com.devexperts.qd.impl.matrix.management;

/**
 * Type of collector operation for lock counters.
 * This class is implementation-dependent and is subject to change in any future version.
 */
public enum CollectorOperation {
    PROCESS_DATA("procData"),
    RETRIEVE_DATA("retData"),
    EXAMINE_DATA("examData"),
    MIN_TIME("minTime"),
    MAX_TIME("maxTime"),
    COUNT_DATA("cntData"),
    REMOVE_DATA("removeData"),
    CREATE_AGENT("newAgent"),
    CLOSE_AGENT("clsAgent"),
    CONFIG_AGENT("cfgAgent"),
    ADD_SUBSCRIPTION("addSub"),
    REMOVE_SUBSCRIPTION("remSub"),
    SET_SUBSCRIPTION("setSub"),
    RETRIEVE_SUBSCRIPTION("retSub"),
    INIT_DISTRIBUTOR("initDist"),
    CLOSE_DISTRIBUTOR("clsDist");

    private final String string;

    CollectorOperation(String string) {
        this.string = string;
    }

    public String toString() {
        return string;
    }
}
