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
 * Annotation for methods and constructors used to map reference types to int QD field. This annotation may be used only for:
 * <ul>
 * <li>Constructors with 1 int parameter;</li>
 * <li>Instance methods without parameters returning int;</li>
 * <li>Static methods with 1 int parameter returning target reference type;</li>
 * <li>Static methods with 1 target type parameter returning int.</li>
 * </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ClassValueMapping {
}
