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

import com.devexperts.logging.Logging;

/**
 * Utility methods for retrieving system properties without getting a {@link SecurityException}.
 *
 * Unless otherwise is specified all methods of this class conceal and log any exceptions and
 * return default value in case of any failure.
 */
public final class SystemProperties {

    // We need log initialization to be deferred as late as possible. SystemProperties class is used
    // in static section of many other classes, and we don't want loading of those other classes to
    // trigger initialization of logging subsystem.
    private static Logging log() {
        return Logging.getLogging(SystemProperties.class);
    }

    /**
     * Gets the string system property indicated by the specified key.
     *
     * <p> This method behaves as {@link System#getProperty(String, String) System.getProperty(key, defValue)},
     * except for the fact that it conceals {@link SecurityException}.
     *
     * @param key system property key
     * @param defValue default value
     * @return string system property indicated by the specified key
     * or default value if failed to acquire the property
     * @see System#getProperty(String, String) System.getProperty(key, def)
     */
    public static String getProperty(String key, String defValue) {
        try {
            return System.getProperty(key, defValue);
        } catch (Throwable t) {
            log().error("Failed to acquire correct value for \"" + key + "\" system property", t);
            return defValue;
        }
    }

    /**
     * Gets the string system property indicated by the specified class and property name.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> This method behaves as {@link System#getProperty(String, String) System.getProperty(key, defValue)},
     * except for the fact that it conceals {@link SecurityException}.
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @return string system property indicated by the specified key
     *         or default value if failed to acquire the property
     * @see System#getProperty(String, String) System.getProperty(key, def)
     */
    public static String getProperty(Class<?> c, String propName, String defValue) {
        return getProperty(key(c, propName), defValue);
    }

    /**
     * Gets the integer system property indicated by the specified key.
     *
     * <p> This method can parse numbers in decimal, hexadecimal, and octal radix
     * (the same way as {@link Integer#getInteger(String, int) Integer.getInteger(key, defValue)} method does).
     *
     * @param key system property key
     * @param defValue default value
     * @return integer system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static int getIntProperty(String key, int defValue) {
        try {
            // we don't call Integer.getInteger(...) directly in order to log any possible exceptions.
            String propStr = System.getProperty(key);
            return propStr == null ? defValue : Integer.decode(propStr);
        } catch (Throwable t) {
            log().error("Failed to acquire correct value for \"" + key + "\" integer system property", t);
            return defValue;
        }
    }

    /**
     * Gets the integer system property indicated by the specified class and property name.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> This method can parse numbers in decimal, hexadecimal, and octal radix
     * (the same way as {@link Integer#getInteger(String, int) Integer.getInteger(key, defValue)} method does).
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @return integer system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static int getIntProperty(Class<?> c, String propName, int defValue) {
        return getIntProperty(key(c, propName), defValue);
    }

    /**
     * Gets the integer system property indicated by the specified key
     * and shrinks it into specified range.
     *
     * <p> This methods behaves likely to {@link #getIntProperty(String, int)
     * getIntProperty(key, defValue)} but if the resulting value is less than
     * {@code minValue} or greater than {@code maxValue} a warning is logged
     * and the corresponding boundary value is returned.
     *
     * @param key system property key
     * @param defValue default value
     * @param minValue minimum allowable value
     * @param maxValue maximum allowable value
     * @return integer system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     * @throws IllegalArgumentException if {@code minValue&le;defValue&le;maxValue} condition breaks
     */
    public static int getIntProperty(String key, int defValue, int minValue, int maxValue) {
        if (minValue > defValue || defValue > maxValue)
            throw new IllegalArgumentException();
        int result = getIntProperty(key, defValue);
        if (result > maxValue) {
            log().warn(result + " exceeds maximum allowed value for \"" + key + "\" integer system property; " + maxValue + " value will be used.");
            return maxValue;
        } else if (result < minValue) {
            log().warn(result + " is less than minimum allowed value for \"" + key + "\" integer system property; " + minValue + " value will be used.");
            // Unfortunately, there is no established antonym for "exceed" in English language yet.
            // ...At least there weren't any by the moment this piece of code was written.
            return minValue;
        } else
            return result;
    }

    /**
     * Gets the integer system property indicated by the specified class and property name
     * and shrinks it into specified range.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> This methods behaves likely to {@link #getIntProperty(String, int)
     * getIntProperty(key, defValue)} but if the resulting value is less than
     * {@code minValue} or greater than {@code maxValue} a warning is logged
     * and the corresponding boundary value is returned.
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @param minValue minimum allowable value
     * @param maxValue maximum allowable value
     * @return integer system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     * @throws IllegalArgumentException if {@code minValue&le;defValue&le;maxValue} condition breaks
     */
    public static int getIntProperty(Class<?> c, String propName, int defValue, int minValue, int maxValue) {
        return getIntProperty(key(c, propName), defValue, minValue, maxValue);
    }

