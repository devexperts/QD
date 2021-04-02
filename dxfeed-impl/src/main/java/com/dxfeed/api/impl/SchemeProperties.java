/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import com.devexperts.util.GlobListUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.dxfeed.api.DXEndpoint.DXSCHEME_ENABLED_PROPERTY_PREFIX;
import static com.dxfeed.api.DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY;

public class SchemeProperties {
    private final Map<String, String> patternStrings = new HashMap<>();
    private final Map<String, Pattern> patterns = new HashMap<>();

    public SchemeProperties(Properties props) {
        props.forEach((objKey, objValue) -> {
            String key = (String) objKey;
            if (key.startsWith(DXSCHEME_ENABLED_PROPERTY_PREFIX)) {
                String propertyName = key.substring(DXSCHEME_ENABLED_PROPERTY_PREFIX.length());
                enableEventPropertyIfAbsent(propertyName, (String) objValue);
            }
        });
        if (Boolean.parseBoolean(props.getProperty(DXSCHEME_NANO_TIME_PROPERTY))) {
            enableEventPropertyIfAbsent("Sequence", "*");
            enableEventPropertyIfAbsent("TimeNanoPart", "*");
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof SchemeProperties &&
            patternStrings.equals(((SchemeProperties) o).patternStrings);
    }

    @Override
    public int hashCode() {
        return patternStrings.hashCode();
    }

    private void enableEventPropertyIfAbsent(String propertyName, String patternString) {
        if (patternStrings.containsKey(propertyName))
            return;
        patternStrings.put(propertyName, patternString);
        patterns.put(propertyName, GlobListUtil.compile(patternString));
    }

    /**
     * Returns {@link Boolean#TRUE} or {@link Boolean#FALSE} if specified property should be enabled or disabled
     * instead of it's default behaviour. Otherwise returns {@code null}.
     */
    Boolean isEventPropertyEnabled(String propertyName, String eventName) {
        Pattern pattern = patterns.get(propertyName);
        if (pattern == null)
            return null;
        return pattern.matcher(eventName).matches();
    }
}
