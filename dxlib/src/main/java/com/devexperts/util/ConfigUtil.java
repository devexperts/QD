/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class with methods that are used to configure objects from command line parameters and/or files.
 */
public class ConfigUtil {
    private static final BigDecimal BYTE_MIN_VALUE = BigDecimal.valueOf(Byte.MIN_VALUE);
    private static final BigDecimal BYTE_MAX_VALUE = BigDecimal.valueOf(Byte.MAX_VALUE);
    private static final BigDecimal SHORT_MIN_VALUE = BigDecimal.valueOf(Short.MIN_VALUE);
    private static final BigDecimal SHORT_MAX_VALUE = BigDecimal.valueOf(Short.MAX_VALUE);
    private static final BigDecimal INTEGER_MIN_VALUE = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INTEGER_MAX_VALUE = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal LONG_MIN_VALUE = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX_VALUE = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal FLOAT_MIN_VALUE = BigDecimal.valueOf(Float.MIN_VALUE);
    private static final BigDecimal FLOAT_MAX_VALUE = BigDecimal.valueOf(Float.MAX_VALUE);
    private static final BigDecimal DOUBLE_MIN_VALUE = BigDecimal.valueOf(Double.MIN_VALUE);
    private static final BigDecimal DOUBLE_MAX_VALUE = BigDecimal.valueOf(Double.MAX_VALUE);

    // Use LinkedHashMap to preserve the order of iteration (form long to short prefixes)
    private static final Map<String, Long> SCALE_INDICATORS = new LinkedHashMap<>();

    static {
        // IEC (binary) prefixes - powers of 1024
        SCALE_INDICATORS.put("ki", 1024L);
        SCALE_INDICATORS.put("mi", 1024L * 1024L);
        SCALE_INDICATORS.put("gi", 1024L * 1024L * 1024L);
        SCALE_INDICATORS.put("ti", 1024L * 1024L * 1024L * 1024L);

        // SI (decimal) prefixes - powers of 1000
        SCALE_INDICATORS.put("k", 1_000L);
        SCALE_INDICATORS.put("m", 1_000_000L);
        SCALE_INDICATORS.put("g", 1_000_000_000L);
        SCALE_INDICATORS.put("t", 1_000_000_000_000L);
    }

    private ConfigUtil() {} // do not create

