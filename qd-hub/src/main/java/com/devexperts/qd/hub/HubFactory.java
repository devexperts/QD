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
package com.devexperts.qd.hub;

import com.devexperts.logging.Logging;
import com.devexperts.qd.hub.config.HubConfig;
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.ModuleFactory;
import com.devexperts.util.SystemProperties;

public class HubFactory implements ModuleFactory<HubConfig> {

    private static final String DXFEED_EXPERIMENTAL_HUB_ENABLE = "dxfeed.experimental.hub.enable";
    private static final boolean ENABLE_EXPERIMENTAL_HUB_FEATURE =
        SystemProperties.getBooleanProperty(DXFEED_EXPERIMENTAL_HUB_ENABLE, false);
    private Logging log = Logging.getLogging(HubFactory.class);

    @Override
    public String getType() {
        return HubConfig.MODULE_TYPE;
    }

    @Override
    public Class<HubConfig> getConfigClass() {
        return HubConfig.class;
    }

    @Override
    public Module<HubConfig> createModule(ModuleContext context) {
        if (!ENABLE_EXPERIMENTAL_HUB_FEATURE) {
            throw new UnsupportedOperationException(
                "Hub module is experimental. " +
                "You should enable the system property '" + DXFEED_EXPERIMENTAL_HUB_ENABLE + "' to use it.");
        }
        return new Hub(context);
    }
}
