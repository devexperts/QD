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
package com.devexperts.qd.test;

import com.devexperts.qd.util.QDConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class QDConfigTest {

    @Test
    public void testParseProperties() {
        checkPP("haba", "haba");
        checkPP("haba(prop)", "haba", "prop");
        checkPP("haba[prop]", "haba", "prop");
        checkPP("haba[prop1](prop2)", "haba", "prop1", "prop2");
        checkPP("haba[prop1,prop2]", "haba", "prop1", "prop2");
        checkPP("haba(prop1,prop2)", "haba", "prop1", "prop2");
        checkPP("(other)haba(prop1,prop2)", "(other)haba", "prop1", "prop2");
        checkPP("[other]haba[prop1,prop2]", "[other]haba", "prop1", "prop2");
        checkPP("[[]]", "", "[]");
        checkPP("[()]", "", "()");
        checkPP("(())", "", "()");
        checkPP("([])", "", "[]");
        checkPP("a[]b", "a[]b");
        checkPP("a[,,]", "a", "", "", "");
        checkPP(" b b ( , , ) ", "b b", "", "", "");
    }

    private void checkPP(String desc, String res, String... rprops) {
        List<String> props = new ArrayList<String>();
        assertEquals(res, QDConfig.parseProperties(desc, props));
        assertEquals(Arrays.asList(rprops), props);
    }
}
