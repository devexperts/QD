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
package com.devexperts.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class with methods that are used to configure objects from command line parameters and/or files.
 */
public class ConfigUtil {
    private ConfigUtil() {} // do not create

    /**
     * Converts string value of the corresponding type to object.
     * @throws InvalidFormatException if value has wrong format.
     */
    public static Object convertStringToObject(Class<?> type, String value) throws InvalidFormatException {
        if (value == null)
            return null;
        try {
            if (type == String.class) {
                return value;
            } else if (type == int.class) {
                return (int) parseLong(value);
            } else if (type == long.class) {
                return parseLong(value.endsWith("l") || value.endsWith("L") ? value.substring(0, value.length() - 1) : value);
            } else if (type == double.class) {
                return parseDouble(value);
            } else if (type == float.class) {
                return (float) parseDouble(value);
            } else if (type == boolean.class) {
                return value.isEmpty() ? true : Boolean.valueOf(value);
            } else if (type == Date.class) {
                return TimeFormat.DEFAULT.parse(value);
            } else {
                if (Enum.class.isAssignableFrom(type))
                    value = value.toUpperCase(Locale.US);
                try {
                    Method m = type.getMethod("valueOf", String.class);
                    return m.invoke(null, value);
                } catch (NoSuchMethodException e) {
                    // ignore
                } catch (IllegalAccessException e) {
                    throw new InvalidFormatException("Cannot access conversion method: " + e.getMessage(), e);
                } catch (InvocationTargetException e) {
                    throw new InvalidFormatException(e.getTargetException().getMessage(), e);
                }
                throw new InvalidFormatException("Unsupported property type \"" + type.getName() + "\"");
            }
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Value should be a number", e);
        }
    }

    private static long getMultiplier(String value) {
        if (value.isEmpty())
            throw new InvalidFormatException("Cannot parse numeric value from an empty string");
        long multiplier = 1;
        char c = value.charAt(value.length() - 1);
        if (Character.isLetter(c)) {
            switch (Character.toUpperCase(c)) {
            case 'B':
                break;
            case 'K':
                multiplier = 1024;
                break;
            case 'M':
                multiplier = 1024 * 1024;
                break;
            case 'G':
                multiplier = 1024 * 1024 * 1024;
                break;
            default:
                throw new InvalidFormatException("Cannot parse numeric value from string \"" + value + "\"");
            }
        }
        return multiplier;
    }

    private static long parseLong(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Long.MAX_VALUE;
        long multiplier = getMultiplier(value);
        if (multiplier != 1)
            value = value.substring(0, value.length() - 1);
        try {
            return Long.parseLong(value) * multiplier;
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Cannot parse integer value from string \"" + value + "\"");
        }
    }

    private static double parseDouble(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Double.MAX_VALUE;
        long multiplier = getMultiplier(value);
        if (multiplier != 1)
            value = value.substring(0, value.length() - 1);
        try {
            return Double.parseDouble(value) * multiplier;
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Cannot parse numeric value from string \"" + value + "\"");
        }
    }
}
