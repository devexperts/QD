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
package com.devexperts.qd.config.hocon;

import com.devexperts.annotation.Internal;
import com.typesafe.config.Config;

@Internal // internal implementation for ConfigProvider facade
public class HoconConfigProvider {
    private static final DefaultConfigBeanMapper DEFAULT_MAPPER = new DefaultConfigBeanMapper();

    @SuppressWarnings("unchecked")
    public <T> T getConfigBean(Config config, Class<T> beanType) {
        ConfigBeanMapper mapper = getMapper(beanType);
        return (T) mapper.getObject(this, config, null, beanType);
    }


    <T> ConfigBeanMapper getMapper(Class<T> beanType) {
        // TODO: allow registering custom mappers
        // TODO: support specifying mapper with annotation
        return getDefaultMapper();
    }

    private ConfigBeanMapper getDefaultMapper() {
        return DEFAULT_MAPPER;
    }
}
