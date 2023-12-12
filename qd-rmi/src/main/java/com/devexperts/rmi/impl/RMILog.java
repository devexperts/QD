/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
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
import com.devexperts.rmi.task.BalanceResult;
import com.dxfeed.promise.Promise;

import javax.annotation.Nonnull;

class RMILog {
    private static final Logging log = Logging.getLogging(RMILog.class);

    private RMILog() {} // utility class, do not create

    static void logFailedTask(RMIExceptionType type, String info, RMIConnection connection,
        long requestId, long channelId, RMIRequestType requestType)
    {
        log.error(type.getMessage() + ". Details: " + info + ", " +
            composeExecutionTaskString(connection, requestId, channelId, "reqType=" + requestType.toString()));
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

    static void logBalancingCompletion(@Nonnull Object details, @Nonnull Promise<BalanceResult> balancePromise) {
        if (RMIEndpointImpl.RMI_TRACE_LOG)
            log.trace("Balance result for request '" + details + " is " +
                (balancePromise.hasResult() ? balancePromise.getResult() : balancePromise.getException()));

        if (!balancePromise.isCancelled() && balancePromise.hasException())
            RMILog.log.error("RMI load balancing for '" + details + "' resulted in an exception",
                balancePromise.getException());

        if (balancePromise.hasResult() && balancePromise.getResult().isReject())
            RMILog.log.warn("RMI load balancing rejected the request '" + details + "' with reason '" +
                balancePromise.getResult().getRejectReason() + "'");
    }
}
