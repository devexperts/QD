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
package com.devexperts.connector.codec;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.util.TypedMap;

import java.io.IOException;

/**
 * Base class for <i>codec</i>-connections, such as ssl, zlib, etc.
 * <p/>
 * Codecs may wrap any existing application protocols.
 * A codec pretends to be an application-layer for overlying transport
 * protocol while representing a transport-layer for underlying
 * application connections.
 * <p/>
 * Different codecs may me combined in arbitrary ways.
 * <p/>
 * In order to create specific codec for given application protocol represented
 * by specific {@link com.devexperts.connector.proto.ApplicationConnectionFactory}
 * a {@link CodecFactory} may be used.
 *
 * @param <F> {@link ApplicationConnectionFactory} that produces this type of codec
 * (may be used to store global configuration)
 *
 * @see ApplicationConnection
 * @see CodecFactory
 */
public abstract class CodecConnection<F extends ApplicationConnectionFactory>
    extends ApplicationConnection<F> implements TransportConnection
{
    protected final ApplicationConnection<?> delegate;

    protected CodecConnection(ApplicationConnectionFactory delegateFactory, F config,
        TransportConnection transportConnection) throws IOException
    {
        super(config, transportConnection);
        this.delegate = delegateFactory.createConnection(this);
    }

    @Override
    protected void startImpl() {
        delegate.start();
    }

    @Override
    protected void closeImpl() {
        delegate.close();
    }

    @Override
    public long examine(long currentTime) {
        return delegate.examine(currentTime);
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        return delegate.retrieveChunks(owner);
    }

    @Override
    public boolean processChunks(ChunkList chunks, Object owner) {
        return delegate.processChunks(chunks, owner);
    }

    // ========== TransportConnection implementation (for delegate connection) ==========

    @Override
    public TypedMap variables() {
        return transportConnection.variables();
    }

    @Override
    public void markForImmediateRestart() {
        delegate.markForImmediateRestart();
    }

    @Override
    public void connectionClosed() {
        close();
    }

    @Override
    public void chunksAvailable() {
        notifyChunksAvailable();
    }

    @Override
    public void readyToProcessChunks() {
        notifyReadyToProcess();
    }
}
