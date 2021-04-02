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

import java.io.IOException;

class TypeCastExpression extends Expression {
    private final Object parameter;

    TypeCastExpression(Compiler compiler, Class<?> type) throws IOException {
        super(type);
        compiler.skipToken('(');
        parameter = compiler.readExpression();
        compiler.skipToken(')');
        Compiler.getValue(Compiler.newTestContext(), parameter, type); // Early check of expression constraints (data types)
    }

    @Override
    Object evaluate(TransformContext ctx) {
        return Compiler.getValue(ctx, parameter, type);
    }
}
