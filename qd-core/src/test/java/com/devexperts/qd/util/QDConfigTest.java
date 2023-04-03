/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.util;

import com.devexperts.qd.qtp.AddressSyntaxException;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class QDConfigTest {

    @Test
    public void testSplitParenthesisSeparated() {
        assertEquals(Arrays.asList("a"), QDConfig.splitParenthesisSeparatedString("a"));
        assertEquals(Arrays.asList("a(b)c"), QDConfig.splitParenthesisSeparatedString("a(b)c"));
        assertEquals(Arrays.asList("(a)"), QDConfig.splitParenthesisSeparatedString("((a))"));
        assertEquals(Arrays.asList("a", "b"), QDConfig.splitParenthesisSeparatedString("(a)(b)"));
        assertEquals(Arrays.asList("a", "(b)", "c"), QDConfig.splitParenthesisSeparatedString("(a)((b))(c)"));
        assertEquals(Arrays.asList("a(b)c"), QDConfig.splitParenthesisSeparatedString("(a(b)c)"));
    }

    @Test
    public void testExpectedError() {
        assertSyntaxError("(abc");
        assertSyntaxError("(abc)de");
        assertSyntaxError("(:Quote&(IBM,MSFT)@:12345");
    }

    private static void assertSyntaxError(String s) {
        assertThrows("Should have failed:" + s, AddressSyntaxException.class,
            () -> QDConfig.splitParenthesisSeparatedString(s));
    }
}
