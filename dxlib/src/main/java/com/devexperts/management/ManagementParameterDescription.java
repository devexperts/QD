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
package com.devexperts.management;

import com.devexperts.annotation.Description;

/**
 * Description for managed operation parameter.
 *
 * @see ManagementDescription
 * @deprecated Use {@link Description} with {@code dgen} instead.
 */
public @interface ManagementParameterDescription {
    /**
     * Parameter name.
     */
    String name();

    /**
     * Parameter description string.
     */
    String value();
}
