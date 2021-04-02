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
package com.devexperts.rmi.task;

/**
 *  States of {@link RMITask}.
 */
public enum RMITaskState {

    /**
     * The task was created and is active.
     */
    ACTIVE(false),

    /**
     * The task execution was suspended.
     */
    SUSPENDED(false),

    /**
     * The task execution successfully finished and result is available.
     */
    SUCCEEDED(true),

    /**
     * The task was canceled, but is waiting for confirmation.
     */
    CANCELLING(false),

    /**
     * The task execution failed because of some error or canceled.
     */
    FAILED(true);

    /**
     * Returns {@code true} if this state corresponds is {@link #isCompleted() completed} or {@link #CANCELLING cancelling}.
     * @return {@code true} if this state corresponds is {@link #isCompleted() completed} or {@link #CANCELLING cancelling}.
     */
    public boolean isCompletedOrCancelling() {
        return completed || this == CANCELLING;
    }

    /**
     * Returns {@code true} if this state corresponds to completed {@link RMITask}.
     * In this case this state is final and can not be changed anymore.
     * <br>There are only two final states of the task: {@link #SUCCEEDED}
     * and {@link #FAILED}.
     * @return <tt>true</tt> if this state corresponds to completed task.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns {@code true} when this state needs cancel confirmation, that for {@link #CANCELLING} state.
     * @return {@code true} when this state needs cancel confirmation, that for {@link #CANCELLING} state.
     */
    public boolean needsConfirmation() {
        return this == CANCELLING;
    }

    private final boolean completed;

    RMITaskState(boolean completed) {
        this.completed = completed;
    }
}
