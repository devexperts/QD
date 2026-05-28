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
package com.devexperts.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TimePeriodInfoTest {

    @Test
    public void testConstruction() {
        TimePeriodInfo info = TimePeriodInfo.valueOf(1500, 2000);
        assertEquals(1500, info.getMin());
        assertEquals(2000, info.getMax());
    }

    @Test
    public void testConstructionInvalidBounds() {
        assertThrows("negative min", IllegalArgumentException.class,
            () -> TimePeriodInfo.valueOf(-1, 1000));
        assertThrows("negative max", IllegalArgumentException.class,
            () -> TimePeriodInfo.valueOf(1000, -1));
        assertThrows("min > max", IllegalArgumentException.class,
            () -> TimePeriodInfo.valueOf(2000, 1000));
        assertThrows("add negative to range", IllegalArgumentException.class,
            () -> TimePeriodInfo.valueOf(1000, 2000).add(-5));
        assertThrows("add negative to UNKNOWN", IllegalArgumentException.class,
            () -> TimePeriodInfo.UNKNOWN.add(-1));
    }

    @Test
    public void testUnknown() {
        TimePeriodInfo unknown = TimePeriodInfo.UNKNOWN;
        assertTrue(unknown.isUnknown());
        assertEquals(-1000, unknown.getMin());
        assertEquals(-1000, unknown.getMax());
        assertRoundTrip(unknown);
        assertSame(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf(unknown.toString()));
    }

    @Test
    public void testEmpty() {
        TimePeriodInfo empty = TimePeriodInfo.EMPTY;
        assertFalse(empty.isUnknown());
        assertEquals(0, empty.getMin());
        assertEquals(0, empty.getMax());
        assertRoundTrip(empty);
        assertSame(TimePeriodInfo.EMPTY, TimePeriodInfo.valueOf(empty.toString()));
        assertSame(TimePeriodInfo.EMPTY, empty.add(TimePeriodInfo.EMPTY));
    }

    @Test
    public void testAdd() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(1000, 1000);
        TimePeriodInfo b = TimePeriodInfo.valueOf(2000, 2000);
        TimePeriodInfo combined = a.add(b);
        assertEquals(1000, combined.getMin());
        assertEquals(2000, combined.getMax());
    }

    @Test
    public void testAddRanges() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(500, 2000);
        TimePeriodInfo b = TimePeriodInfo.valueOf(1000, 3000);
        TimePeriodInfo combined = a.add(b);
        assertEquals(500, combined.getMin());
        assertEquals(3000, combined.getMax());
    }

    @Test
    public void testAddUnknownIsIdentity() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(1000, 2000);
        assertSame(a, a.add(TimePeriodInfo.UNKNOWN));
        assertSame(a, TimePeriodInfo.UNKNOWN.add(a));
        assertSame(TimePeriodInfo.UNKNOWN, TimePeriodInfo.UNKNOWN.add(TimePeriodInfo.UNKNOWN));
    }

    @Test
    public void testAddLong() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(1000, 3000);
        TimePeriodInfo result = a.add(500);
        assertEquals(500, result.getMin());
        assertEquals(3000, result.getMax());
    }

    @Test
    public void testAddLongToUnknown() {
        TimePeriodInfo result = TimePeriodInfo.UNKNOWN.add(2000);
        assertEquals(2000, result.getMin());
        assertEquals(2000, result.getMax());
    }

    @Test
    public void testValueOf() {
        TimePeriodInfo info = TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2.0}");
        assertEquals(1500, info.getMin());
        assertEquals(2000, info.getMax());

        // Integer seconds
        info = TimePeriodInfo.valueOf("{\"min\":1,\"max\":2}");
        assertEquals(1000, info.getMin());
        assertEquals(2000, info.getMax());
    }

    @Test
    public void testValueOfEdgeCases() {
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf(null));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf(""));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("garbage"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":-1.0,\"max\":-1.0}"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":-5.0,\"max\":2.0}"));
        // Semantically invalid bounds are tolerated: wire parser maps to UNKNOWN instead of throwing.
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":2,\"max\":1}"));
        // Malformed number rejected by regex pattern
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":abc,\"max\":2.0}"));
    }

    @Test
    public void testRoundTrip() {
        assertRoundTrip(TimePeriodInfo.valueOf(1500, 2000));
        assertRoundTrip(TimePeriodInfo.valueOf(500, 5000));
        assertRoundTrip(TimePeriodInfo.valueOf(0, 0));
        assertRoundTrip(TimePeriodInfo.valueOf(1000, 1000));
    }

    @Test
    public void testToString() {
        assertEquals("{\"min\":0,\"max\":0}", TimePeriodInfo.valueOf(0, 0).toString());
        assertEquals("{\"min\":1,\"max\":1}", TimePeriodInfo.valueOf(1000, 1000).toString());
        assertEquals("{\"min\":1.5,\"max\":1.5}", TimePeriodInfo.valueOf(1500, 1500).toString());
        assertEquals("{\"min\":0.5,\"max\":2}", TimePeriodInfo.valueOf(500, 2000).toString());
    }

    @Test
    public void testEqualsHashCode() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(1500, 2000);
        TimePeriodInfo b = TimePeriodInfo.valueOf(1500, 2000);
        TimePeriodInfo c = TimePeriodInfo.valueOf(1000, 2000);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(TimePeriodInfo.UNKNOWN, a);
        assertNotEquals(null, a);
    }

    @Test
    public void testValueOfInvalidNumbers() {
        // Multiple dots
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":1.2.3,\"max\":2.0}"));
        // Only dots
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":...,\"max\":2.0}"));
        // Letters in number
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":1a2,\"max\":2.0}"));
        // Empty value
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":,\"max\":2.0}"));
        // Duplicate keys
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\":1.0,\"min\":2.0}"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"max\":1.0,\"max\":2.0}"));
    }

    @Test
    public void testValueOfStrictOrder() {
        // "min" first, "max" second — accepted
        TimePeriodInfo info = TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2.0}");
        assertEquals(1500, info.getMin());
        assertEquals(2000, info.getMax());
    }

    @Test
    public void testValueOfRejectsReversedFieldOrder() {
        // Strict "min" first, "max" second. Reversed order → UNKNOWN.
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"max\":2.0,\"min\":1.5}"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"max\":3,\"min\":1}"));
    }

    @Test
    public void testValueOfRejectsWhitespace() {
        // Compact wire form only — no whitespace permitted.
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\"min\" : 1.5, \"max\" : 2}"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{ \"min\":1.5, \"max\":2 }"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\t\"min\"\t:\t1.5\t,\t\"max\"\t:\t2\t}"));
        assertEquals(TimePeriodInfo.UNKNOWN, TimePeriodInfo.valueOf("{\n  \"min\": 1.5,\n  \"max\": 2\n}"));
    }

    @Test
    public void testValueOfAcceptsCompactForm() {
        TimePeriodInfo expected = TimePeriodInfo.valueOf(1500, 2000);
        // Our own toString format
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2}"));
        // Extra trailing zeros (same numeric value)
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.500,\"max\":2.000}"));
    }

    @Test
    public void testValueOfForwardCompatibleExtraFields() {
        // Unknown trailing fields MUST be ignored so future peers can add e.g. "avg"
        // without breaking older parsers.
        TimePeriodInfo expected = TimePeriodInfo.valueOf(1500, 2000);
        // Scalar values
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"avg\":1.7}"));
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"avg\":1.7,\"p95\":1.9}"));
        // Exponent notation
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"count\":1e6}"));
        // true / false / null
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"active\":true}"));
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"active\":false}"));
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"note\":null}"));
        // Quoted string value (including embedded comma & escaped quote)
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"label\":\"abc\"}"));
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"label\":\"a,b\"}"));
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"label\":\"a\\\"b\"}"));
        // Single-level JSON object value (percentile map)
        assertEquals(expected, TimePeriodInfo.valueOf(
            "{\"min\":1.5,\"max\":2,\"stats\":{\"p50\":1.5,\"p95\":1.9}}"));
        // Single-level JSON array value
        assertEquals(expected, TimePeriodInfo.valueOf("{\"min\":1.5,\"max\":2,\"hist\":[1,2,3]}"));
        assertEquals(expected, TimePeriodInfo.valueOf(
            "{\"min\":1.5,\"max\":2,\"tags\":[\"a\",\"b\"]}"));
        // Mix: array, object, and scalar all together
        assertEquals(expected, TimePeriodInfo.valueOf(
            "{\"min\":1.5,\"max\":2,\"hist\":[1,2,3],\"stats\":{\"p50\":1.5},\"count\":42}"));
    }

    @Test
    public void testValueOfLongFactory() {
        // (0, 0) returns shared EMPTY sentinel
        assertSame(TimePeriodInfo.EMPTY, TimePeriodInfo.valueOf(0, 0));
        // Other values allocate
        TimePeriodInfo info = TimePeriodInfo.valueOf(1500, 2000);
        assertEquals(1500, info.getMin());
        assertEquals(2000, info.getMax());
    }

    // Covers `if (newMin == this.min && newMax == this.max) return this;` branch in add(TimePeriodInfo).
    @Test
    public void testAddReturnsThisIdentityWhenOtherFitsInside() {
        TimePeriodInfo wide = TimePeriodInfo.valueOf(500, 3000);
        TimePeriodInfo inner = TimePeriodInfo.valueOf(1000, 2000);       // strictly inside
        TimePeriodInfo leftTouch = TimePeriodInfo.valueOf(500, 2000);    // min shared, max inside
        TimePeriodInfo rightTouch = TimePeriodInfo.valueOf(1000, 3000);  // max shared, min inside
        assertSame(wide, wide.add(inner));
        assertSame(wide, wide.add(leftTouch));
        assertSame(wide, wide.add(rightTouch));
        // Self-add also preserves identity.
        assertSame(wide, wide.add(wide));
    }

    // Covers `if (newMin == other.min && newMax == other.max) return other;` branch in add(TimePeriodInfo).
    @Test
    public void testAddReturnsOtherIdentityWhenThisFitsInside() {
        TimePeriodInfo wide = TimePeriodInfo.valueOf(500, 3000);
        TimePeriodInfo inner = TimePeriodInfo.valueOf(1000, 2000);
        // inner is `this`, wide is `other` → first branch fails, second branch returns other.
        assertSame(wide, inner.add(wide));
    }

    // When both operands have equal bounds, the first branch is reached first and `this` wins.
    @Test
    public void testAddEqualBoundsPrefersThis() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(1000, 2000);
        TimePeriodInfo b = TimePeriodInfo.valueOf(1000, 2000);
        assertSame(a, a.add(b));
        assertSame(b, b.add(a));
    }

    // Overlapping (neither side contains the other) → neither identity branch fires → fresh allocation.
    @Test
    public void testAddOverlappingAllocates() {
        TimePeriodInfo a = TimePeriodInfo.valueOf(500, 2000);
        TimePeriodInfo b = TimePeriodInfo.valueOf(1000, 3000);
        TimePeriodInfo combined = a.add(b);
        assertEquals(500, combined.getMin());
        assertEquals(3000, combined.getMax());
        // Must be a distinct instance, not `a` or `b`.
        assertNotSame(a, combined);
        assertNotSame(b, combined);
    }

    // Covers `if (newMin == this.min && newMax == this.max) return this;` branch in add(long).
    @Test
    public void testAddLongReturnsThisIdentityWhenInsideRange() {
        TimePeriodInfo wide = TimePeriodInfo.valueOf(500, 3000);
        assertSame(wide, wide.add(1500L));   // strictly inside
        assertSame(wide, wide.add(500L));    // min boundary
        assertSame(wide, wide.add(3000L));   // max boundary
    }

    // period outside the current range widens bounds → allocates a fresh instance.
    @Test
    public void testAddLongOutsideRangeAllocates() {
        TimePeriodInfo wide = TimePeriodInfo.valueOf(500, 3000);
        TimePeriodInfo lower = wide.add(100L);
        assertEquals(100, lower.getMin());
        assertEquals(3000, lower.getMax());
        assertNotSame(wide, lower);

        TimePeriodInfo upper = wide.add(5000L);
        assertEquals(500, upper.getMin());
        assertEquals(5000, upper.getMax());
        assertNotSame(wide, upper);
    }

    private static void assertRoundTrip(TimePeriodInfo original) {
        assertEquals(original, TimePeriodInfo.valueOf(original.toString()));
    }
}
