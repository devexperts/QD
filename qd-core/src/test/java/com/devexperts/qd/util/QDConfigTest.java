/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.util;

import java.util.Arrays;

import com.devexperts.qd.qtp.AddressSyntaxException;
import junit.framework.TestCase;

public class QDConfigTest extends TestCase {
    public void testSplitParenthesisSeparated() {
        assertEquals(Arrays.asList("a"), QDConfig.splitParenthesisSeparatedString("a"));
        assertEquals(Arrays.asList("a(b)c"), QDConfig.splitParenthesisSeparatedString("a(b)c"));
        assertEquals(Arrays.asList("(a)"), QDConfig.splitParenthesisSeparatedString("((a))"));
        assertEquals(Arrays.asList("a", "b"), QDConfig.splitParenthesisSeparatedString("(a)(b)"));
        assertEquals(Arrays.asList("a", "(b)", "c"), QDConfig.splitParenthesisSeparatedString("(a)((b))(c)"));
        assertEquals(Arrays.asList("a(b)c"), QDConfig.splitParenthesisSeparatedString("(a(b)c)"));
    }

    public void testExpectedError() {
        checkError("(abc");
        checkError("(abc)de");
        checkError("(:Quote&(IBM,MSFT)@:12345");
    }

    private void checkError(String s) {
        try {
            QDConfig.splitParenthesisSeparatedString(s);
            fail("Should have failed:" + s);
        } catch (AddressSyntaxException e) {
            return; // expected
        }
    }
}
