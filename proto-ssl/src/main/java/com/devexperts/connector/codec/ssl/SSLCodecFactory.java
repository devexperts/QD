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
package com.devexperts.connector.codec.ssl;

import com.devexperts.connector.codec.CodecFactory;
import com.devexperts.connector.proto.ApplicationConnectionFactory;

public class SSLCodecFactory implements CodecFactory {
    @Override
    public ApplicationConnectionFactory createCodec(String name, ApplicationConnectionFactory delegate) {
        if (name.equalsIgnoreCase("ssl") || name.equalsIgnoreCase("tls"))
            return new SSLConnectionFactory(delegate);
        return delegate;
    }
}
