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
 * SSEngine adapter that synchronizes all API calls of encapsulated engine.
 * Required to avoid potential deadlock in sun.security.ssl.SSLEngineImpl prior to Java 11 [See QD-1196]
 */
class SSLEngineSynchronizedAdapter extends SSLEngineAdapter {

    /**
     * Create adapter delegating functions to a specified SSL engine.
     *
     * @param engine - SSL engine to be used as delegate.
     */
    SSLEngineSynchronizedAdapter(SSLEngine engine) {
        super(engine);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized SSLSession getSession() {
        return super.getSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized Runnable getDelegatedTask() {
        final Runnable task = super.getDelegatedTask();
        if (task == null)
            return null;
        return () -> {
            synchronized (SSLEngineSynchronizedAdapter.this) {
                task.run();
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return super.getHandshakeStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return super.unwrap(src, dst);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return super.wrap(src, dst);
    }
}
