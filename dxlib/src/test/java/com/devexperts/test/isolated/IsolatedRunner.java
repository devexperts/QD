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

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * JUnit runner to run tests in a separate ClassLoader.
 *
 * @see Isolated
 */
public class IsolatedRunner extends BlockJUnit4ClassRunner {

    public IsolatedRunner(Class<?> clazz) throws InitializationError {
        super(IsolatedClassLoader.isolatedTestClass(clazz));
    }
}

