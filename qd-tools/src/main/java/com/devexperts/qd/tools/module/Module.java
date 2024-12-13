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
import com.devexperts.qd.tools.Tools;
import com.devexperts.qd.tools.launcher.Launcher;

/**
 * API of a module (pluggable component) executed by {@link Launcher}.
 *
 * @apiNote The term "module" is chosen to distinguish managed components from the Launcher's perspective from "tools"
 *     managed on the top-level of {@link Tools} starter (Launcher itself is also a "tool")
 *
 * @param <ConfigT> supported configuration bean type. It's recommended that configuration beans implement "deep"
 *     content-based {@link Object#equals} - it will help avoid reconfiguring modules due to irrelevant configuration
 *     modifications.
 */
@Experimental
public interface Module<ConfigT> {

    /**
     * @return name of the module as specified during initialization
     */
    public String getName();

    /**
     * Validate module configuration without applying any changes.
     * Validation is performed on a best-effort basis, configuration matching to the runtime environment
     * (like network ports availability) may be skipped.
     *
     * @param config configuration to be validated
     *
     * @return an object representing unsuccessful validation result or null if validation passed. Type of the
     *     returned object is implementation-specific, {@link Object#toString} method will be used to represent returned
     *     validation results in general case.
     * @throws RuntimeException if some validation issue has occurred.
     */
    public default Object validate(ConfigT config) { return null; }

    /**
     * Initialize module execution with provided configuration.
     * The module shall become "active" after the method invocation.
     *
     * @param config module configuration
     *
     * @apiNote The method is expected to be invoked at most once in a module lifecycle after initialization of the
     *     module.
     *     <br>The {@code config} parameter is expected to pass validation with {@link #validate} method before, but
     *     there is no guaranteed "atomicity" of these operations' pairing.
     *
     * @implSpec Start operation shall be "atomic" in the sense of global resources allocation/reference - in case of
     *     initialization failure, no module "leftovers" shall remain.
     */
    public void start(ConfigT config);

    /**
     * Apply updated module configuration.
     *
     * @param config module configuration
     *
     * @apiNote The module is expected to be "started" before.
     *     <br>The {@code config} parameter is expected to pass validation with {@link #validate} method before, but
     *     there is no guaranteed "atomicity" of these operations' pairing.
     *
     * @implSpec Modules expected to be able to apply an updated configuration on-the-fly. It's expected that active
     *     module can match provided configuration with the current module's state and handle unchanged configuration
     *     without service interrupts.
     */
    public void reconfigure(ConfigT config);

    /**
     * Checks that the module is in active state (still running).
     *
     * @implSpec Method implementation shall be thread-safe and fast.
     *
     * @return true if the module is still active.
     */
    public boolean isActive();

    /**
     * Close module.
     *
     * @implSpec close shall be safe to perform in any module's state.
     */
    public void close();

}
