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
import com.devexperts.qd.config.Required;

/**
 * Convenient base for the root configuration bean of a {@link Module}
 */
@Experimental
public abstract class AbstractModuleConfig {

    private final String type; // fixed module type ID

    @Required
    protected String name;

    public AbstractModuleConfig(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        // Module type is defined by the creator and fixed.
        // Config management environment might try to fill it, and it shall pass if the actual value match expected.
        if (!this.type.equalsIgnoreCase(type))
            throw new IllegalArgumentException("Illegal type " + type);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('{');
        fieldsToString(sb);
        sb.append('}');
        return sb.toString();
    }

    protected StringBuilder fieldsToString(StringBuilder sb) {
        return sb
            .append("type=").append(type)
            .append(",name=").append(name);
    }
}
