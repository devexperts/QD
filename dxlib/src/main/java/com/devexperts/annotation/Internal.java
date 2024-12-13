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
 * Signifies that a formally public API element (public class, method or field) is made visible for external code due
 * to some technical reasons and is not intended to be used by the external code (e.g., for testing).
 * Such API elements are subject to incompatible changes, or even removal, in a future release.
 *
 * <p><b>An API bearing this annotation is exempt from any compatibility guarantees made by its containing library.</b>
 */
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Internal {
}
