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
import java.util.List;

/**
 * Marks an interface or abstract class that is an extension point via services annotation framework.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    /**
     * The name of the static combine method that is used by
     * {@link Services#createService(Class, ClassLoader, String) createService} method to
     * combine multiple service implementations into one. This method should have
     * a single {@link List} arguments, receive a list of service instances to combine in
     * {@link ServiceProvider#order() order}.
     * If combine method is not defined, then {@code createService}
     * return the first instance in order.
     */
    public String combineMethod() default "";

    /**
     * The name of the static upgrade method that is used by
     * {@link Services#createService(Class, ClassLoader, String) createService} method to
     * upgrade instances of this service to a more recent version of service interface that
     * supersedes this legacy interface.
     */
    public String upgradeMethod() default "";
}
