/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector.codec.shaper;

import java.io.IOException;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.*;

public class ShapedConnectionFactory extends CodecConnectionFactory {
    ShapedConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate);
    }

    double throughput = Double.POSITIVE_INFINITY; // decimal kilobytes per second (or bytes per millisecond)

    public double getThroughput() {
        return throughput;
    }

    @Configurable
    public void setThroughput(double throughput) {
        if (throughput <= 0)
            throw new IllegalArgumentException("positive value expected");
        this.throughput = throughput;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        return new ShapedConnection(getDelegate(), this, transportConnection);
    }

    public String toString() {
        return "shaped+" + getDelegate().toString();
    }
}
