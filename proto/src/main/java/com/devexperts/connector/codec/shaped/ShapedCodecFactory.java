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

import com.devexperts.connector.codec.CodecFactory;
import com.devexperts.connector.proto.ApplicationConnectionFactory;

/**
 * This codec does not change underlying data but artificially constrains
 * connection outbound throughput by specified value.
 *
 * It can be used for testing purposes.
 */
public class ShapedCodecFactory implements CodecFactory {
    public ApplicationConnectionFactory createCodec(String name, ApplicationConnectionFactory delegate) {
        if (name.equalsIgnoreCase("shaped"))
            return new ShapedConnectionFactory(delegate);
        return delegate;
    }
}
