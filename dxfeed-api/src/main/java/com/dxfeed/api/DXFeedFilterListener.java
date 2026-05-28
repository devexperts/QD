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

/**
 * Listener for {@link DXFeedFilter} update tracking.
 *
 * @see <a href="DXFeedFilter.html#dynamicFilterMechanismSection">Dynamic filter mechanism</a>
 */
@Experimental
@FunctionalInterface
public interface DXFeedFilterListener {
    /**
     * Called when the underlying filter has been updated. The latest filter instance
     * is available via {@link DXFeedFilterTracker#getCurrentFilter()}.
     *
     * @param tracker the tracker whose underlying filter was updated
     */
    public void filterUpdated(DXFeedFilterTracker tracker);
}
