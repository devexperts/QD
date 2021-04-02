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
package com.dxfeed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for {@code getXXX()} methods on properties that need custom mapping to QD fields.
 * Non-annotated pairs of {@code getXXX/setXXX} methods are mapped automatically by default.
 * Overridden methods are not mapped implicitly.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface EventFieldMapping {
    /**
     * Returns QD field name where this event class field maps to.
     * @return QD field name where this event class field maps to.
     */
    String fieldName() default "";

    /**
     * Returns mapping type.
     * @return mapping type.
     */
    EventFieldType type() default EventFieldType.DEFAULT;

    /**
     * Returns true if field is optional, false otherwise.
     * @return true if field is optional, false otherwise.
     */
    boolean optional() default false;
}
