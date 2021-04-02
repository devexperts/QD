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
package com.dxfeed.ipf.transform;

/**
 * Basic class for computable expressions.
 */
abstract class Expression<T> {
    final Class<T> type;

    Expression(Class<T> type) {
        this.type = type;
    }

    abstract T evaluate(TransformContext ctx);
}
