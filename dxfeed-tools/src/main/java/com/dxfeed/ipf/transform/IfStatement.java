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

class IfStatement extends Statement {
    private final Object parameter;
    private final Statement thenStatement;
    private final Statement elseStatement;

    IfStatement(Object parameter, Statement thenStatement, Statement elseStatement) {
        this.parameter = parameter;
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        if (Compiler.getBoolean(ctx, parameter))
            return thenStatement.execute(ctx);
        else
            return elseStatement.execute(ctx);
    }
}
