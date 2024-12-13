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

import com.typesafe.config.Config;

import java.lang.reflect.Type;
import javax.annotation.Nullable;

interface ConfigBeanMapper {

    // FIXME: simplify & document, for example Type seems to be overkill
    <T> T getObject(HoconConfigProvider factory, Config config, @Nullable String property, Type valueType);
}


