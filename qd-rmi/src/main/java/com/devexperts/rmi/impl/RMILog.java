/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.message.RMIRequestType;

class RMILog {
    private RMILog() {} // utility class, do not create

    static final Logging log = Logging.getLogging("com.devexperts.rmi.RMI"); // for errors

    static void logFailedTask(RMIExceptionType type, String info, RMIConnection connection,
        long requestId, long channelId, RMIRequestType requestType)
    {
        log.error(type.getMessage() + " " + info + ": " + composeExecutionTaskString(connection, requestId, channelId,
            "reqType=" + requestType.toString()));
    }

    static <T> void logExecutionError(RMIExecutionTaskImpl<T> execution, RMIExceptionType type, Throwable e) {
        log.error(type.getMessage() + ": " + execution, e);
    }

    static String composeExecutionTaskString(RMIConnection connection, long requestId, long channelId, String task) {
        return "Task{" +
            "host=" + connection.getRemoteHostAddress() + ", " +
            "reqId=" + requestId + ", " +
            "channelId=" + channelId + ", " +
            task + // print task in-place
            "}";
    }
}
