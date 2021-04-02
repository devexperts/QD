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

/**
 * Callback interface that receives notifications about changes in {@link ObservableListModel}.
 * @param <E> the list model element type.
 * @see Change
 */
@FunctionalInterface
public interface ObservableListModelListener<E> {

    /**
     * Represents a notification of the change done to an ObservableListModel.
     *
     * <p><b>Note</b>, that the change object object currently provides only information about the
     * source of the change (what list has changed) and does not actually provide
     * information about what items in the list has changed.
     *
     * <p><b>Warning:</b> Change object can be reused and can become invalid when another change occurs
     * on the list. It is not safe to use this class on a different thread from the listener's thread.
     *
     * @param <E> the list model element type.
     */
    public static class Change<E> {
        private final ObservableListModel<E> source;

        public Change(ObservableListModel<E> source) {
            this.source = source;
        }

        /**
         * Returns the source list model of the change.
         * @return the list model that was changed.
         */
        public ObservableListModel<E> getSource() {
            return source;
        }
    }

    /**
     * Called after a change has been made to an ObservableListModel.
     * It is safe to query the list from inside this method.
     *
     * <p><b>Note</b>, that the change object object currently provides only information about the
     * source of the change (what list has changed) and does not actually provide
     * information about what items in the list has changed.
     *
     * <p><b>Warning:</b> Change object can be reused and can become invalid when another change occurs
     * on the list. It is not safe to use this change object on a different thread from the listener's thread.
     *
     * @param change an object representing the change.
     */
    public void modelChanged(Change<? extends E> change);
}
