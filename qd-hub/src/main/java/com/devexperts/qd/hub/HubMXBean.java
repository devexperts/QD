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

/**
 * Management interface for {@link Hub} class.
 *
 * @dgen.annotate method {}
 */
@SuppressWarnings({"unused", "UnnecessaryInterfaceModifier"})
public interface HubMXBean {

    /**
     * True when Hub is active - has any active (started) connector.
     */
    public boolean isActive();

    /**
     * Reports current state of the Hub.
     *
     * @param regex Regex to filter and highlight the result
     */
    public String reportCurrentState(String regex);

    /**
     * Reports event log since creation of the Hub.
     *
     * @param regex Regex to filter and highlight the result
     */
    public String reportEventLog(String regex);
}
