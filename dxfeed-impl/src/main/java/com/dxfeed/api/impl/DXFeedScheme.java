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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.services.ServiceProvider;
import com.devexperts.services.Services;

import javax.annotation.Nonnull;

/**
 * DataScheme for DXFeed API that is dynamically created using registered
 * {@link EventDelegateFactory} implementations.
 */
@ServiceProvider(order = 100)
public class DXFeedScheme extends DefaultScheme {

    private static final SchemeProperties DEFAULT_PROPERTIES =
        new SchemeProperties(System.getProperties());

    private static final Iterable<? extends EventDelegateFactory> EVENT_DELEGATE_FACTORIES =
        Services.createServices(EventDelegateFactory.class, null);

    private static final DXFeedScheme INSTANCE =
        new DXFeedScheme(EVENT_DELEGATE_FACTORIES, DEFAULT_PROPERTIES);

    public static DXFeedScheme getInstance() {
        return INSTANCE;
    }

    static DXFeedScheme withProperties(@Nonnull SchemeProperties schemeProperties) {
        if (schemeProperties.equals(DEFAULT_PROPERTIES))
            return INSTANCE;
        return new DXFeedScheme(EVENT_DELEGATE_FACTORIES, schemeProperties);
    }

    protected DXFeedScheme(Iterable<? extends EventDelegateFactory> eventDelegateFactories,
        @Nonnull SchemeProperties schemeProperties)
    {
        super(new PentaCodec(), createRecords(eventDelegateFactories, schemeProperties));
    }

    private static DataRecord[] createRecords(Iterable<? extends EventDelegateFactory> eventDelegateFactories,
        @Nonnull SchemeProperties schemeProperties)
    {
        SchemeBuilder builder = new SchemeBuilder(schemeProperties);
        for (EventDelegateFactory factory : eventDelegateFactories)
            factory.buildScheme(builder);
        return builder.buildRecords();
    }
}
