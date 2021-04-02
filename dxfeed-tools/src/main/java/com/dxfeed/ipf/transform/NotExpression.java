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

class NotExpression extends Expression<Boolean> {
    private final Object parameter;

    NotExpression(Object parameter) {
        super(Boolean.class);
        this.parameter = parameter;
        Compiler.getBoolean(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        return !Compiler.getBoolean(ctx, parameter);
    }
}
