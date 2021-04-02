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

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.dxfeed.promise.Promise;

import javax.annotation.Nonnull;

/**
 * <h2>Overview</h2>
 * A strategy that defines how to choose a service implementation for an {@link RMIRequest}. There might be multiple
 * implementations of a service in the network, the task of this balancer is to determine where to route each request.
 * <p>
 * QD RMI endpoints distribute service advertisement. The balancer is notified when a service is discovered, disappears
 * or changes its properties via the {@link #updateServiceDescriptor(RMIServiceDescriptor)} method.
 * <p>
 * <h2>Load Balancing</h2>
 * The decision is made in the {@link #balance(RMIRequestMessage)} method. The balancer must return a promise
 * to define how a request should be routed. See Javadoc for the method for more details.
 * <p>
 * <h2>Lifecycle</h2>
 * The balancer is created by the pluggable {@link RMILoadBalancerFactory} for each of the services when a first
 * request for a service is sent. The balancer is {@link #close() closed and released} when
 * {@link RMIEndpoint the corresponding endpoint} is {@link RMIEndpoint#close() closed}.
 * <p>
 * <h2>Thread Safety</h2>
 * Implementation of this strategy must be thread-safe. The promises returned by {@link #balance(RMIRequestMessage)}
 * can be cancelled externally (when the corresponding request is cancelled i.e. because of timeout or manually).
 * <p>
 * <h2>Configuration Details</h2>
 * Note: for custom load balancing to work on intermediate multiplexors one has to disable service advertisements
 * sent to clients via {@code [advertise=none]} filter on multiplexor agent address. Otherwise default load balancing
 * will kick in on the clients.
 */
public interface RMILoadBalancer extends AutoCloseable {

    /**
     * Returns a decision how to route the request. A balancer can either route the request to some {@link RMIServiceId}
     * (see {@link BalanceResult#route(RMIServiceId)} or reject the request
     * (see {@link BalanceResult#reject(RMIServiceId, String)}.
     * <p>
     * A request might have a {@link RMIRequestMessage#getTarget() tentative target} assigned. If the balancer
     * cannot make any decision where to send the request it must keep the previous target (even if it's {@code null})
     * by returning {@code BalanceResult.route(request.getTarget())}'.
     * <p>
     * If a balancer decides to {@link BalanceResult#reject(RMIServiceId, String) reject} the request, it may
     * indicate the rejected target in the reject result (for example, by copying the target from the request via
     * {@code BalanceResult.reject(request.getTarget, "some details")}.
     * <p>
     * The promise returned from this method can be {@link Promise#cancel() cancelled} by QD RMI if the request
     * being balanced is cancelled while balancing decision is still not made. QD RMI tracks timeouts of all requests
     * and aborts the requests cancelling the corresponding promises so the balancer can omit any promise timeout
     * tracking.
     * <p>
     * This method can be invoked multiple times for the same request (if re-balancing is required). QD RMI
     * guarantees that a previous promise will be completed prior to calling this method again for the same
     * request (either completed successfully or cancelled).
     * <p>
     * This method might be called before any services are discovered in the network
     * and are {@link #updateServiceDescriptor(RMIServiceDescriptor) reported to the balancer}. This happens when
     * a request for a service is sent while service advertisements are not yet received. The balancer may either
     * reject the request, postpone the decision by returning an incomplete promise or make no decision by
     * returning a {@link BalanceResult#route(RMIServiceId) balance result with the 'null' tentative target}.
     * <p>
     * This method should not throw any exceptions. Any exception thrown from this method will lead to rejection
     * of the request being balanced.
     * @param request the request which must be routed
     * @return a decision how to route the request. If the balancer completes the resulting promise with any exception
     * (including {@link Promise#cancel() cancellation}) the request will be rejected.
     * If the balancer returns {@code null} the request is also rejected.
     */
    @Nonnull
    public Promise<BalanceResult> balance(@Nonnull RMIRequestMessage<?> request);

    /**
     * This method is invoked by QD RMI when a service appears, disappears or changes its properties such as
     * {@link RMIServiceDescriptor#getDistance() distance}.
     * Use {@link RMIServiceDescriptor#isAvailable()} to find out if the service is available or not.
     * @param descriptor the service descriptor
     */
    public void updateServiceDescriptor(@Nonnull RMIServiceDescriptor descriptor);

    /**
     * This method is invoked when this balancer is closed and will no longer be used by QD RMI. The balancer is
     * expected to release any resources it might hold. The balancer is free to forget about incomplete
     * balancing promises it produced (results of {@link #balance(RMIRequestMessage)} method), the promises
     * will eventually be cancelled by QD RMI.
     */
    @Override
    public void close();
}
