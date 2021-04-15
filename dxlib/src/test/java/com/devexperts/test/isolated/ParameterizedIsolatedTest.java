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
package com.devexperts.test.isolated;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(IsolatedParametersRunnerFactory.class)
@Isolated({"com.devexperts.test.isolated.StaticClass"})
public class ParameterizedIsolatedTest {
    @Parameter
    public String property;

    @Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[] { "foo", "bar" };
    }

    @Test
    public void testStaticFinalProperty() {
        System.setProperty("fooBar.property", property);
        assertEquals(property, StaticClass.PROPERTY);
    }
}

class StaticClass {
    public static final String PROPERTY = System.getProperty("fooBar.property");
}
