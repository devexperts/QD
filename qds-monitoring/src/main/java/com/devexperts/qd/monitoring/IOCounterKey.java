/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import com.devexperts.qd.qtp.MessageConnector;

import java.util.Objects;

class IOCounterKey {
    public final MessageConnector connector;
    public final String stripe;
    public final String name;

    public IOCounterKey(MessageConnector connector, String stripe) {
        this.connector = connector;
        this.stripe = stripe;
        this.name = connector.getName() + (stripe != null ? "." + stripe : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IOCounterKey) {
            IOCounterKey that = (IOCounterKey) o;
            return this.connector == that.connector && Objects.equals(this.stripe, that.stripe);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connector, stripe);
    }
}
