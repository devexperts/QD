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

import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;

/**
 * States of {@link RMIExecutionTask}.
 */
public enum RMIExecutionTaskState {

    /**
     * The task was created but not submitted for execution yet.
     */
    NEW(false),

    /**
     * The task was submitted for execution first time.
     */
    SUBMITTED(false),

    /**
     * The task was submitted for execution on resume.
     */
    SUBMITTED_ON_RESUME(false),

    /**
     * The task actual execution has started.
     */
    RUNNING(false),

    /**
     * The task was suspended and resumed while still running.
     */
    RESUMED_WHILE_RUNNING(false),

    /**
     * The task execution was suspended explicitly or implicitly
     * by returning from {@link RMIService#processTask(RMITask)} method.
     */
    SUSPENDED(false),

    /**
     * The task execution successfully finished and result is available.
     */
    SUCCEEDED(true),

    /**
     * The task execution failed by some error.
     */
    FAILED(true),

    ; // ====================

    private final boolean completed;

    RMIExecutionTaskState(boolean completed) {
        this.completed = completed;
    }

    /**
     * Returns {@code true} if this state corresponds to completed task.
     * In this case this state is final and can not be changed anymore.
     * <br>There are only two final states of the task: {@link #SUCCEEDED}
     * and {@link #FAILED}.
     * @return {@code true} if this state corresponds to completed task.
     */
    public boolean isCompleted() {
        return completed;
    }



    /**
     * Returns {@code true} is this state corresponds to suspended task.
     * @return {@code true} is this state corresponds to suspended task.
     */
    public boolean isSuspended() {
        return this == SUSPENDED || this == RESUMED_WHILE_RUNNING;
    }
}
