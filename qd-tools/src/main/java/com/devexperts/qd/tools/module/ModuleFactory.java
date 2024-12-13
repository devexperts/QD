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
import com.devexperts.qd.tools.launcher.Launcher;

/**
 * API of factory for creating {@link Module} instances for {@link Launcher}
 *
 * @param <ConfigT> Configuration bean type
 */
@Experimental
public interface ModuleFactory<ConfigT> {

    /**
     * The type of module provided by the factory
     */
    String getType();

    /**
     * Class of configuration bean required for the module
     */
    Class<ConfigT> getConfigClass();

    /**
     * Creates a module instance
     *
     * @implSpec factory creates a module instance bound to provided runtime environment parameters.
     *     It should not be initialized by factory (see {@link Module#start})
     */
    Module<ConfigT> createModule(ModuleContext context);
}
