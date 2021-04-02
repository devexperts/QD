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

class DeleteStatement extends Statement {
    DeleteStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        compiler.skipToken(')');
        compiler.skipToken(';');
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        incModificationCounter(ctx);
        return ControlFlow.DELETE;
    }
}
