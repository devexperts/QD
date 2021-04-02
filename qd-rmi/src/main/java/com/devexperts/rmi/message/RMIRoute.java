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
package com.devexperts.rmi.message;

import com.devexperts.connector.proto.EndpointId;

import java.util.AbstractList;
import java.util.Arrays;


/**
 * {@link RMIRoute} this is container for passed ways {@link RMIMessage} in the network. {@link RMIRoute} are constant;
 * their values cannot be changed after they are created.
 */
public class RMIRoute extends AbstractList<EndpointId> {

    /**
     * Empty route.
     */
    public static final RMIRoute EMPTY = new RMIRoute(new EndpointId[0]);

    private EndpointId[] ids;

    /**
     * Creates a route along traversed nodes.
     * @param ids traversed nodes, can be null (ignored)
     */
    public static RMIRoute createRMIRoute(EndpointId... ids) {
        int n = 0;
        for (EndpointId id : ids) {
            if (id != null)
                n++;
        }
        if (n == 0)
            return EMPTY;
        EndpointId[] filteredIds = new EndpointId[n];
        int i = 0;
        for (EndpointId id : ids) {
            if (id != null)
                filteredIds[i++] = id;
        }
        return new RMIRoute(filteredIds);
    }

    private RMIRoute(EndpointId[] ids) {
        this.ids = ids;
    }

    /**
     * Creates new {@link RMIRoute} by adding a new node to itself.
     * @param id new nodes
     * @return new {@link RMIRoute} by adding a new node to itself
     */
    public RMIRoute append(EndpointId id) {
        if (id == null)
            throw new NullPointerException();
        int n = this.ids.length;
        if (n > 0 && this.ids[n - 1].equals(id))
            return this;
        EndpointId[] ids = Arrays.copyOf(this.ids, n + 1);
        ids[n] = id;
        return createRMIRoute(ids);
    }

    /**
     * Returns first node in route.
     * @return first node in route
     */
    public EndpointId getFirst() {
        return size() == 0 ? null : ids[0];
    }

    /**
     * Returns last node in route.
     * @return last node in route
     */
    public EndpointId getLast() {
        return size() == 0 ? null : ids[size() - 1];
    }

    /**
     * Returns nodes under the corresponding number in route. Throw {@link ArrayIndexOutOfBoundsException} if index is
     * greater than the number of nodes in route or if index less then 0.
     * @param index number node in route
     * @return nodes under the corresponding number in route
     * @throws ArrayIndexOutOfBoundsException if index is greater than the number of nodes in route or
     * if index less then 0
     */
    @Override
    public EndpointId get(int index) {
        return ids[index];
    }


    /**
     * Returns the length of route.
     * @return the length of route
     */
    @Override
    public int size() {
        return ids.length;
    }

    /**
     * Method checks whether a given endpointId in the route.
     * @param endpointId endpointId to check
     * @return true, if endpointId is contained in the route
     */
    public boolean isNotEmptyWithLast(EndpointId endpointId) {
        return size() > 1 && getLast().equals(endpointId);
    }

    @Override
    public String toString() {
        if (size() == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(ids[i]);
            if (i != (size() - 1))
                sb.append("->");
        }
        return sb.toString();
    }
}
