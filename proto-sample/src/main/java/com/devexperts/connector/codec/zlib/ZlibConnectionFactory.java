/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.connector.codec.zlib;

import java.io.IOException;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.*;
import com.jcraft.jzlib.JZlib;

public class ZlibConnectionFactory extends CodecConnectionFactory {
    int compression = JZlib.Z_DEFAULT_COMPRESSION;
    boolean suppressHeaders = false;

    ZlibConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate);
    }

    public int getCompression() {
        return compression;
    }

    @Configurable
    public void setCompression(int compression) {
        this.compression = compression;
    }

    public boolean isSuppressHeaders() {
        return suppressHeaders;
    }

    @Configurable
    public void setSuppressHeaders(boolean suppressHeaders) {
        this.suppressHeaders = suppressHeaders;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        return new ZlibConnection(getDelegate(), this, transportConnection);
    }

    public String toString() {
        return "zlib+" + getDelegate().toString();
    }
}
