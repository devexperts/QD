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

import org.junit.runners.model.InitializationError;

import java.net.URLClassLoader;
import java.util.Arrays;

class IsolatedClassLoader extends URLClassLoader {

    private final String[] patternsToIsolate;

    private IsolatedClassLoader(String[] patternsToIsolate) {
        super(((URLClassLoader) getSystemClassLoader()).getURLs());
        this.patternsToIsolate = patternsToIsolate.clone();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        for (String pattern : patternsToIsolate) {
            if (name.startsWith(pattern)) {
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    return c;
                }
                return findClass(name);
            }
        }
        return super.loadClass(name);
    }

    public static Class<?> isolatedTestClass(Class<?> testClass) throws InitializationError {
        String testClassName = testClass.getName();

        Isolated annotation = testClass.getAnnotation(Isolated.class);
        String[] isolatedPatterns = (annotation != null) ? annotation.value() : new String[0];
        String[] allPatterns = Arrays.copyOf(isolatedPatterns, isolatedPatterns.length + 1);
        allPatterns[isolatedPatterns.length] = testClassName;

        IsolatedClassLoader classLoader = new IsolatedClassLoader(allPatterns);
        try {
            // Need to reload test class in the separate classloader
            return classLoader.loadClass(testClassName);
        } catch (IllegalArgumentException | SecurityException | ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }
}
