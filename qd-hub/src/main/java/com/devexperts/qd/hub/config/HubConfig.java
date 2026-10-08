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
package com.devexperts.qd.hub.config;

import com.devexperts.qd.config.ConfigUtil;
import com.devexperts.qd.config.Required;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.tools.module.AbstractModuleConfig;
import com.devexperts.util.InvalidFormatException;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Hub configuration bean.
 */
@SuppressWarnings({"unused"})
public class HubConfig extends AbstractModuleConfig {

    public static final String MODULE_TYPE = "hub";

    private String defaultUplinkBindAddr = MessageConnector.Bindable.ANY_BIND_ADDRESS;
    private String defaultDownlinkBindAddr = MessageConnector.Bindable.ANY_BIND_ADDRESS;

    @Required
    private UniverseConfig universeConfig;
    @Required
    private Map<String, UplinkConfig> uplinks = Collections.emptyMap();
    @Required
    private Map<String, DownlinkConfig> downlinks = Collections.emptyMap();

    public HubConfig() {
        super(MODULE_TYPE);
    }

    public String getDefaultUplinkBindAddr() {
        return defaultUplinkBindAddr;
    }

    public void setDefaultUplinkBindAddr(String defaultUplinkBindAddr) {
        this.defaultUplinkBindAddr = MessageConnector.Bindable.normalizeBindAddr(defaultUplinkBindAddr);
    }

    public String getDefaultDownlinkBindAddr() {
        return defaultDownlinkBindAddr;
    }

    public void setDefaultDownlinkBindAddr(String defaultDownlinkBindAddr) {
        this.defaultDownlinkBindAddr = MessageConnector.Bindable.normalizeBindAddr(defaultDownlinkBindAddr);
    }

    public UniverseConfig getUniverseConfig() {
        return universeConfig;
    }

    public void setUniverseConfig(UniverseConfig universeConfig) {
        this.universeConfig = universeConfig;
    }

    @Nonnull
    public Map<String, UplinkConfig> getUplinks() {
        return uplinks;
    }

    public void setUplinks(@Nonnull Map<String, UplinkConfig> uplinks) {
        this.uplinks = ConfigUtil.resolveNames(Objects.requireNonNull(uplinks),
            UplinkConfig::getName, UplinkConfig::setName, true);
    }

    @Nonnull
    public Map<String, DownlinkConfig> getDownlinks() {
        return downlinks;
    }

    public void setDownlinks(@Nonnull Map<String, DownlinkConfig> downlinks) {
        this.downlinks = ConfigUtil.resolveNames(Objects.requireNonNull(downlinks),
            DownlinkConfig::getName, DownlinkConfig::setName, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof HubConfig))
            return false;
        if (!super.equals(o))
            return false;
        HubConfig hubConfig = (HubConfig) o;
        return Objects.equals(defaultUplinkBindAddr, hubConfig.defaultUplinkBindAddr) &&
            Objects.equals(defaultDownlinkBindAddr, hubConfig.defaultDownlinkBindAddr) &&
            Objects.equals(universeConfig, hubConfig.universeConfig) &&
            Objects.equals(uplinks, hubConfig.uplinks) &&
            Objects.equals(downlinks, hubConfig.downlinks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), defaultUplinkBindAddr, defaultDownlinkBindAddr, universeConfig, uplinks, downlinks);
    }

    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", defaultUplinkBindAddr=").append(defaultUplinkBindAddr)
            .append(", defaultDownlinkBindAddr=").append(defaultDownlinkBindAddr)
            .append(", universeConfig=").append(universeConfig)
            .append(", uplinks=").append(uplinks)
            .append(", downlinks=").append(downlinks);
    }

    // builder methods

    @Override
    public HubConfig withName(String name) {
        return (HubConfig) super.withName(name);
    }

    public HubConfig withDefaultUplinkBindAddr(String defaultUplinkBindAddr) {
        setDefaultUplinkBindAddr(defaultUplinkBindAddr);
        return this;
    }

    public HubConfig withDefaultDownlinkBindAddr(String defaultDownlinkBindAddr) {
        setDefaultDownlinkBindAddr(defaultDownlinkBindAddr);
        return this;
    }

    public HubConfig withUniverseConfig(UniverseConfig universeConfig) {
        setUniverseConfig(universeConfig);
        return this;
    }

    public HubConfig withUplinks(Map<String, UplinkConfig> uplinks) {
        setUplinks(uplinks);
        return this;
    }

    public HubConfig withUplink(UplinkConfig uplink) {
        String name = uplink.getName();
        if (name == null)
            throw new InvalidFormatException("Explicit name is absent for " + uplink);
        uplinks = ConfigHelper.ensureLinkedHashMap(uplinks);
        uplinks.put(name, uplink);
        return this;
    }

    public HubConfig withDownlinks(Map<String, DownlinkConfig> downlinks) {
        setDownlinks(downlinks);
        return this;
    }

    public HubConfig withDownlink(DownlinkConfig downlink) {
        String name = downlink.getName();
        if (name == null)
            throw new InvalidFormatException("Explicit name is absent for " + downlink);
        downlinks = ConfigHelper.ensureLinkedHashMap(downlinks);
        downlinks.put(name, downlink);
        return this;
    }
}
