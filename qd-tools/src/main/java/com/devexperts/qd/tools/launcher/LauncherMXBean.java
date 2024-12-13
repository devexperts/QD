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

import com.devexperts.annotation.Experimental;

/**
 * Management interface for {@link Launcher} class.
 *
 * @dgen.annotate method {}
 */
@SuppressWarnings({"unused", "UnnecessaryInterfaceModifier"})
@Experimental
public interface LauncherMXBean {
    /**
     * True when Launcher is active - has any active module.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isActive();

    /**
     * Forces re-reading and applying the configuration from the configured source.
     */
    public void forceReadConfig();

    /**
     * Reports current state for active modules.
     *
     * @param regex Regex to filter and highlight the result
     * @param moduleRegex Regex to filter what module names are reported
     */
    public String reportCurrentState(String regex, String moduleRegex);

    /**
     * Reports event logs for active modules since creation of the Launcher.
     *
     * @param regex Regex to filter and highlight the result
     * @param moduleRegex Regex to filter what module names are reported
     */
    public String reportEventLog(String regex, String moduleRegex);

    /**
     * Reports event logs for closed modules since creation of the Launcher.
     *
     * @param regex Regex to filter and highlight the result
     * @param moduleRegex Regex to filter what module names are reported
     */
    public String reportClosedModulesEventLogs(String regex, String moduleRegex);

    /**
     * Stop all active modules and exit.
     */
    public void stop(); 
}
