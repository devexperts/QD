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

import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * {@link ParametersRunnerFactory} for
 * {@link org.junit.runners.Parameterized.UseParametersRunnerFactory UseParametersRunnerFactory} annotation
 * to run tests with parameters in a separate ClassLoader.
 *
 * @see Isolated
 */
public class IsolatedParametersRunnerFactory implements ParametersRunnerFactory {

    @Override
    public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
        return new IsolatedParameterizedRunner(test);
    }

    private static class IsolatedParameterizedRunner extends BlockJUnit4ClassRunnerWithParameters {
        public IsolatedParameterizedRunner(TestWithParameters test) throws InitializationError {
            super(isolated(test));
        }

        private static TestWithParameters isolated(TestWithParameters test) throws InitializationError {
            Class<?> testClass = test.getTestClass().getJavaClass();
            Class<?> isolatedTestClass = IsolatedClassLoader.isolatedTestClass(testClass);
            return new TestWithParameters(test.getName(), new TestClass(isolatedTestClass), test.getParameters());
        }
    }
}

