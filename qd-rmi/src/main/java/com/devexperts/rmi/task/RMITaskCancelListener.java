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
 * The listener for notifications from {@link RMIService} implementation
 * that {@link RMITask} had completed for any reason or is requested to be canceled with confirmation.
 */
public interface RMITaskCancelListener {
    /**
     * This method provides notification from {@link RMIService} implementation
     * that {@link RMITask} had completed for any reason or is requested to be canceled with confirmation.
     * @param task that has been completed for any reason.
     */
    public void taskCompletedOrCancelling(RMITask<?> task);
}
