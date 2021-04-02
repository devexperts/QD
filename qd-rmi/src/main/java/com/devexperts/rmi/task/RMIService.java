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

import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.RMIServiceInterface;
import com.devexperts.util.IndexerFunction;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Provides the asynchronous execution of some operations on the {@link RMIServer}.
 * A main extension point in RMIService is {@link #processTask(RMITask)} method that is used to submit an {@link RMITask}
 * for execution to this service and must be overridden in its concrete implementations.
 *
 * <p>To implement services locally, use {@link RMILocalService} class that established an appropriate
 * context for local execution.
 *
 * @param <T> type of the service's methods expected result or a super class of it.
 */
public abstract class RMIService<T> implements RMIObservableServiceDescriptors, RMIChannelSupport<T> {

    /**
     * Returns a service name for a given service interface.
     * When {@code serviceInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the exported service.
     * By default, it is equal to the full name of a {@code serviceInterface}
     *
     * @param serviceInterface the remote service interface.
     * @return a service name for a given service interface.
     */
    public static String getServiceName(Class<?> serviceInterface) {
        RMIServiceInterface annotation = serviceInterface.getAnnotation(RMIServiceInterface.class);
        return annotation != null && !annotation.name().isEmpty() ? annotation.name() :
            serviceInterface.getName();
    }

    public static final IndexerFunction<String, RMIService<?>> RMI_SERVICE_INDEXER =
        (IndexerFunction<String, RMIService<?>>) RMIService::getServiceName;

    /**
     * Distance to unavailable services.
     */
    public static final int UNAVAILABLE_METRIC = Integer.MAX_VALUE;

    private RMIServiceId serviceId;

    /**
     * The name of the service.
     */
    protected final String serviceName;

    /**
     * Executor that will be used by this service to execute the requests. This field can be initialized
     * with the implementation of this class.
     * Note, that for backwards compatibility, if the executor implements {@link ExecutorService}, then it will be use
     * only {@link ExecutorService#submit(Runnable)}
     */
    protected Executor executor;

    /**
     * Creates service with a specified name
     * @param serviceName name of the service
     * @throws IllegalArgumentException if service name contains '#' character.
     */
    protected RMIService(String serviceName) {
        if (serviceName.indexOf('#') >= 0)
            throw new IllegalArgumentException("Invalid service name: " + serviceName);
        this.serviceName = serviceName;
    }

    /**
     * Returns the service name.
     * @return the service name
     */
    public final String getServiceName() {
        return serviceName;
    }

    /**
     * Returns executor for this service. A default implementation that returns null.
     * Note, that for backwards compatibility, if the executor implements {@link ExecutorService}, then it will be use
     * only {@link ExecutorService#submit(Runnable)}
     * @return executor for these service.
     */
    public final Executor getExecutor() {
        return executor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openChannel(RMITask<T> task) {}

    /**
     * Method that is used to submit a task for execution to this service.
     *
     * <p> If {@code processTask} method completes exceptionally, then the {@link RMITask task} is automatically
     * tagged as {@link RMITaskState#FAILED}.
     *
     * <p> This method is executed in an RMI execution thread pool or in RMIService's own executor
     * (if its {@link #getExecutor() getExecutor} method returns non-null).
     *
     * <p> Any exceptions throws by this method are turned into {@link RMIExceptionType#EXECUTION_ERROR EXECUTION_ERROR}.
     *
     * @param task the {@link RMITask}
     */
    public abstract void processTask(RMITask<T> task);

    /**
     * Returns true, if implementation of this service is available from current endpoint.
     * @return true, if implementation of this service is available from current endpoint
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Returns unique serviceId for this RMIService.
     * @return unique serviceId for this RMIService.
     */
    protected final synchronized RMIServiceId getOrCreateServiceId() {
        if (serviceId == null)
            serviceId = RMIServiceId.newServiceId(serviceName);
        return serviceId;
    }

    /**
     * Returns unmodifiable list of descriptors of all the implementations of this service in the network.
     * Order of descriptors is not specified.
     * <b>This method must be lock-free.</b>
     * @return descriptors of all the implementations of this service in the network. The list is unmodifiable.
     */
    @Nonnull
    public List<RMIServiceDescriptor> getDescriptors() {
        return Collections.singletonList(RMIServiceDescriptor.createDescriptor(getOrCreateServiceId(), 0, null, null));
    }

    /**
     * Adds a {@link RMIServiceDescriptorsListener service descriptor listener} for this service.
     * When a listener is added it is immediately notified with all the descriptors that are known currently.
     *
     * The listener is notified whenever a descriptor of a service implementation appears or disappears in the network.
     * For server-side services this obviously never happens.
     *
     * <b>Impl. note: notification happens while holding monitor on this {@code RMIService} object.</b>
     * @param listener listener to add
     * @see #removeServiceDescriptorsListener(RMIServiceDescriptorsListener)
     */
    @Override
    public synchronized void addServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        List<RMIServiceDescriptor> descriptors = getDescriptors();
        if (!descriptors.isEmpty())
            listener.descriptorsUpdated(descriptors);
    }

    /**
     * Removes the specified {@link RMIServiceDescriptorsListener listener} from this service.
     * When the listener is removed, it receives notification of the update distance to all
     * previously reported services to {@link #UNAVAILABLE_METRIC} distance.
     * <b>Note: notification happens while holding monitor on this {@code RMIService} object.</b>
     *
     * @param listener the listener to be removed
     */
    @Override
    public synchronized void removeServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        List<RMIServiceDescriptor> descriptors = getDescriptors().stream()
            .map(descriptor ->
                RMIServiceDescriptor.createUnavailableDescriptor(descriptor.getServiceId(), descriptor.getProperties()))
            .collect(Collectors.toList());
        if (!descriptors.isEmpty())
            listener.descriptorsUpdated(descriptors);
    }

    @Override
    public String toString() {
        return getOrCreateServiceId().toString();
    }
}
