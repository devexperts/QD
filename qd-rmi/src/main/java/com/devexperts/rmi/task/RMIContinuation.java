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

import java.rmi.RMISecurityManager;
import java.util.concurrent.Callable;

/**
 * Provides ability to {@link #resume resume} {@link RMITask} from {@link RMITaskState#SUSPENDED SUSPENDED} state.
 */
public interface RMIContinuation<T> {

    /**
     * This continuation just returns {@code null} on {@link #resume(Callable) resume}.
     */
    @SuppressWarnings("rawtypes")
    public static RMIContinuation EMPTY = new RMIContinuation<Object>() {
        @Override
        public void resume(Callable<Object> callable) {
            return;
        }
    };

    /**
     * Resumes {@link RMITask} from {@link RMITaskState#SUSPENDED SUSPENDED} state.
     * Execution thread is allocated. Inside execution thread the {@link RMITask#current() RMITask.current} is
     * configured in and the request subject is installed via {@link RMISecurityManager}, then
     * {@code callable.call()} is invoked and the result of invocation becomes the result of the task.
     *
     * @param callable {@link Callable#call() call} method is invoked
     *                  when a task starts to work again in the server-assigned execution thread.
     * @throws NullPointerException if {@code callable} is {@code null}.
     * @throws IllegalStateException if this continuation is the result {@link RMITask#suspend(RMITaskCancelListener)}
     * and this method has been invoked more than once
     */
    public void resume(Callable<T> callable);
}
