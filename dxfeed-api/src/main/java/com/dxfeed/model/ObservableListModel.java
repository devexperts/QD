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
package com.dxfeed.model;

import java.util.List;

/**
 * A list that allows to track its changes via listeners.
 * @param <E> the list model element type
 */
public interface ObservableListModel<E> extends List<E> {

    /**
     * Adds a listener to this observable list model.
     *
     * <p><b>Note</b>, that the listener currently provides only information about the
     * source of the change (what list has changed) and does not actually provide
     * information about what items in the list has changed.
     *
     * @param listener the listener for listening to the list changes.
     */
    public void addListener(ObservableListModelListener<? super E> listener);

    /**
     * Removes a listener from this observable list model.
     * If the listener is not attached to this model, nothing happens.
     * @param listener the listener to remove.
     */
    public void removeListener(ObservableListModelListener<? super E> listener);
}
