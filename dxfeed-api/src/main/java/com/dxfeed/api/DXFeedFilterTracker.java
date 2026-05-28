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
 * A stable reference holder that tracks the most recent version of a {@link DXFeedFilter}.
 * Unlike the filter instance, the tracker reference does not change across filter updates.
 *
 * <p>{@link #getCurrentFilter()} always returns the most recent filter version. Under
 * high-frequency updates, listeners may be notified fewer times than there are underlying
 * changes.
 *
 * @see <a href="DXFeedFilter.html#dynamicFilterMechanismSection">Dynamic filter mechanism</a>
 */
@Experimental
public interface DXFeedFilterTracker {
    /**
     * Returns the most recent version of the filter.
     *
     * <p>NOTE: Tracking of the filter updates is activated lazily by {@link #addListener adding a listener}.
     * If no listeners were added, the original version of the underlying filter may be returned.
     *
     * @return the current filter instance (never null)
     */
    public DXFeedFilter getCurrentFilter();

    /**
     * Adds a listener for {@link DXFeedFilter#isDynamic() dynamic} filter. When this filter
     * is not dynamic this method does nothing. The listener's
     * {@link DXFeedFilterListener#filterUpdated filterUpdated} method is invoked
     * on every update of a filter that happens <b>afterwards</b>.
     * Listeners must be {@link #removeListener removed} when they are no longer needed.
     *
     * @param listener the listener to add
     */
    public void addListener(DXFeedFilterListener listener);

    /**
     * Removes a previously added listener.
     *
     * <p>A listener may receive one final invocation after this method returns, because
     * notification iteration uses a snapshot of the listener list. Callers must tolerate
     * this trailing call.
     *
     * @param listener the listener to remove
     */
    public void removeListener(DXFeedFilterListener listener);
}
