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
package com.devexperts.connector.proto;

import com.devexperts.io.ChunkPool;
import com.devexperts.logging.Logging;

import java.io.IOException;

/**
 * A factory that creates {@link ApplicationConnection application-layer connections}
 * of specific protocol.
 */
public abstract class ApplicationConnectionFactory extends ConfigurableObject {

    public static final ConfigurationKey<String> NAME = ConfigurationKey.create("name", String.class);
    public static final ConfigurationKey<ChunkPool> CHUNK_POOL_CONFIGURATION_KEY = ConfigurationKey.create("chunkPool", ChunkPool.class);

    protected Logging log;

    private String name; // Null when it was not explicitly set, never used by CodecConnectionFactory, always use getName()!
    private ChunkPool chunkPool = ChunkPool.DEFAULT; // never used by CodecConnectionFactory, always use getChunkPool()!

    protected ApplicationConnectionFactory() {
        this(null);
    }

    protected ApplicationConnectionFactory(String name) {
        this.name = name;
        this.log = getLoggingInternal(name == null ? getDefaultName() : name);
    }

    private String getDefaultName() {
        String type = getClass().getSimpleName().replace('$', '-'); // dashes will look nicer in name
        String suffix = "ConnectionFactory";
        if (type.endsWith(suffix))
            type = type.substring(0, type.length() - suffix.length());
        return type;
    }

    /**
     * Creates new {@link ApplicationConnection} for a given transport connection.
     * @param transportConnection the transport connection.
     * @return new {@link ApplicationConnection}
     * @throws IOException if failed to create a connection.
     */
    public abstract ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException;

    /**
     * Returns name of this factory.
     * @return name of this factory.
     */
    public String getName() {
        return name;
    }

    /**
     * Changes name of this factory.
     * @param name name of this factory.
     */
    @Configurable(description = "name of this connection")
    public void setName(String name) {
        this.name = name;
        log = getLoggingInternal(name);
    }

    /**
     * Returns a {@link ChunkPool} used by this factory.
     * @return a {@link ChunkPool} used by this factory
     */
    public ChunkPool getChunkPool() {
        return chunkPool;
    }

    /**
     * Changes {@link ChunkPool} used by this factory.
     * @param chunkPool {@link ChunkPool} used by this factory
     */
    @Configurable(description = "Chunk pool")
    public void setChunkPool(ChunkPool chunkPool) {
        this.chunkPool = chunkPool;
    }

    /**
     * Creates a copy of this {@link ApplicationConnectionFactory} with the same initial configuration.
     */
    @Override
    public ApplicationConnectionFactory clone() {
        return (ApplicationConnectionFactory) super.clone();
    }

    /**
     * Returns string representation of this application connection factory for debugging and monitoring purposes.
     * @return string representation of this application connection factory for debugging and monitoring purposes
     */
    @Override
    public abstract String toString();

    private static Logging getLoggingInternal(String name) {
        return Logging.getLogging(ApplicationConnectionFactory.class.getName() + "." + name);
    }
}
