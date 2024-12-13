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
package com.devexperts.qd.tools.launcher;

import com.devexperts.qd.tools.module.AbstractModuleConfig;

public class TestModuleConfig extends AbstractModuleConfig {

    static final String MODULE_TYPE = "test-module";

    public TestModuleConfig() {
        super(MODULE_TYPE);
    }
}
