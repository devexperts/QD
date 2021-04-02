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
package com.devexperts.qd.test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Tests marked with this annotation are also tested
 * with {@link com.devexperts.qd.impl.stripe.StripedFactory} by {@link StripedTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TestStriped {
}
