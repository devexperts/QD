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
package com.devexperts.connector.codec.shaped;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.TransportConnection;

import java.io.IOException;

public class ShapedConnectionFactory extends CodecConnectionFactory {

    private double outLimit = Double.POSITIVE_INFINITY; // bytes per second

    ShapedConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate);
    }

    public double getOutLimit() {
        return outLimit;
    }

    @Configurable(description = "output throughput limit (bytes per second)")
    public void setOutLimit(double outLimit) {
        if (outLimit <= 0)
            throw new IllegalArgumentException("positive value expected");
        this.outLimit = outLimit;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        return new ShapedConnection(getDelegate(), this, transportConnection);
    }

    public String toString() {
        return "shaped+" + getDelegate().toString();
    }
}
