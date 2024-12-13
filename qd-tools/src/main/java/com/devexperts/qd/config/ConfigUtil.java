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
package com.devexperts.qd.config;

import com.devexperts.logging.Logging;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ConfigUtil {
    private static final Logging log = Logging.getLogging(ConfigUtil.class);

    /**
     * Ensures naming consistency in a map of config-beans: key in the map shall match a name-property of a config-bean
     * associated with the key. Name property in a config-bean will be set to the associated key in the map.
     * If name-property is set and mismatches the associated key then it will be reported and overridden.
     *
     * @param <T> type of config beans in a map

     * @param map - map of configuration beans to be processed
     * @param getter - getter method extracting name-property from the bean
     * @param setter - setter method to set a new name-property value
     * @return passed map parameter.
     *
     */
    public static <T> Map<String, T> resolveNames(Map<String, T> map,
        Function<T, String> getter, BiConsumer<T, String> setter)
    {
        for (Map.Entry<String, T> entry : map.entrySet()) {
            T value = entry.getValue();
            String key = entry.getKey();
            String name = getter.apply(value);
            if (name == null) {
                setter.accept(value, key);
            } else if (!name.equals(key)) {
                log.error("Explicit name '" + name + "' does not match external map key '" + key + "' for " + value +
                    " - explicit name is overridden with external map key");
                setter.accept(value, key);
            }
        }
        return map;
    }

    private ConfigUtil() {}
}
