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

import java.util.Date;

class MathExpression extends Expression {
    private static final int ADD = '+';
    private static final int SUB = '-';
    private static final int MUL = '*';
    private static final int DIV = '/';
    private static final int REM = '%';

    static boolean isOperator(int token) {
        return token == ADD || token == SUB || token == MUL || token == DIV || token == REM;
    }

    private static final Class<?> B = Boolean.class;
    private static final Class<?> D = Date.class;
    private static final Class<?> N = Double.class;
    private static final Class<?> S = String.class;

    static MathExpression create(Object left, int token, Object right) {
        if (!isOperator(token))
            throw new IllegalArgumentException("Unknown operator");
        Class<?> l = Compiler.getType(left);
        Class<?> r = Compiler.getType(right);
        if (l == B || r == B)
            throw new IllegalArgumentException("Incompatible types");
        if (l == D && r == D)
            return new MathExpression(0, N, D, D, left, token, right, token == SUB);
        if (l == D)
            return new MathExpression(1, D, D, N, left, token, right, token == ADD || token == SUB);
        if (r == D)
            return new MathExpression(2, D, N, D, left, token, right, token == ADD);
        if (l == N || r == N)
            return new MathExpression(3, N, N, N, left, token, right, true);
        if (l == S && r == S)
            return new MathExpression(4, S, S, S, left, token, right, token == ADD);
        throw new IllegalArgumentException("Incompatible types");
    }

    private final int variant;
    private final Object left;
    private final int operator;
    private final Object right;

    private MathExpression(int variant, Class<?> type, Class<?> leftType, Class<?> rightType, Object left, int token, Object right, boolean ok) {
        super(type);
        this.variant = variant;
        this.left = left;
        this.operator = token;
        this.right = right;
        if (!ok)
            throw new IllegalArgumentException("Incompatible types");
        Compiler.getValue(Compiler.newTestContext(), left, leftType); // Early check of expression constraints (data types)
        Compiler.getValue(Compiler.newTestContext(), right, rightType); // Early check of expression constraints (data types)
    }

    @Override
    Object evaluate(TransformContext ctx) {
        switch (variant) {
        case 0: return Compiler.getDouble(operate(Compiler.getDayId(Compiler.getDate(ctx, left)), Compiler.getDayId(Compiler.getDate(ctx, right))));
        case 1: return Compiler.getDate(operate(Compiler.getDayId(Compiler.getDate(ctx, left)), Compiler.getDouble(ctx, right)));
        case 2: return Compiler.getDate(operate(Compiler.getDouble(ctx, left), Compiler.getDayId(Compiler.getDate(ctx, right))));
        case 3: return Compiler.getDouble(operate(Compiler.getDouble(ctx, left), Compiler.getDouble(ctx, right)));
        case 4: return Compiler.getString(ctx, left) + Compiler.getString(ctx, right);
        default: throw new IllegalArgumentException("Unknown variant");
        }
    }

    private double operate(double l, double r) {
        switch (operator) {
        case ADD: return l + r;
        case SUB: return l - r;
        case MUL: return l * r;
        case DIV: return l / r;
        case REM: return l % r;
        default: throw new IllegalArgumentException("Unknown operator");
        }
    }
}
