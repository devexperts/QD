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

class AssignmentStatement extends Statement {
    private final FieldReference field;
    private final Object parameter;

    AssignmentStatement(Compiler compiler, FieldReference field, int token) throws IOException {
        super(compiler);
        Object expression = compiler.readExpression();
        compiler.skipToken(';');
        this.field = field;
        parameter = token == -1 ? expression : MathExpression.create(field, token, expression);
        field.setValue(Compiler.newTestProfile(), Compiler.getValue(Compiler.newTestContext(), parameter, field.type)); // Early check of statement constraints (data types)
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        Object oldValue = field.getValue(ctx.currentProfile());
        Object newValue = Compiler.getValue(ctx, parameter, field.type);
        if (!newValue.equals(oldValue)) {
            field.setValue(ctx.copyProfile(), newValue);
            incModificationCounter(ctx);
        }
        return ControlFlow.NORMAL;
    }
}
