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
package com.devexperts.rmi.impl;

import java.lang.reflect.Method;
import java.util.Arrays;

enum ObjectMethods {
    TO_STRING(getObjMethod("toString")),
    EQUALS(getObjMethod("equals", Object.class)),
    HASH_CODE(getObjMethod("hashCode"));

    private static Method getObjMethod(String name, Class<?>... params) {
        try {
            return Object.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private Method method;

    ObjectMethods(Method method) {
        this.method = method;
    }

    public static ObjectMethods getMethod(Method method) {
        for (ObjectMethods m : values()) {
            if (method.getName().equals(m.method.getName()) &&
                method.getReturnType().equals(m.method.getReturnType()) &&
                Arrays.equals(method.getParameterTypes(), m.method.getParameterTypes()))
            {
                return m;
            }
        }
        return null;
    }
}
