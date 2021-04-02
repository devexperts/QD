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
package com.devexperts.services;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensures smooth transition from older versions of services interfaces to their newer versions.
 *
 * <p>For example, assume there was a service interface or class {@code A}. It was superseded by
 * {@code B extends A} which is annotated with {@code @SupersedesService(A.class)}. A becomes deprecated.
 * The first step in migration is to change all users for {@code A} to {@code B}.
 * This is where {@code SupersedesService} annotation comes into play, since {@link Services} will
 * find providers for {@code A}, if being requested to find providers of {@code B}.
 *
 * <p>As soon as
 * all users of the service had migrated, providers can start providing {@code B} instead of {@code A}.
 * As soon as all providers had migrated, deprecated {@code A} can be removed altogether.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SupersedesService {
    /**
     * Type that was superseded by a type with this annotation.
     */
    public Class<?> value();

    /**
     * The old-to-new static conversion method name to use when the old type
     * (referenced to by {@link #value()}) does not extend the new one that is being annotated.
     * This method names has to correspond to a static method with one argument of type
     * that is equal to {@link #value()} of this annotation.
     */
    public String adapterMethod() default "";
}

