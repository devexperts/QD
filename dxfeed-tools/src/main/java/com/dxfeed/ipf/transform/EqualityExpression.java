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

class EqualityExpression extends Expression<Boolean> {
    private final Class<?> parameterType;
    private final Object left;
    private final Object right;

    EqualityExpression(Object left, Object right) {
        super(Boolean.class);
        this.parameterType = Compiler.getType(left, right);
        this.left = left;
        this.right = right;
        Compiler.getValue(Compiler.newTestContext(), left, parameterType); // Early check of expression constraints (data types)
        Compiler.getValue(Compiler.newTestContext(), right, parameterType); // Early check of expression constraints (data types)
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        return Compiler.getValue(ctx, left, parameterType).equals(Compiler.getValue(ctx, right, parameterType));
    }
}
