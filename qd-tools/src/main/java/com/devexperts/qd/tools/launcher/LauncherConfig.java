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
import com.devexperts.util.TimePeriod;

import java.util.Objects;

/**
 * Launcher configuration bean.
 */
@SuppressWarnings({"unused"})
@Experimental
public class LauncherConfig {

    private TimePeriod configCheckPeriod = TimePeriod.valueOf("10s");
    private TimePeriod configReadPeriod = TimePeriod.valueOf("1h");

    public LauncherConfig() {
    }

    public TimePeriod getConfigCheckPeriod() {
        return configCheckPeriod;
    }

    public void setConfigCheckPeriod(TimePeriod configCheckPeriod) {
        this.configCheckPeriod = configCheckPeriod;
    }

    public TimePeriod getConfigReadPeriod() {
        return configReadPeriod;
    }

    public void setConfigReadPeriod(TimePeriod configReadPeriod) {
        this.configReadPeriod = configReadPeriod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LauncherConfig))
            return false;
        LauncherConfig that = (LauncherConfig) o;
        return Objects.equals(configCheckPeriod, that.configCheckPeriod) &&
            Objects.equals(configReadPeriod, that.configReadPeriod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configCheckPeriod, configReadPeriod);
    }

    @Override
    public String toString() {
        return "Launcher{" +
            "configCheckPeriod=" + configCheckPeriod +
            ", configReadPeriod=" + configReadPeriod +
            "}";
    }
}
