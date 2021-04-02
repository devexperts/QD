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

class ConditionalExpression extends Expression {
    private final Object parameter;
    private final Object first;
    private final Object second;

    ConditionalExpression(Object parameter, Object first, Object second) {
        super(Compiler.getType(first, second));
        this.parameter = parameter;
        this.first = first;
        this.second = second;
        Compiler.getBoolean(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getValue(Compiler.newTestContext(), first, type); // Early check of expression constraints (data types)
        Compiler.getValue(Compiler.newTestContext(), second, type); // Early check of expression constraints (data types)
    }

    @Override
    Object evaluate(TransformContext ctx) {
        return Compiler.getBoolean(ctx, parameter) ? Compiler.getValue(ctx, first, type) : Compiler.getValue(ctx, second, type);
    }
}
