/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.management;

import java.lang.annotation.*;
import javax.management.MBeanOperationInfo;

import com.devexperts.annotation.Description;

/***
 * Description for managed type, operation, or attribute.
 * Attribute descriptions shall be attached to the corresponding getter method.
 *
 * @deprecated Use {@link Description} with {@code dgen} instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ManagementDescription {
    /**
     * Description string.
     */
    String value();

    /**
     * Operation impact (one of
     * {@link MBeanOperationInfo#INFO INFO},
     * {@link MBeanOperationInfo#ACTION ACTION}, or
     * {@link MBeanOperationInfo#ACTION_INFO ACTION_INFO}).
     * {@link MBeanOperationInfo#UNKNOWN UNKNOWN} by default.
     *
     * @see MBeanOperationInfo
     */
    int impact() default MBeanOperationInfo.UNKNOWN;

    /**
     * Descriptions of operation parameters.
     */
    ManagementParameterDescription[] parameters() default {};
}
