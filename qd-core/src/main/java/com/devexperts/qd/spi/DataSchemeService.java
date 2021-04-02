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
package com.devexperts.qd.spi;

import com.devexperts.qd.DataScheme;

/**
 * Service that is scheme-specific and can be retrieved via {@link DataScheme#getService(Class)} method.
 * The corresponding scheme is automatically set by {@link DataScheme#getService(Class)} method
 * using {@link #setScheme(DataScheme)} method.
 */
public interface DataSchemeService {
    /**
     * Sets data scheme for this service. This method can be invoked only once in an object lifetime.
     * @throws NullPointerException when scheme is null.
     * @throws IllegalStateException when scheme is already set.
     */
    public void setScheme(DataScheme scheme);
}
