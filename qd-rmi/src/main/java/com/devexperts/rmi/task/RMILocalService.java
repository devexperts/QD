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

import com.devexperts.io.MarshallingException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.impl.RMITaskImpl;
import com.devexperts.rmi.security.SecurityController;
import com.dxfeed.promise.Promise;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Provides the asynchronous execution of some operations on the {@link RMIServer}
 * in the local JVM with the appropriate local context.
 * A main extension point in {@code RMILocalService} is {@link #invoke(RMITask) invoke}
 * method that must be overridden in concrete implementations.
 *
 * <p>The local context, that is established for {@link #invoke(RMITask) invoke} method,
 * includes current task in {@link RMITask#current() RMITask.current} method and security context in
 * {@link SecurityController#getSubject() SecurityController.getSubject} method.
 *
 * @param <T> type of the service's methods expected result or a super class of it.
 */
public abstract class RMILocalService<T> extends RMIService<T> {

    private final List<RMIServiceDescriptor> descriptors;


    // ==================== public/protected methods ====================
    /**
     * Creates service with a specified name
     * @param serviceName name of the service
     */
    protected RMILocalService(String serviceName, Map<String, String> properties) {
        super(serviceName);
        descriptors = Collections.singletonList(RMIServiceDescriptor.createDescriptor(
            getOrCreateServiceId(), 0, null, properties));
    }

    @Nonnull
    @Override
    public List<RMIServiceDescriptor> getDescriptors() {
        return descriptors;
    }

    @Override
    public final void openChannel(RMITask<T> task) {
        final RMIChannelSupport<T> channelSupport = channelSupport();
        if (channelSupport == null)
            return; // doest not support channels
        final RMITaskImpl<T> taskImpl = (RMITaskImpl<T>) task; // works only for RMITaskImpl instances, throw CCE early if not
        doAction(taskImpl, () -> {
            try {
                channelSupport.openChannel(taskImpl);
            } catch (Throwable t) {
                taskImpl.completeExceptionally(t);
                return;
            }
        });
    }

    /**
     * Method that is used to submit a task for execution to this service.
     * @param task the {@link RMITask}
     */
    @Override
    public final void processTask(RMITask<T> task) {
        final RMITaskImpl<T> taskImpl = (RMITaskImpl<T>) task; // works only for RMITaskImpl instances, throw CCE early if not
        doAction(taskImpl, () -> {
            try {
                T result = invoke(taskImpl);
                boolean suspended = taskImpl.getExecutionTask().getState().isSuspended();
                if (suspended && !isDefaultValue(result, taskImpl.getOperation().getResultMarshaller().getClasses(null)[0])) {
                    RuntimeException e =
                        new IllegalStateException("Method that called suspend on this task returns non default value");
                    taskImpl.completeExceptionally(RMIExceptionType.INVALID_SUSPEND_STATE, e);
                    return;
                }
                if (result instanceof Promise) {
                    // this can actually happen only when T is Object, so we don't mind casting T to Promise<T>
                    taskImpl.completePromise((Promise<T>) result);
                    return;
                }
                if (!suspended && !taskImpl.isCompleted())
                    taskImpl.complete(result);  // not suspended or failed yet
            } catch (Throwable t) {
                taskImpl.completeExceptionally(t);
            }
        });
    }

    private void doAction(RMITaskImpl<T> taskImpl, Runnable action) {
        RMITaskImpl<?> prevTask = RMITask.THREAD_TASK.get();
        RMITask.THREAD_TASK.set(taskImpl);
        try {
            Object subject = taskImpl.getSubject().getObject();
            SecurityController securityController = taskImpl.getSecurityController();
            securityController.doAs(subject, action);
        } catch (SecurityException e) {
            taskImpl.completeExceptionally(RMIExceptionType.SECURITY_VIOLATION, e);
        } catch (MarshallingException e) {
            taskImpl.completeExceptionally(RMIExceptionType.SUBJECT_UNMARSHALLING_ERROR, e);
        } finally {
            RMITask.THREAD_TASK.set(prevTask);
        }
    }

    /**
     * Method that provides local implementation of the service. The result of this method becomes
     * the result of the task, unless that task is {@link RMITask#suspend(RMITaskCancelListener) suspended}.
     *
     * <p>The local context, that is established for {@code invoke} method,
     * includes current task in {@link RMITask#current() RMITask.current} method and security context in
     * {@link SecurityController#getSubject() SecurityController.getSubject} method.
     *
     * @param task the {@link RMITask}
     * @throws Throwable if something goes wrong, it will be caught abd turned into
     *              {@link RMIExceptionType#APPLICATION_ERROR APPLICATION_ERROR} of this task.
     */
    public abstract T invoke(RMITask<T> task) throws Throwable;

    // override this method to support channels
    protected RMIChannelSupport<T> channelSupport() {
        return null;
    }

    // ------------------- static helper stuff ----------------------------

    private static final Set<Class<?>> PRIMITIVE_NUMBER_TYPES = new HashSet<>(Arrays.<Class<?>>asList(
        byte.class, short.class, int.class, long.class, float.class, double.class));

    private static boolean isDefaultValue(Object result, Class<?> resultClass) {
        if (result == null)
            return true;
        if (!resultClass.isPrimitive())
            return false; // fast path for non-primitive results
        if (PRIMITIVE_NUMBER_TYPES.contains(resultClass))
            return result instanceof Number && ((Number) result).doubleValue() == 0.0;
        if (resultClass == boolean.class)
            return result instanceof Boolean && result.equals(false);
        if (resultClass == char.class)
            return result instanceof Character && result.equals('\0');
        return false; // should not happen, just in case of some mismatch
    }
}
