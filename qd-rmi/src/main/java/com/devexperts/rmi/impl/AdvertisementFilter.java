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
package com.devexperts.rmi.impl;

import com.devexperts.util.InvalidFormatException;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * A connection-level filter that limits service advertisement messages sent over this connection.
 */
public final class AdvertisementFilter {

    /**
     * This filter permits advertising of all services that match the {@link ServiceFilter} configured for a
     * connection
     */
    public static final AdvertisementFilter ALL = new AdvertisementFilter(FilterType.ALL);
    /**
     * This filter does not permit advertising of any services through a connection
     */
    public static final AdvertisementFilter NONE = new AdvertisementFilter(FilterType.NONE);
    private final FilterType type;

    private AdvertisementFilter(FilterType type) {
        this.type = type;
    }

    /**
     * Creates an instance of the filter that corresponds to the given 'filter' string. Only 'all' or 'none' values
     * are supported.
     * <p>
     * Note: this method is invoked via the {@link com.devexperts.connector.proto.ConfigurableObject} framework.
     * @param filter filter string
     * @return an instance of the filter
     * @throws IllegalArgumentException filter string is null or empty
     * @throws InvalidFormatException filter string could not be parsed
     */
    @Nonnull
    public static AdvertisementFilter valueOf(String filter) {
        Objects.requireNonNull(filter, "filter");
        if (filter.trim().isEmpty()) {
            throw new IllegalArgumentException("Advertisement filter is empty");
        }

        String trimmed = filter.trim();
        if (ALL.type.code.equalsIgnoreCase(trimmed)) {
            return ALL;
        }
        if (NONE.type.code.equalsIgnoreCase(trimmed)) {
            return NONE;
        }
        throw new InvalidFormatException("Advertisement filter: unsupported value '" + trimmed + "'. Only " +
            "'all' or 'none' are supported");
    }

    /**
     * @return {@code true} if an RMI endpoint should send service advertisement messages, {@code false} otherwise
     */
    public boolean isSendAdvertisement() {
        return type == FilterType.ALL;
    }

    private enum FilterType {
        // Advertisement messages are sent for all services that match the ServiceFilter configured for a connection
        ALL("all"),
        // Nothing is advertised. Connected clients do not receive any advertisements and therefore
        // do not apply any load balancing
        NONE("none");

        private final String code;

        FilterType(String code) {
            this.code = code;
        }
    }
}
