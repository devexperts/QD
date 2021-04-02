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

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * {@link SSLEngine} representative for {@link SSLConnection}.
 */
class SSLEngineAdapter {

    private final SSLEngine engine;

    /**
     * Create adapter delegating functions to a specified SSL engine.
     * @param engine - SSL engine to be used as delegate.
     */
    SSLEngineAdapter(SSLEngine engine) {
        this.engine = engine;
    }

    /**
     * @see SSLEngine#getSession
     */
    SSLSession getSession() {
        return engine.getSession();
    }

    /**
     * @see SSLEngine#getDelegatedTask()
     */
    Runnable getDelegatedTask() {
        return engine.getDelegatedTask();
    }

    /**
     * @see SSLEngine#getHandshakeStatus()
     */
    SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return engine.getHandshakeStatus();
    }

    /**
     * @see SSLEngine#wrap(ByteBuffer, ByteBuffer)
     */
    SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return engine.wrap(src, dst);
    }

    /**
     * @see SSLEngine#unwrap(ByteBuffer, ByteBuffer)
     */
    SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return engine.unwrap(src, dst);
    }
}
