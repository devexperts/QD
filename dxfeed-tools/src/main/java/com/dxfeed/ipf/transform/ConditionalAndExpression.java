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

class ConditionalAndExpression extends Expression<Boolean> {
    private final Object left;
    private final Object right;

    ConditionalAndExpression(Object left, Object right) {
        super(Boolean.class);
        this.left = left;
        this.right = right;
        Compiler.getBoolean(Compiler.newTestContext(), left); // Early check of expression constraints (data types)
        Compiler.getBoolean(Compiler.newTestContext(), right); // Early check of expression constraints (data types)
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        return Compiler.getBoolean(ctx, left) && Compiler.getBoolean(ctx, right);
    }
}
