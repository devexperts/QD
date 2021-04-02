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
package com.devexperts.qd.qtp.socket;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.InvalidFormatException;

import java.util.Objects;

/**
 * ConnectOrder specifies a strategy of considering specified addresses in {@link ClientSocketConnector} during
 * connect/reconnect.
 */
public class ConnectOrder {

    private static final IndexedSet<String, ConnectOrder> VALUES = IndexedSet.create(ConnectOrder::getName);

    public static final ConnectOrder SHUFFLE = register("shuffle", true, false);
    public static final ConnectOrder RANDOM = register("random", true, true);
    public static final ConnectOrder ORDERED = register("ordered", false, false);
    public static final ConnectOrder PRIORITY = register("priority", false, true);

    private final String name;
    private final boolean randomized;
    private final boolean resetOnConnect;

    private ConnectOrder(String name, boolean randomized, boolean resetOnConnect) {
        this.name = Objects.requireNonNull(name);
        this.randomized = randomized;
        this.resetOnConnect = resetOnConnect;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ConnectOrder that = (ConnectOrder) o;

        return name.equals(that.name);
    }

    private static ConnectOrder register(String name, boolean randomized, boolean resetOnConnect) {
        ConnectOrder order = new ConnectOrder(name, randomized, resetOnConnect);
        if (VALUES.put(order) != null)
            throw new IllegalArgumentException("Duplicate ConnectOrder name '" + name + "'");
        return order;
    }

    public static ConnectOrder valueOf(String name) {
        if (name == null || name.isEmpty())
            return null;
        ConnectOrder connectOrder = VALUES.getByKey(name);
        if (connectOrder == null)
            throw new InvalidFormatException("Unknown ConnectOrder '" + name + "'");
        return connectOrder;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    boolean isRandomized() {
        return randomized;
    }

    boolean isResetOnConnect() {
        return resetOnConnect;
    }
}
