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
package com.devexperts.rmi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks remote service interfaces. This is an <em>optional</em> annotation.
 * This annotation marks service interfaces that are used in {@link RMIServer#export(Object, Class) export}
 * and {@link RMIClient#getProxy(Class) getProxy} methods.
 * The interfaces that are used there does not have to be annotated.
 * This annotation provides means to specify additional attributes for service interface.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RMIServiceInterface {
    /**
     * Default remote name of the service. If not specified, then fully qualified name of the service interface is used.
     * @return remote name of the service.
     */
    public String name() default "";
}
