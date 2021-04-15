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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines class prefix patterns that will be loaded in a separate ClassLoader.
 *
 * <p>Sample usage for the standalone tests:
 * <pre><tt>
 * &#064;RunWith(IsolatedRunner.class)
 * &#064;Isolated({"com.sample.LegacyClass"})
 * public class LegacyTest {
 *     &#064;Test
 *     public void testFoo() {
 *         LegacyClass.initializeOnce();
 *     }
 * }
 *
 * &#064;RunWith(IsolatedRunner.class)
 * &#064;Isolated({"com.sample.LegacyClass"})
 * public class AnotherLegacyTest {
 *     &#064;Test
 *     public void testBar() {
 *         // No problem since class will be loaded in a separate ClassLoader
 *         LegacyClass.initializeOnce();
 *     }
 * }
 * </tt></pre>
 *
 * <p>Sample usage for the parameterized tests:
 * <pre><tt>
 * &#064;RunWith(Parameterized.class)
 * &#064;UseParametersRunnerFactory(IsolatedParametersRunnerFactory.class)
 * &#064;Isolated({"com.sample.StaticClass"})
 * public class ParameterizedIsolatedTest {
 *     &#064;Parameter
 *     public String property;
 *
 *     &#064;Parameters(name = "{0}")
 *     public static Object[] params() { return new Object[] { "foo", "bar" }; }
 *
 *     &#064;Test
 *     public void testStaticFinalProperty() {
 *         System.setProperty("fooBar.property", property);
 *         assertEquals(property, StaticClass.PROPERTY);
 *     }
 * }
 *
 * class StaticClass {
 *     public static final String PROPERTY = System.getProperty("fooBar.property");
 * }
 * </tt></pre>
 *
 * <p><b>Note!</b> If you add JUnit class patterns to the {@link Isolated#value()},
 * then it might fail to work since JUnit is using class-loading "magic" to find tests
 * by annotations, etc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Isolated
{
	/**
	 * @return prefix patterns which {@link IsolatedRunner} will load in a separate ClassLoader
	 */
	public String[] value() default {};
}
