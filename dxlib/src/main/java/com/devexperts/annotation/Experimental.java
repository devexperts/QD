/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Signifies that a public API (public class, method or field) is subject to incompatible changes, or even removal,
 * in a future release.
 *
 * <p><b>An API bearing this annotation is exempt from any compatibility guarantees made by its containing library.</b>
 *
 * @apiNote Some experimental features may be considered not ready for inadvertent use in a reliable environment
 *     without informed consent and require additional system property to "unlock".
 */
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Experimental {
}
