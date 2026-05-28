/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api;

import com.devexperts.annotation.Experimental;

import javax.annotation.Nullable;

/**
 * A factory interface for creating {@link DXFeedFilter} instances using
 * {@link com.devexperts.services.Services} framework.
 *
 * <p>Factory must return {@code null} if it cannot create a filter for the given parameters.
 * Dynamic filters must be updated via internal logic without an explicit call of the factory.
 *
 * <h3>Implementation details</h3>
 * Factories are iterated in {@link DXFeedFilter.Builder#build()} method and the first non-null result wins.
 */
@Experimental
public interface DXFeedFilterFactory {
    /**
     * Returns a {@link DXFeedFilter} for the given parameters, {@code null} otherwise.
     *
     * @param builder the builder holding endpoint and category configuration
     * @return the filter instance, or {@code null} if cannot create filter
     */
    @Nullable
    public DXFeedFilter create(DXFeedFilter.Builder builder);
}
