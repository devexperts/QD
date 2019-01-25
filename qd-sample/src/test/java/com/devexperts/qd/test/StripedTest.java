/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.devexperts.qd.impl.stripe.StripedFactory;
import junit.framework.*;

/**
 * A suite of tests that can also test {@link StripedFactory}.
 * Only tests annotated with {@link TestStriped} are run.
 */
public class StripedTest extends TestCase {
    private static final int N = 16; // stripe size for this test
    private static final List<Class<? extends QDTestBase>> STRIPED_TESTS = Arrays.asList(
        MultiAgentTest.class,
        TickerRemoveTest.class,
        ExamineSubTest.class,
        RecordBufferDataTest.class,
        TickerTest.class
    );

    public static Test suite() {
        TestSuite suite = new TestSuite(StripedTest.class.getName());
        for (Class<? extends QDTestBase> c : STRIPED_TESTS)
            suite.addTest(stripedTest(c));
        return suite;
    }

    private static Test stripedTest(Class<? extends QDTestBase> clazz) {
        try {
            TestSuite suite = new TestSuite(clazz.getName());
            for (Method m : clazz.getMethods())
                if (m.getAnnotation(TestStriped.class) != null) {
                    String name = m.getName();
                    QDTestBase test = clazz.getConstructor(String.class).newInstance(name);
                    test.qdf = new StripedFactory(N);
                    suite.addTest(test);
                }
            return suite;
        } catch (Exception e) {
            throw new RuntimeException(e); // noting wrong should happen
        }
    }
}
