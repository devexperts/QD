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
package com.devexperts.util.test;

import com.devexperts.util.GlobListUtil;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class GlobListUtilTest {
    @Test
    public void testGlobs() {
        assertGlob("A,B", "A", "C");
        assertGlob("AB", "AB", "A");
        assertGlob("A*", "AB", "BA");
        assertGlob("A*,B", "AB", "BA");
        assertGlob("A*,B", "B", "");
    }

    private void assertGlob(String glob, String match, String notMatch) {
        Pattern pattern = GlobListUtil.compile(glob);
        assertTrue(pattern.matcher(match).matches());
        assertTrue(!pattern.matcher(notMatch).matches());
    }
}
