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
import com.devexperts.rmi.task.RMIServiceImplementation;

import java.util.concurrent.Executor;

/**
 * The server side {@link RMIEndpoint endpoint}.
 * <p/>
 * A server side endpoint can export some services for execution.
 *
 * @see RMIService
 */
public abstract class RMIServer {

    /**
     * Exports a special {@link RMIServiceImplementation} with default name.
     * When {@code serviceInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the exported service.
     * By default, it is equal to the full name of a {@code serviceInterface}
     * (see {@link Class#getName() serviceInterface.getName()}).
     * This service will be executed {@link #getDefaultExecutor()}.
     * To set a custom executor or classloader, create an instance of
     * {@link RMIServiceImplementation RMIServiceImplementation} and
     * use {@link #export(RMIService) export(service)} method.
     *
     * <p>This method is a shortcut for
     * <code>{@link #export(RMIService) export}(new {@link RMIServiceImplementation RMIServiceImplementation}(implementation, serviceInterface))</code>.
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @see #export(RMIService)
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface);

    /**
     * Exports a {@link RMIServiceImplementation} with specified name.
     * This service will be executed {@link #getDefaultExecutor()}
     * To set a custom executor or classloader, create an instance of
     * {@link RMIServiceImplementation RMIServiceImplementation} and
     * use {@link #export(RMIService) export(service)} method.
     *
     * <p>This method is a shortcut for
     * <code>{@link #export(RMIService) export}(new {@link RMIServiceImplementation RMIServiceImplementation}(implementation, serviceInterface, serviceName))</code>.
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @param serviceName      a public name of this service.
     * @see #export(Object, Class)
     * @throws IllegalArgumentException if serviceName contains '#' character.
     * @deprecated Use {@link #export(RMIService)} and
     * {@link RMIServiceImplementation#RMIServiceImplementation(Object, Class, String)
     * RMIServiceImplementation(Object, Class, String)} or use {@link RMIServiceInterface} annotation.
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface, String serviceName);

    /**
     * Exports {@link RMIService} specified by the user.
     *
     * @param service the RMIService
     * @see #export(Object, Class)
     * @throws IllegalArgumentException if {@link RMIService#getServiceName() serviceName} contains '#' character.
     */
    public abstract void export(RMIService<?> service);

    /**
     * Unexports {@link RMIService} specified by the user.
     *
     * @param service the RMIService
     * @throws IllegalArgumentException if {@link RMIService#getServiceName() serviceName} contains '#' character.
     */
    public abstract void unexport(RMIService<?> service);

    /**
     * Returns the default {@link Executor} that is used
     * by this endpoint to execute the requests.
     *
     * @return the default {@link Executor} that is used
     * by this endpoint to execute the requests.
     * @see #setDefaultExecutor(Executor)
     */
    public abstract Executor getDefaultExecutor();

    /**
     * Sets default {@link Executor} that will be commonly used
     * by this endpoint to execute the requests.
     * <p/>
     * In order to use special executors (different from the default one)
     * for some services operations one should set executor for these services using
     * protected field: {@link RMIService#executor} before {@link #export(RMIService)}.
     *
     * @param executor default {@link Executor} that will be commonly used by this endpoint to execute the requests.
     * @see #getDefaultExecutor()
     */
    public abstract void setDefaultExecutor(Executor executor);
}
