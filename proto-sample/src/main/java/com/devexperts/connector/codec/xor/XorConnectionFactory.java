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
package com.devexperts.connector.codec.xor;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.TransportConnection;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class XorConnectionFactory extends CodecConnectionFactory {
    MessageDigest algorithm;
    String secret = "secret";

    XorConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate);
        try {
            algorithm = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public MessageDigest getAlgorithm() {
        return algorithm;
    }

    @Configurable
    public void setAlgorithm(MessageDigest algorithm) {
        this.algorithm = algorithm;
    }

    public String getSecret() {
        return secret;
    }

    @Configurable
    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        return new XorConnection(getDelegate(), this, transportConnection);
    }

    public String toString() {
        return "xor+" + getDelegate().toString();
    }
}
