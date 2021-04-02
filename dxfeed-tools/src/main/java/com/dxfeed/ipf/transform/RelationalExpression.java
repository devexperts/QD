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

class RelationalExpression extends Expression<Boolean> {
    static final int LT = 1;
    static final int LE = 2;
    static final int GT = 3;
    static final int GE = 4;

    private final Class<?> parameterType;
    private final Object left;
    private final int operator;
    private final Object right;

    RelationalExpression(Object left, int operator, Object right) {
        super(Boolean.class);
        this.parameterType = Compiler.getType(left, right);
        this.left = left;
        this.operator = operator;
        this.right = right;
        Compiler.getValue(Compiler.newTestContext(), left, parameterType); // Early check of expression constraints (data types)
        Compiler.getValue(Compiler.newTestContext(), right, parameterType); // Early check of expression constraints (data types)
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        //noinspection unchecked,rawtypes
        int compare = ((Comparable) Compiler.getValue(ctx, left, parameterType)).compareTo(Compiler.getValue(ctx, right, parameterType));
        switch (operator) {
        case LT: return compare < 0;
        case LE: return compare <= 0;
        case GT: return compare > 0;
        case GE: return compare >= 0;
        default: throw new IllegalArgumentException("Unknown operator " + operator);
        }
    }
}