    /**
     * Converts string value of the corresponding type to object.
     * <p>
     * For numeric types (int, long, double, float), this method supports both SI (decimal) and IEC (binary)
     * unit prefixes as postfixes:
     * <ul>
     *   <li>SI prefixes (powers of 1000): k (10<sup>3</sup>), M (10<sup>6</sup>), G (10<sup>9</sup>),
     *   T (10<sup>12</sup>)</li>
     *   <li>IEC prefixes (powers of 1024): Ki (2<sup>10</sup>), Mi (2<sup>20</sup>), Gi (2<sup>30</sup>),
     *   Ti (2<sup>40</sup>)</li>
     * </ul>
     * Examples:
     * <ul>
     *   <li>"1k" → 1,000</li>
     *   <li>"1Ki" → 1,024</li>
     *   <li>"2M" → 2,000,000</li>
     *   <li>"2Mi" → 2,097,152</li>
     * </ul>
     * The prefixes are case-insensitive. The special value "max" returns the maximum value for numeric types.
     *
     * @param type the target type to convert to
     * @param value the string value to convert
     * @param <T> the target type
     * @return the converted value
     * @throws InvalidFormatException if value has wrong format.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertStringToObject(Class<T> type, String value) throws InvalidFormatException {
        // Handle null values
        if (value == null) {
            if (type.isPrimitive()) {
                throw new InvalidFormatException("Cannot convert null to primitive type " + type.getName());
            }
            return null;
        }

        try {
            if (type == String.class) {
                return (T) value;
            } else if (type == byte.class || type == Byte.class) {
                return (T) Byte.valueOf(parseByte(value));
            } else if (type == short.class || type == Short.class) {
                return (T) Short.valueOf(parseShort(value));
            } else if (type == int.class || type == Integer.class) {
                return (T) Integer.valueOf(parseInt(value));
            } else if (type == long.class || type == Long.class) {
                return (T) Long.valueOf(parseLong(value));
            } else if (type == double.class || type == Double.class) {
                return (T) Double.valueOf(parseDouble(value));
            } else if (type == float.class || type == Float.class) {
                return (T) Float.valueOf(parseFloat(value));
            } else if (type == boolean.class || type == Boolean.class) {
                return (T) Boolean.valueOf(parseBoolean(value));
            } else if (type == Date.class) {
                return (T) TimeFormat.DEFAULT.parse(value);
            } else {
                if (Enum.class.isAssignableFrom(type))
                    value = value.toUpperCase(Locale.US);
                try {
                    Method m = type.getMethod("valueOf", String.class);
                    return (T) m.invoke(null, value);
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

    private static boolean parseBoolean(String value) throws InvalidFormatException {
        validateNoWhitespace(value);
        // see com.devexperts.util.SystemProperties.parseBooleanValue
        if (value.isEmpty()) {
            // Empty is considered true to simplify an option enabling aka "address[logDisconnects]"
            return true;
        } else if (value.equalsIgnoreCase("true")) {
            return true;
        } else if (value.equalsIgnoreCase("false")) {
            return false;
        } else {
            throw new InvalidFormatException("Invalid boolean value: \"" + value + "\"");
        }
    }

    private static byte parseByte(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Byte.MAX_VALUE;
        return parseIntegralType(value, BYTE_MIN_VALUE, BYTE_MAX_VALUE, "byte").byteValue();
    }

    private static short parseShort(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Short.MAX_VALUE;
        return parseIntegralType(value, SHORT_MIN_VALUE, SHORT_MAX_VALUE, "short").shortValue();
    }

    private static int parseInt(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Integer.MAX_VALUE;
        return parseIntegralType(value, INTEGER_MIN_VALUE, INTEGER_MAX_VALUE, "int").intValue();
    }

    private static long parseLong(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Long.MAX_VALUE;
        return parseIntegralType(value, LONG_MIN_VALUE, LONG_MAX_VALUE, "long").longValue();
    }

    /**
     * Common parsing logic for all integral types (byte, short, int, long).
     * Validates that the value is a whole number and within the specified range.
     *
     * @param value the string value to parse
     * @param minValue minimum allowed value as BigDecimal
     * @param maxValue maximum allowed value as BigDecimal
     * @param typeName type name for error messages
     * @return parsed value as BigDecimal
     * @throws InvalidFormatException if validation fails
     */
    private static BigDecimal parseIntegralType(String value, BigDecimal minValue, BigDecimal maxValue,
        String typeName) throws InvalidFormatException
    {
        validateNumericFormat(value);
        BigDecimal result = parseWithMultiplier(value);
        // Check for fractional part
        if (result.stripTrailingZeros().scale() > 0) {
            throw new InvalidFormatException("Value must result in a whole number: \"" + value + "\"");
        }
        // Check range using BigDecimal comparison
        if (result.compareTo(maxValue) > 0 || result.compareTo(minValue) < 0) {
            throw new InvalidFormatException("Value " + value + " is out of " + typeName + " range [" +
                minValue.longValue() + ", " + maxValue.longValue() + "]");
        }
        return result;
    }

    private static float parseFloat(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Float.MAX_VALUE;
        if (value.equals("Infinity"))
            return Float.POSITIVE_INFINITY;
        if (value.equals("-Infinity"))
            return Float.NEGATIVE_INFINITY;
        if (value.equals("NaN"))
            return Float.NaN;
        return parseFloatingPointType(value, FLOAT_MIN_VALUE, FLOAT_MAX_VALUE, "float").floatValue();
    }

