/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.dxlink.websocket.application.DxLinkWebSocketApplicationConnectionFactory;
import com.devexperts.qd.dxlink.websocket.transport.DxLinkClientWebSocketConnector;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.SystemProperties;

@ServiceProvider
public class DxLinkWebSocketConnectorFactory implements MessageConnectorFactory {
    private static final String DXLINK_PREFIX = "dxlink:";
    private static final String DXFEED_EXPERIMENTAL_DXLINK_ENABLE = "dxfeed.experimental.dxlink.enable";
    private static final boolean ENABLE_EXPERIMENTAL_FEATURE =
        SystemProperties.getBooleanProperty(DXFEED_EXPERIMENTAL_DXLINK_ENABLE, false);

    @Override
    public MessageConnector createMessageConnector(ApplicationConnectionFactory factory, String address)
        throws InvalidFormatException
    {
        if (!address.startsWith(DXLINK_PREFIX))
            return null;
        if (!ENABLE_EXPERIMENTAL_FEATURE) {
            throw new IllegalStateException(
                "This feature is experimental. You should enable the system property '" +
                    DXFEED_EXPERIMENTAL_DXLINK_ENABLE + "' before using it.");
        }
        return new DxLinkClientWebSocketConnector(
            new DxLinkWebSocketApplicationConnectionFactory(
                ((MessageAdapterConnectionFactory) factory).getMessageAdapterFactory()),
            address.substring(DXLINK_PREFIX.length()));
    }

    @Override
    public Class<? extends MessageConnector> getResultingClass() { return DxLinkClientWebSocketConnector.class; }
}
