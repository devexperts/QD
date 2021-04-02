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
package com.dxfeed.webservice.rest;

import com.devexperts.annotation.Description;
import com.dxfeed.webservice.rest.Secure.SecureRole;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PathInfo implements Comparable<PathInfo> {
    public final Method method;
    public final String path; // the path starting from "/"
    public final int helpOrder;
    public final String description;
    public final ParamInfo[] args;
    public final int nArgs;
    public final SecureRole secure;

    public PathInfo(Method method) {
        this.method = method;
        this.path = method.getAnnotation(Path.class).value();
        this.helpOrder = method.getAnnotation(HelpOrder.class).value();
        this.description = method.getAnnotation(Description.class).value();
        this.secure = method.getAnnotation(Secure.class).value();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        nArgs = parameterTypes.length;
        args = new ParamInfo[nArgs];
        for (int i = 0; i < nArgs; i++) {
            Param param = null;
            Description description = null;
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof Param)
                    param = (Param) annotation;
                if (annotation instanceof Description)
                    description = (Description) annotation;
            }
            String paramName = param != null ? param.value() : description.name();
            args[i] = new ParamInfo(paramName, ParamType.forClass(parameterTypes[i]), description.value(), null);
        }
    }

    @Override
    public int compareTo(PathInfo o) {
        return helpOrder - o.helpOrder;
    }

    public void invokeFor(EventsResource resource) throws InvocationTargetException {
        /*
         * Design note: if this reflection-based code is ever found to be slow, please replace reflection with
         * a code that is automatically generated during compilation and does the same function.
         */
        Object[] values = new Object[nArgs];
        for (int i = 0; i < nArgs; i++) {
            values[i] = resource.getRequestValue(args[i]);
        }
        try {
            method.invoke(resource, values);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