    private static double parseDouble(String value) throws InvalidFormatException {
        if (value.equalsIgnoreCase("max"))
            return Double.MAX_VALUE;
        if (value.equals("Infinity"))
            return Double.POSITIVE_INFINITY;
        if (value.equals("-Infinity"))
            return Double.NEGATIVE_INFINITY;
        if (value.equals("NaN"))
            return Double.NaN;
        return parseFloatingPointType(value, DOUBLE_MIN_VALUE, DOUBLE_MAX_VALUE, "double").doubleValue();
    }

    /**
     * Common parsing logic for floating-point types (float, double).
     * Validates that the value does not overflow or underflow the specified range.
     *
     * @param value the string value to parse
     * @param minValue minimum allowed positive value as BigDecimal (for underflow check)
     * @param maxValue maximum allowed value as BigDecimal (for overflow check)
     * @param typeName type name for error messages
     * @return parsed value as BigDecimal
     * @throws InvalidFormatException if validation fails
     */
    private static BigDecimal parseFloatingPointType(String value, BigDecimal minValue, BigDecimal maxValue,
        String typeName) throws InvalidFormatException
    {
        validateNumericFormat(value);
        BigDecimal result = parseWithMultiplier(value);
        BigDecimal absResult = result.abs();
        // Check for overflow
        if (absResult.compareTo(maxValue) > 0) {
            throw new InvalidFormatException("Value " + value + " overflows " + typeName + " range");
        }
        // Check for underflow - value is smaller than MIN_VALUE but not zero
        if (absResult.compareTo(BigDecimal.ZERO) > 0 && absResult.compareTo(minValue) < 0) {
            throw new InvalidFormatException("Value " + value + " underflows " + typeName + " range");
        }
        return result;
    }

    private static void validateNoWhitespace(String value) throws InvalidFormatException {
        if (value.isEmpty())
            return;
        if (Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1))) {
            throw new InvalidFormatException("Value contains leading or trailing whitespace: \"" + value + "\"");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                throw new InvalidFormatException("Value contains whitespace: \"" + value + "\"");
            }
        }
    }

    private static void validateNumericFormat(String value) throws InvalidFormatException {
        validateNoWhitespace(value);
        if (value.isEmpty())
            throw new InvalidFormatException("Cannot parse numeric value from an empty string");
        String lowerValue = value.toLowerCase(Locale.US);
        if (lowerValue.startsWith("0x") || lowerValue.startsWith("-0x") || lowerValue.startsWith("+0x"))
            throw new InvalidFormatException("Hexadecimal notation is not supported: \"" + value + "\"");
        if (value.equals("-") || value.equals("+"))
            throw new InvalidFormatException("Invalid numeric value: \"" + value + "\"");
    }

    /**
     * Parses a numeric value with optional scale multiplier (k, M, G, T, Ki, Mi, Gi, Ti) and returns BigDecimal.
     *
     * @param value the string value to parse
     * @return BigDecimal representation of the value with multiplier applied
     * @throws InvalidFormatException if the value format is invalid
     */
    private static BigDecimal parseWithMultiplier(String value) {
        String valueLowerCase = value.toLowerCase(Locale.US);
        for (Map.Entry<String, Long> entry : SCALE_INDICATORS.entrySet()) {
            String scaleIndicator = entry.getKey();
            if (valueLowerCase.endsWith(scaleIndicator)) {
                String baseValue = value.substring(0, value.length() - scaleIndicator.length());
                if (baseValue.isEmpty()) {
                    throw new InvalidFormatException("Missing numeric value before scale indicator: \"" + value + "\"");
                }
                try {
                    BigDecimal base = new BigDecimal(baseValue);
                    BigDecimal multiplier = BigDecimal.valueOf(entry.getValue());
                    return base.multiply(multiplier);
                } catch (NumberFormatException e) {
                    throw new InvalidFormatException("Cannot parse numeric value from string \"" + value + "\"");
                }
            }
        }
        // No multiplier found, parse as is
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Cannot parse numeric value from string \"" + value + "\"");
        }
    }
}
