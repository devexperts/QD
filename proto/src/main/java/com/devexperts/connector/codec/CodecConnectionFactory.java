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

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.ConfigurationException;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.io.ChunkPool;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.net.ssl.TrustManager;

public abstract class CodecConnectionFactory extends ApplicationConnectionFactory {
    private ApplicationConnectionFactory delegate;

    protected CodecConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate.getName());
        this.delegate = delegate;
    }

    @Override
    public Set<ConfigurationKey<?>> supportedConfiguration() {
        Set<ConfigurationKey<?>> set = new LinkedHashSet<>(super.supportedConfiguration());
        set.addAll(delegate.supportedConfiguration());
        return set;
    }

    @Override
    public <T> T getConfiguration(ConfigurationKey<T> key) {
        if (super.supportedConfiguration().contains(key))
            return super.getConfiguration(key);
        return delegate.getConfiguration(key);
    }

    @Override
    public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) throws ConfigurationException {
        if (super.supportedConfiguration().contains(key))
            return super.setConfiguration(key, value);
        return delegate.setConfiguration(key, value);
    }

    @Override
    public void reinitConfiguration() {
        delegate.reinitConfiguration();
    }

    @Override
    public CodecConnectionFactory clone() {
        CodecConnectionFactory clone = (CodecConnectionFactory) super.clone();
        clone.delegate = delegate.clone();
        return clone;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    @Configurable(description = "name of this connection")
    public void setName(String name) {
        super.setName(name); // this reconfigures logging
        delegate.setName(name);
    }

    @Override
    public ChunkPool getChunkPool() {
        return delegate.getChunkPool();
    }

    @Override
    @Configurable(description = "Chunk pool")
    public void setChunkPool(ChunkPool chunkPool) {
        super.setChunkPool(chunkPool); // just in case...
        delegate.setChunkPool(chunkPool);
    }

    public ApplicationConnectionFactory getDelegate() {
        return delegate;
    }

    /**
     * Using for SSLConnectionFactory
     */
    public void setTrustManager(TrustManager trustManager) {
        if (delegate instanceof CodecConnectionFactory)
            ((CodecConnectionFactory) delegate).setTrustManager(trustManager);
    }
}
