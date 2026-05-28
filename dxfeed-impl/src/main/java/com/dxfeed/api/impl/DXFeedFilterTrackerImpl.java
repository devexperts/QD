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
package com.dxfeed.api.impl;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFilter;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterListener;
import com.dxfeed.api.DXFeedFilterTracker;

import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.concurrent.GuardedBy;

/**
 * {@link DXFeedFilterTracker} implementation based on {@link QDFilter.Updated}.
 * Listener registration is lazy: subscribes on first listener add, unsubscribes when last is removed.
 */
class DXFeedFilterTrackerImpl implements DXFeedFilterTracker, QDFilter.UpdateListener {

    private static final Logging log = Logging.getLogging(DXFeedFilterTrackerImpl.class);

    private final QDFilter.Updated[] qdUpdated;
    private final boolean hasDynamic;

    private volatile DXFeedFilterImpl currentFilter;

    private final CopyOnWriteArrayList<DXFeedFilterListener> listeners = new CopyOnWriteArrayList<>();

    DXFeedFilterTrackerImpl(DXFeedFilterImpl initial, QDFilter.Updated[] qdUpdated) {
        this.currentFilter = initial;
        this.qdUpdated = qdUpdated;
        boolean dynamic = false;
        for (QDFilter.Updated updated : qdUpdated) {
            if (updated.getFilter().isDynamic()) {
                dynamic = true;
                break;
            }
        }
        this.hasDynamic = dynamic;
    }

    boolean isDynamic() {
        return hasDynamic;
    }

    @Override
    public DXFeedFilter getCurrentFilter() {
        return currentFilter;
    }

    @Override
    public void addListener(DXFeedFilterListener listener) {
        if (!hasDynamic)
            return;
        synchronized (this) {
            boolean wasEmpty = listeners.isEmpty();
            listeners.add(listener);
            if (wasEmpty) {
                for (QDFilter.Updated updated : qdUpdated) {
                    updated.addUpdateListener(this);
                }
                // we could miss filter updates before the first listener was added
                update();
            }
        }
    }

    @Override
    public void removeListener(DXFeedFilterListener listener) {
        if (!hasDynamic)
            return;
        synchronized (this) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                for (QDFilter.Updated updated : qdUpdated) {
                    updated.removeUpdateListener(this);
                }
            }
        }
    }

    @Override
    public void filterUpdated(QDFilter filter) {
        boolean updated;
        synchronized (this) {
            updated = update();
        }
        if (updated) {
            notifyListeners();
        }
    }

    /**
     * Retrieves the latest QDFilter for each category and rebuilds the current filter.
     */
    @GuardedBy("this")
    private boolean update() {
        DXFeedFilterImpl current = currentFilter;
        QDFilter[] oldFilters = current.getCategoryFilters();
        QDFilter[] newFilters = new QDFilter[qdUpdated.length];
        boolean updated = false;
        for (int i = 0; i < qdUpdated.length; i++) {
            newFilters[i] = qdUpdated[i].getFilter();
            if (newFilters[i] != oldFilters[i]) {
                updated = true;
            }
        }
        if (updated) {
            currentFilter = new DXFeedFilterImpl(current, newFilters);
        }
        return updated;
    }

    private void notifyListeners() {
        for (DXFeedFilterListener listener : listeners) {
            try {
                listener.filterUpdated(this);
            } catch (Throwable t) {
                log.error("Unexpected error in DXFeedFilter update listener", t);
            }
        }
    }
}
