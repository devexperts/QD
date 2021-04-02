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

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A decision made by a {@link RMILoadBalancer} - we either route the request somewhere or reject the request.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class BalanceResult {

    private final RMIServiceId tentativeTarget;
    private final Type type;
    private final String rejectReason;

    private BalanceResult(RMIServiceId tentativeTarget, @Nonnull Type type, String rejectReason) {
        this.tentativeTarget = tentativeTarget;
        this.type = Objects.requireNonNull(type, "type");
        this.rejectReason = rejectReason;
    }

    /**
     * Returns a decision to route the request to some target. The target is 'tentative' in the sense that
     * it can be overridden later. The target can be {@code null} - this indicates that we could not determine
     * a decision but the request should not be rejected.
     * @param tentativeTarget target to route the request to
     * @return a decision to route the request to some target
     * @see #getTarget()
     */
    @Nonnull
    public static BalanceResult route(@Nullable RMIServiceId tentativeTarget) {
        return new BalanceResult(tentativeTarget, Type.ROUTE, null);
    }

    /**
     * Returns a decision to reject the request.
     * @param tentativeTarget the target that was rejected
     * @param rejectReason human-readable details describing the reason for rejection.
     * @return a decision to reject the request
     * @see #isReject()
     * @see #getRejectReason()
     */
    @Nonnull
    public static BalanceResult reject(@Nullable RMIServiceId tentativeTarget, @Nullable String rejectReason) {
        return new BalanceResult(tentativeTarget, Type.REJECT,
            rejectReason == null ? "Request rejected by load balancer" : rejectReason);
    }

    /**
     * @return id of the service the request should be routed to. If the decision is to
     * {@link #reject(RMIServiceId, String) reject} the request, contains the target that is being rejected.
     * Usually this target is copied from the request.
     */
    @Nullable
    public RMIServiceId getTarget() {
        return tentativeTarget;
    }

    /**
     * @return true if this decision is to reject the request, false otherwise.
     */
    public boolean isReject() {
        return type == Type.REJECT;
    }

    /**
     * @return human-readable details about a {@link #reject(RMIServiceId, String) reject} decision.
     * Reject reason is never {@code null} for the rejected result - default reason is returned if not specified in
     * {@link #reject(RMIServiceId, String)}.
     * If decision is to route ({@link #isReject()} returns false), this method returns {@code null}.
     */
    @Nullable
    public String getRejectReason() {
        return rejectReason;
    }

    /**
     * Type of the result
     */
    private enum Type {
        REJECT,
        ROUTE
    }

    @Override
    public String toString() {
        return "BalanceResult{" +
            (isReject() ? "REJECT routing to " + tentativeTarget + " due to '" + rejectReason + "'" :
                "ROUTE to " + tentativeTarget) +  '}';
    }
}
