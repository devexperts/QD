/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.socket;


import com.devexperts.annotation.Experimental;
import com.devexperts.services.Service;

import javax.annotation.Nullable;

/**
 * Factory for {@link SocketSource} instances required by connectors.
 */
@Experimental
@Service
public interface SocketSourceFactory {

    /**
     * Represents a context that provides additional information for socket source creation.
     */
    public static class Context {

        private final String stripe;

        /**
         * Constructs a new {@code Context} instance with the specified stripe specification.
         *
         * @param stripe the stripe specification (optional).
         */
        public Context(String stripe) {
            this.stripe = stripe;
        }

        /**
         * Returns the stripe specification (optional).
         *
         * @return the stripe specification (if available)
         */
        @Nullable
        public String getStripe() {
            return stripe;
        }

        @Override
        public String toString() {
            return "Context{stripe=" + stripe + "}";
        }
    }

    /**
     * Creates a new {@link SocketSource} for the specified message connector.
     * The created {@link SocketSource} may encapsulate logic for managing network connections,
     * including address resolution and load balancing, specific to the given connector.
     *
     * @param connector the {@link ClientSocketConnector} instance that requires a new socket source.
     * @param context connection-specific context for the required {@link SocketSource}.
     * @return the created {@link SocketSource} instance or {@code null} if the factory does not support
     *     specified configuration
     */
    @Nullable
    public SocketSource createSocketSource(ClientSocketConnector connector, Context context);
}
