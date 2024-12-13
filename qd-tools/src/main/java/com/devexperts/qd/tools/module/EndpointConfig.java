/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.module;

import com.devexperts.annotation.Experimental;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.Properties;
import javax.annotation.Nonnull;

/**
 * QDEndpoint configuration entry
 *
 * <p>FIXME: For now it just a container of properties object, but maybe it should be incapsulated somehow
 *     (return a preconfigured builder or apply to a given builder, for example)
 */
@Experimental
public interface EndpointConfig {

    /**
     * Returns a set of properties that can be used with {@link QDEndpoint.Builder#withProperties}
     * @return QDEndpoint configuration properties;
     */
    @Nonnull
    public Properties getProperties();
}
