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
package com.devexperts.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes main information about any element, such as class, method, field.
 * Can be generated via {@code dgen} or placed manually.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {
    /**
     * Element's description.
     */
    String value();

    /**
     * Element's name. Should be non-empty for method parameters.
     */
    String name() default "";
}
