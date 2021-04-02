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

import com.devexperts.rmi.message.RMIRequestType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks remote service methods. This is an <em>optional</em> annotation.
 * This annotation marks service interfaces methods are used in {@link RMIServer#export(Object, Class) export}
 * and {@link RMIClient#getProxy(Class) getProxy} methods. The
 * The methods that are defined in service interfaces does not have to be annotated.
 * By default, all methods in a service interface are exported with default attributes.
 * This annotation provides means to specify additional attributed for service method.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RMIServiceMethod {
    /**
     * Default remote name of the method. If not specified, then Java name of the method is used.
     * @return remote name of the method.
     */
    public String name() default "";

    /**
     * Request type of the method that is used when this method is invoked via {@link RMIClient#getProxy(Class) proxy}.
     * If not specified, then {@link RMIRequestType#DEFAULT DEFAULT} request type is used.
     */
    public RMIRequestType type() default RMIRequestType.DEFAULT;
}
