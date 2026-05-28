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
package com.dxfeed.api.impl;

import com.devexperts.services.ServiceProvider;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterFactory;

/**
 * Default {@link DXFeedFilterFactory} implementation supporting {@link DXEndpointImpl}.
 */
@ServiceProvider
public class DXFeedFilterFactoryImpl implements DXFeedFilterFactory {

    @Override
    public DXFeedFilter create(DXFeedFilter.Builder builder) {
        if (!(builder.getEndpoint() instanceof DXEndpointImpl))
            return null;
        return new DXFeedFilterImpl(builder);
    }
}
