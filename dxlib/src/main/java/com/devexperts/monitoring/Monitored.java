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
package com.devexperts.monitoring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates bean attributes that are supposed to be monitored via MARS.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Monitored {
    /**
     * Returns name of the attribute in MARS.
     * @return name of the attribute in MARS.
     */
    public String name();

    /**
     * Returns description of the attribute in MARS.
     * @return description of the attribute in MARS.
     */
    public String description();

    /**
     * Returns true when the value of this property shall be expanded by MARS into sub node.
     * By default, the value is represented as a string.
     *
     * @return true when the value of this property shall be expanded by MARS into sub node.
     */
    public boolean expand() default false;
}