    /**
     * Gets the long system property indicated by the specified key.
     *
     * <p> This method can parse numbers in decimal, hexadecimal, and octal radix
     * (the same way as {@link Long#getLong(String, long) Long.getLong(key, defValue)} method does).
     *
     * @param key system property key
     * @param defValue default value
     * @return long system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static long getLongProperty(String key, long defValue) {
        try {
            // we don't call Long.getLong(...) directly in order to log any possible exceptions.
            String propStr = System.getProperty(key);
            return propStr == null ? defValue : Long.decode(propStr);
        } catch (Throwable t) {
            log().error("Failed to acquire correct value for \"" + key + "\" long system property", t);
            return defValue;
        }
    }

    /**
     * Gets the long system property indicated by the specified class and property name.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> This method can parse numbers in decimal, hexadecimal, and octal radix
     * (the same way as {@link Long#getLong(String, long) Long.getLong(key, defValue)} method does).
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @return long system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static long getLongProperty(Class<?> c, String propName, long defValue) {
        return getLongProperty(key(c, propName), defValue);
    }

    /**
     * Gets the long system property indicated by the specified key
     * and shrinks it into specified range.
     *
     * <p> This methods behaves likely to {@link #getLongProperty(String, long)
     * getLongProperty(key, defValue)} but if the resulting value is less than
     * {@code minValue} or greater than {@code maxValue} a warning is logged
     * and the corresponding boundary value is returned.
     *
     * @param key system property key
     * @param defValue default value
     * @param minValue minimum allowable value
     * @param maxValue maximum allowable value
     * @return long system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     * @throws IllegalArgumentException if {@code minValue&le;defValue&le;maxValue} condition breaks
     */
    public static long getLongProperty(String key, long defValue, long minValue, long maxValue) {
        if (minValue > defValue || defValue > maxValue)
            throw new IllegalArgumentException();
        long result = getLongProperty(key, defValue);
        if (result > maxValue) {
            log().warn(result + " exceeds maximum allowed value for \"" + key + "\" long system property; " + maxValue + " value will be used.");
            return maxValue;
        } else if (result < minValue) {
            log().warn(result + " is less than minimum allowed value for \"" + key + "\" long system property; " + minValue + " value will be used.");
            return minValue;
        } else
            return result;
    }

    /**
     * Gets the long system property indicated by the specified class and property name
     * and shrinks it into specified range.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> This methods behaves likely to {@link #getLongProperty(String, long)
     * getLongProperty(key, defValue)} but if the resulting value is less than
     * {@code minValue} or greater than {@code maxValue} a warning is logged
     * and the corresponding boundary value is returned.
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @param minValue minimum allowable value
     * @param maxValue maximum allowable value
     * @return long system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     * @throws IllegalArgumentException if {@code minValue&le;defValue&le;maxValue} condition breaks
     */
    public static long getLongProperty(Class<?> c, String propName, long defValue, long minValue, long maxValue) {
        return getLongProperty(key(c, propName), defValue, minValue, maxValue);
    }

    /**
     * Gets the boolean system property indicated by the specified key.
     *
     * <p> Unlike {@link Boolean#getBoolean(String) Boolean.getBoolean(key)}
     * this method recognizes defined properties with empty values as {@code true}
     * and returns the default value (and logs an error) if the value of a specified
     * property is neither of "true", "false" or "" (case-insensetively).
     * @param key system property key
     * @param defValue default value
     * @return boolean system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static boolean getBooleanProperty(String key, boolean defValue) {
        try {
            String propStr = System.getProperty(key);
            return propStr == null ? defValue : parseBooleanValue(propStr);
        } catch (Throwable t) {
            log().error("Failed to acquire correct value for \"" + key + "\" boolean system property", t);
            return defValue;
        }
    }

    /**
     * Gets the boolean system property indicated by the specified class and property name.
     * The property key is equal to <code>c.getName() + "." + propName</code>.
     *
     * <p> Unlike {@link Boolean#getBoolean(String) Boolean.getBoolean(key)}
     * this method recognizes defined properties with empty values as {@code true}
     * and returns the default value (and logs an error) if the value of a specified
     * property is neither of "true", "false" or "" (case-insensetively).
     *
     * @param c the class that the property is part of
     * @param propName property name in the class. Use <b>camelCase</b> naming starting with lowercase first letter
     * @param defValue default value
     * @return boolean system property indicated by the specified key
     * or default value if failed to acquire or parse the property
     */
    public static boolean getBooleanProperty(Class<?> c, String propName, boolean defValue) {
        return getBooleanProperty(key(c, propName), defValue);
    }

    /**
     * Utility method that parses boolean value from a string.
     *
     * This method recognizes "" (empty string) and "true" as {@code true}
     * and "false" as {@code false} (case-insensitively); all other values
     * are considered invalid.
     * @param value string to parse
     * @return boolean value, represented by given string
     * @throws IllegalArgumentException if the string was neither of "", "true" or "false" (case-insensitively)
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static boolean parseBooleanValue(String value) throws IllegalArgumentException {
        if (value.equalsIgnoreCase("") || value.equalsIgnoreCase("true"))
            return true;
        if (value.equalsIgnoreCase("false"))
            return false;
        throw new IllegalArgumentException(value + " is not a valid boolean value");
    }

    private static String key(Class<?> c, String propName) {
        return c.getName() + "." + propName;
    }

    // Suppress instance creation
    private SystemProperties() {}
}
