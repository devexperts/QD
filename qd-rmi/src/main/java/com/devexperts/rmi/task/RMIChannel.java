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

import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIServiceInterface;

/**
 * {@link RMIChannel} is a channel that is open between client and server in both direction on each request.
 *
 * Channel provides ability to send multiple additional messages from server to client and from client to server
 * while the request is in processes. The channel is open when the server receives a request from the client
 * and is closed when the request completes either normally or exceptionally
 * (including case when client cancels a request or the request is cancelled because of the loss of network connection).
 * All requests open a channel implicitly and there's no need to do anything special to open one.
 *
 * <p>
 * All nested requests are executed sequentially in a single thread in a special channel handler.
 */
public interface RMIChannel extends RMIClientPort {
    /**
     * Added a special {@link RMIServiceImplementation channel handler} with default name
     * for processing requests within the channel.
     * When {@code handlerInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the added handler.
     * By default, it is equal to the full name of a {@code handlerInterface}
     * (see {@link Class#getName() handlerInterface.getName()}).
     * The only possible wildcard is a symbol "*".
     *
     * <p>This method is a shortcut for
     * <code>{@link #addChannelHandler(RMIService) addChannelHandler}(new {@link RMIServiceImplementation
     * RMIServiceImplementation}(implementation, handlerInterface))</code>.
     *
     * @param implementation   implementation of the exporting service.
     * @param handlerInterface interface of the exporting service.
     * @see #addChannelHandler(RMIService)
     * @throws IllegalArgumentException if the handler with the same {@link RMIService#getServiceName() handlerName}
     * has already been added
     * @throws IllegalStateException if the channel has already been opened.
     */
    public <T> void addChannelHandler(T implementation, Class<T> handlerInterface);

    /**
     * Added {@link RMIService channel handler} specified by the user.
     *<p> The only possible wildcard in service name in handler is a symbol "*".
     *
     * @param handler the RMIService
     * @see #addChannelHandler(Object, Class)
     * @throws IllegalArgumentException if the handler with the same {@link RMIService#getServiceName() handlerName}
     * has already been added
     * @throws IllegalStateException if the channel has already been opened.
     */
    public void addChannelHandler(RMIService<?> handler);

    /**
     * Returns the current state of the channel.
     * @return the current state of the channel.
     * @see RMIChannelState
     */
    public RMIChannelState getState();

    /**
     * Returns {@link RMIChannelType channel type.}
     * @return channel type.
     */
    public RMIChannelType getType();

    /**
     * Returns either {@link RMITask} or {@link com.devexperts.rmi.RMIRequest} that had created this channel.
     */
    public Object getOwner();

    /**
     * Removed {@link RMIService channel handler} specified by the user.
     *
     * @param handler the RMIService.
     */
    public void removeChannelHandler(RMIService<?> handler);
}
