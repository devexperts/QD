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

class ControlFlowStatement extends Statement {
    static final Statement NOP = new ControlFlowStatement(ControlFlow.NORMAL);
    static final Statement BREAK = new ControlFlowStatement(ControlFlow.BREAK);
    static final Statement RETURN = new ControlFlowStatement(ControlFlow.RETURN);

    private final ControlFlow controlFlow;

    private ControlFlowStatement(ControlFlow controlFlow) {
        this.controlFlow = controlFlow;
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        return controlFlow;
    }
}
