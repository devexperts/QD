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

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import static com.devexperts.util.ConfigUtil.convertStringToObject;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ConfigUtilTest {

    @Test
    public void testBooleanValidValues() {
        assertTrue("'true' should be true", convertStringToObject(boolean.class, "true"));
        assertFalse("'false' should be false", convertStringToObject(boolean.class, "false"));
        assertTrue("Empty string should be true", convertStringToObject(boolean.class, ""));
        assertTrue(convertStringToObject(boolean.class, "TRUE"));
        assertTrue(convertStringToObject(boolean.class, "TrUe"));
        assertFalse(convertStringToObject(boolean.class, "FALSE"));
        assertFalse(convertStringToObject(boolean.class, "FalSe"));
    }

    @Test
    public void testBooleanInvalidValues() {
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "1"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "0"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "yes"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "no"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "t"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "f"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "random"));
    }

    @Test
    public void testByteValidValues() {
        assertEquals("'max' should return Byte.MAX_VALUE", Byte.MAX_VALUE, (byte) convertStringToObject(byte.class, "max"));
        assertEquals("Byte.MAX_VALUE should be 127", Byte.MAX_VALUE, (byte) convertStringToObject(byte.class, "127"));
        assertEquals("Byte.MIN_VALUE should be -128", Byte.MIN_VALUE, (byte) convertStringToObject(byte.class, "-128"));
        assertEquals("0 should be 0", 0, (byte) convertStringToObject(byte.class, "0"));
        assertEquals("123 should be 123", 123, (byte) convertStringToObject(byte.class, "123"));
    }

    @Test
    public void testShortValidValues() {
        assertEquals("'max' should return Short.MAX_VALUE", Short.MAX_VALUE, (short) convertStringToObject(short.class, "max"));
        assertEquals("Short.MAX_VALUE should parse correctly", Short.MAX_VALUE, (int) convertStringToObject(int.class, Short.toString(Short.MAX_VALUE)));
        assertEquals("Short.MIN_VALUE should parse correctly", Short.MIN_VALUE, (int) convertStringToObject(int.class, Short.toString(Short.MIN_VALUE)));
        assertEquals("1k should be 1000", 1000, (short) convertStringToObject(short.class, "1k"));
        assertEquals("10k should be 10000", 10000, (short) convertStringToObject(short.class, "10k"));
        assertEquals("1Ki should be 1024", 1024, (short) convertStringToObject(short.class, "1Ki"));
        assertEquals("-1k should be -1000", -1000, (short) convertStringToObject(short.class, "-1k"));
        assertEquals("1.5k should be 1500", 1500, (short) convertStringToObject(short.class, "1.5k"));
        assertEquals("0 should be 0", 0, (short) convertStringToObject(short.class, "0"));
        assertEquals("12345 should be 12345", 12345, (short) convertStringToObject(short.class, "12345"));
    }

    @Test
    public void testIntValidValues() {
        assertEquals("'max' should return Integer.MAX_VALUE", Integer.MAX_VALUE, (int) convertStringToObject(int.class, "max"));
        assertEquals("Integer.MAX_VALUE should parse correctly", Integer.MAX_VALUE, (int) convertStringToObject(int.class, Integer.toString(Integer.MAX_VALUE)));
        assertEquals("Integer.MIN_VALUE should parse correctly", Integer.MIN_VALUE, (int) convertStringToObject(int.class, Integer.toString(Integer.MIN_VALUE)));
        assertEquals("1k should be 1000", 1000, (int) convertStringToObject(int.class, "1k"));
        assertEquals("1.5k should be 1500", 1500, (int) convertStringToObject(int.class, "1.5k"));
        assertEquals("2M should be 2000000", 2_000_000, (int) convertStringToObject(int.class, "2M"));
        assertEquals("1Ki should be 1024", 1024, (int) convertStringToObject(int.class, "1Ki"));
        assertEquals("2Mi should be 2097152", 2_097_152, (int) convertStringToObject(int.class, "2Mi"));
        assertEquals("-100 should be -100", -100, (int) convertStringToObject(int.class, "-100"));
        assertEquals("1.5k should be 1500", 1500, (int) convertStringToObject(int.class, "1.5k"));
    }

    @Test
    public void testLongValidValues() {
        assertEquals("'max' should return Long.MAX_VALUE", Long.MAX_VALUE, (long) convertStringToObject(long.class, "max"));
        assertEquals("Long.MAX_VALUE should parse correctly", Long.MAX_VALUE, (long) convertStringToObject(long.class, Long.toString(Long.MAX_VALUE)));
        assertEquals("Long.MIN_VALUE should parse correctly", Long.MIN_VALUE, (long) convertStringToObject(long.class, Long.toString(Long.MIN_VALUE)));
        assertEquals("1G should be 1000000000", 1_000_000_000L, (long) convertStringToObject(long.class, "1G"));
        assertEquals("1.5G should be 1500000000", 1_500_000_000L, (long) convertStringToObject(long.class, "1.5G"));
        assertEquals("1T should be 1000000000000", 1_000_000_000_000L, (long) convertStringToObject(long.class, "1T"));
        assertEquals("1Gi should be 1073741824", 1_073_741_824L, (long) convertStringToObject(long.class, "1Gi"));
        assertEquals("1Ti should be 1099511627776", 1_099_511_627_776L, (long) convertStringToObject(long.class, "1Ti"));
        assertEquals("2.5G should be 2500000000", 2_500_000_000L, (long) convertStringToObject(long.class, "2.5G"));
    }

    @Test
    public void testFloatValidValues() {
        assertEquals("Valid float value", 123.456F, convertStringToObject(float.class, "123.456"), 0F);
        assertEquals("1.23456789 should be 1.23456789", 1.23456789F, convertStringToObject(float.class, "1.23456789"), 0F);
        assertEquals("1.23456789k should be 1234.56789", 1234.56789F, convertStringToObject(float.class, "1.23456789k"), 0F);
        assertEquals("1M should be 1000000.0", 1_000_000F, convertStringToObject(float.class, "1M"), 0F);
        assertEquals("1G should be 1000000000.0", 1_000_000_000F, convertStringToObject(float.class, "1G"), 0F);
        assertEquals("1T should be 1000000000000.0", 1_000_000_000_000F, convertStringToObject(float.class, "1T"), 0F);
        assertEquals("1ki should be 1024.0", 1_024F, convertStringToObject(float.class, "1ki"), 0F);
        assertEquals("1Mi should be 1048576.0", 1_048_576F, convertStringToObject(float.class, "1Mi"), 0F);
        assertEquals("1Gi should be 1073741824.0", 1_073_741_824F, convertStringToObject(float.class, "1Gi"), 0F);
        assertEquals("1Ti should be 1099511627776.0", 1_099_511_627_776F, convertStringToObject(float.class, "1Ti"), 0F);
        assertEquals("'max' should return Float.MAX_VALUE", Float.MAX_VALUE, convertStringToObject(float.class, "max"), 0F);
        assertEquals("Float.MAX_VALUE should parse correctly", Float.MAX_VALUE, convertStringToObject(float.class, BigDecimal.valueOf(Float.MAX_VALUE).toPlainString()), 0F);
        assertEquals("-Float.MAX_VALUE should parse correctly", -Float.MAX_VALUE, convertStringToObject(float.class, BigDecimal.valueOf(-Float.MAX_VALUE).toPlainString()), 0F);
        assertEquals("Float.MIN_VALUE should parse correctly", Float.MIN_VALUE, convertStringToObject(float.class, BigDecimal.valueOf(Float.MIN_VALUE).toPlainString()), 0F);
        assertEquals("-Float.MIN_VALUE should parse correctly", -Float.MIN_VALUE, convertStringToObject(float.class, BigDecimal.valueOf(-Float.MIN_VALUE).toPlainString()), 0F);
        assertEquals("Infinity should parse to POSITIVE_INFINITY", Float.POSITIVE_INFINITY, convertStringToObject(float.class, "Infinity"), 0F);
        assertEquals("-Infinity should parse to NEGATIVE_INFINITY", Float.NEGATIVE_INFINITY, convertStringToObject(float.class, "-Infinity"), 0F);
        assertTrue("NaN should parse to Float.NaN", Float.isNaN(convertStringToObject(float.class, "NaN")));
        assertEquals("Zero float", 0F, convertStringToObject(float.class, "0"), 0F);
    }

    @Test
    public void testDoubleValidValues() {
        assertEquals("Valid double value", 123.456, convertStringToObject(double.class, "123.456"), 0.0);
        assertEquals("1.23456789 should be 1.23456789", 1.23456789, convertStringToObject(double.class, "1.23456789"), 0.0);
        assertEquals("1.23456789k should be 1234.56789", 1234.56789, convertStringToObject(double.class, "1.23456789k"), 0.0);
        assertEquals("1M should be 1000000.0", 1_000_000.0, convertStringToObject(double.class, "1M"), 0.0);
        assertEquals("1G should be 1000000000.0", 1_000_000_000.0, convertStringToObject(double.class, "1G"), 0.0);
        assertEquals("1T should be 1000000000000.0", 1_000_000_000_000.0, convertStringToObject(double.class, "1T"), 0.0);
        assertEquals("1ki should be 1024.0", 1_024.0, convertStringToObject(double.class, "1ki"), 0.0);
        assertEquals("1Mi should be 1048576.0", 1_048_576.0, convertStringToObject(double.class, "1Mi"), 0.0);
        assertEquals("1Gi should be 1073741824.0", 1_073_741_824.0, convertStringToObject(double.class, "1Gi"), 0.0);
        assertEquals("1Ti should be 1099511627776.0", 1_099_511_627_776.0, convertStringToObject(double.class, "1Ti"), 0.0);
        assertEquals("'max' should return Double.MAX_VALUE", Double.MAX_VALUE, convertStringToObject(double.class, "max"), 0.0);
        assertEquals("Double.MAX_VALUE should parse correctly", Double.MAX_VALUE, convertStringToObject(double.class, BigDecimal.valueOf(Double.MAX_VALUE).setScale(325, RoundingMode.HALF_UP).toPlainString()), 0.0);
        assertEquals("-Double.MAX_VALUE should parse correctly", -Double.MAX_VALUE, convertStringToObject(double.class, BigDecimal.valueOf(-Double.MAX_VALUE).setScale(325, RoundingMode.HALF_UP).toPlainString()), 0.0);
        assertEquals("Double.MIN_VALUE should parse correctly", Double.MIN_VALUE, convertStringToObject(double.class, BigDecimal.valueOf(Double.MIN_VALUE).setScale(325, RoundingMode.HALF_UP).toPlainString()), 0.0);
        assertEquals("-Double.MIN_VALUE should parse correctly", -Double.MIN_VALUE, convertStringToObject(double.class, BigDecimal.valueOf(-Double.MIN_VALUE).setScale(325, RoundingMode.HALF_UP).toPlainString()), 0.0);
        assertEquals("Infinity should parse to POSITIVE_INFINITY", Double.POSITIVE_INFINITY, convertStringToObject(double.class, "Infinity"), 0.0);
        assertEquals("-Infinity should parse to NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY, convertStringToObject(double.class, "-Infinity"), 0.0);
        assertTrue("NaN should parse to Double.NaN", Double.isNaN(convertStringToObject(double.class, "NaN")));
        assertEquals("Zero double", 0.0, convertStringToObject(double.class, "0"), 0.0);
    }


    // ========== General/Cross-Type Tests ==========

    @Test
    public void testValidNumberFormat() {
        assertNull("Null value should return null for Boolean wrapper", convertStringToObject(Boolean.class, null));
        assertNull("Null value should return null for Byte wrapper", convertStringToObject(Byte.class, null));
        assertNull("Null value should return null for Short wrapper", convertStringToObject(Short.class, null));
        assertNull("Null value should return null for Integer wrapper", convertStringToObject(Integer.class, null));
        assertNull("Null value should return null for Long wrapper", convertStringToObject(Long.class, null));
        assertNull("Null value should return null for Float wrapper", convertStringToObject(Float.class, null));
        assertNull("Null value should return null for Double wrapper", convertStringToObject(Double.class, null));

        // Multiplier case insensitivity
        assertEquals("Lowercase 'k' should work", 1000, (int) convertStringToObject(int.class, "1k"));
        assertEquals("Uppercase 'K' should work", 1000, (int) convertStringToObject(int.class, "1K"));
        assertEquals("Lowercase 'ki' should work", 1024, (int) convertStringToObject(int.class, "1ki"));
        assertEquals("Mixed case 'Ki' should work", 1024, (int) convertStringToObject(int.class, "1Ki"));
        assertEquals("Uppercase 'KI' should work", 1024, (int) convertStringToObject(int.class, "1KI"));

        // Max Keyword case insensitivity
        assertEquals("'max' should return Integer.MAX_VALUE", Integer.MAX_VALUE, (int) convertStringToObject(int.class, "max"));
        assertEquals("'MAX' should return Integer.MAX_VALUE", Integer.MAX_VALUE, (int) convertStringToObject(int.class, "MAX"));
        assertEquals("'Max' should return Integer.MAX_VALUE", Integer.MAX_VALUE, (int) convertStringToObject(int.class, "Max"));

        // Scientific notation
        assertEquals("1e2 should parse as 100", 100, (byte)convertStringToObject(byte.class, "1e2"));
        assertEquals("1e3 should parse as 1000", 1000, (short)convertStringToObject(short.class, "1e3"));
        assertEquals("1e3 should parse as 1000", 1000, (int)convertStringToObject(int.class, "1e3"));
        assertEquals("1e9 should parse as 1000000000", 1_000_000_000L, (long)convertStringToObject(long.class, "1e9"));
        assertEquals("1e5 should parse as 100000", 100000.0, (float) convertStringToObject(float.class, "1e5"), 0.0);
        assertEquals("1.23E5 should parse as 123000", 123000.0, (float) convertStringToObject(float.class, "1.23E5"), 0.0);
        assertEquals("1e5 should parse as 100000", 100000.0, (double) convertStringToObject(double.class, "1e5"), 0.0);
        assertEquals("1.23E5 should parse as 123000", 123000.0, (double) convertStringToObject(double.class, "1.23E5"), 0.0);

        // Leading Zeros
        assertEquals("Leading zeros should be ignored", 123, (int) convertStringToObject(int.class, "00123"));
        assertEquals("Multiple zeros should be parsed as zero", 0, (int) convertStringToObject(int.class, "0000"));
        assertEquals("010 should be parsed as decimal 10, not octal", 10, (int) convertStringToObject(int.class, "010"));

        // Decimal points
        assertEquals("123. should parse as 123.0", 123.0, convertStringToObject(double.class, "123."), 0.0);
        assertEquals(".456 should parse as 0.456", 0.456, convertStringToObject(double.class, ".456"), 0.0);
    }

    @Test
    public void testInvalidNumberFormat() {
        // Primitives with null should throw exception
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, null));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, null));

        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, "not-a-number"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, "not-a-number"));

        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, ""));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, ""));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, ""));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, ""));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, ""));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, ""));

        // Fractional values
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, "12.3"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, "12.3456"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, "12.3456k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "123.456"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "123.4567k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, "123.456"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, "123.4567k"));

        // Leading/trailing whitespace should cause error
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(boolean.class, " true"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, " 123"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, "123 "));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "1 k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, " 123 "));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, " 12 .3"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, "123. 0"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, ""));

        // Overflow
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, "1k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, Short.toString((short) (Byte.MAX_VALUE + 1))));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(byte.class, Short.toString((short) (Byte.MIN_VALUE - 1))));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, Short.MAX_VALUE / 100 + "k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, Integer.toString(Short.MAX_VALUE + 1)));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(short.class, Integer.toString(Short.MIN_VALUE - 1)));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, Integer.MAX_VALUE / 100 + "k"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, Long.toString(Integer.MAX_VALUE + 1L)));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, Long.toString(Integer.MIN_VALUE - 1L)));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, "10000000T"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(long.class, BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE).toString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, BigDecimal.valueOf(Float.MAX_VALUE).add(BigDecimal.ONE).toPlainString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, BigDecimal.valueOf(Float.MAX_VALUE).negate().subtract(BigDecimal.ONE).toPlainString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.ONE).toPlainString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, BigDecimal.valueOf(Double.MAX_VALUE).negate().subtract(BigDecimal.ONE).toPlainString()));

        // Underflow
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(float.class, BigDecimal.valueOf(Float.MIN_VALUE).divide(BigDecimal.valueOf(1.1), 46, RoundingMode.HALF_UP).toPlainString()));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, BigDecimal.valueOf(Double.MIN_VALUE).divide(BigDecimal.valueOf(1.1), 325, RoundingMode.HALF_UP).toPlainString()));

        // ========== Edge Cases ==========
        // Just signs
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "-"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "+"));
        // Just decimal points
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, "."));
        // Multiple decimal points
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(double.class, "123.456.789"));
        // Hex notation not supported
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "0x10"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "-0x10"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "+0x10"));
        // Multiple or misplaced multipliers
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "1kk"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "1k2"));
        assertThrows(InvalidFormatException.class, () -> convertStringToObject(int.class, "k"));
    }
}