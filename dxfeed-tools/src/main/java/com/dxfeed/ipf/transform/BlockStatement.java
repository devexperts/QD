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

class BlockStatement extends Statement {
    private final Statement[] statements;

    BlockStatement(Statement[] statements) {
        this.statements = statements;
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        for (Statement statement : statements) {
            ControlFlow flow = statement.execute(ctx);
            if (flow != ControlFlow.NORMAL)
                return flow;
        }
        return ControlFlow.NORMAL;
    }
}
