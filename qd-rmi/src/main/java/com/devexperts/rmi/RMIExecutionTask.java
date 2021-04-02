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
package com.devexperts.rmi;

import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.util.TypedMap;

/**
 * A task that is submitted to the endpoint executor on the server side
 * for every request.
 *
 * <p><b>This class is separate from {@link RMITask} only for backwards-compatibility.</b>
 *
 * @see RMIEndpoint#getDefaultExecutor()
 * @deprecated use {@link RMITask}
 */
public abstract class RMIExecutionTask<T> implements Runnable {
    /**
     * Returns map of per-connection variables for this request. One of the most important connection
     * variables is {@link TransportConnection#REMOTE_HOST_ADDRESS_KEY}. Application can
     * define and store its custom connection variables in this map.
     */
    public abstract TypedMap getConnectionVariables();

    /**
     * Returns time in milliseconds when this task was submitted for execution.
     * @return time in milliseconds when this task was submitted for execution.
     */
    public abstract long getSubmissionTime();

    /**
     * Returns the {@link RMIExecutionTaskState state} of this task.
     * @return the {@link RMIExecutionTaskState state} of this task.
     */
    public abstract RMIExecutionTaskState getState();

    /**
     * Returns <tt>true</true> if this task corresponds to a one-way request.
     * @return <tt>true</true> if this task corresponds to a one-way request.
     */
    public abstract boolean isOneWayRequest();

    /**
     * Returns the task of this execution task
     * @return the task of this execution task
     */
    public abstract RMITask<T> getTask();

    @Override
    public abstract String toString();

}
