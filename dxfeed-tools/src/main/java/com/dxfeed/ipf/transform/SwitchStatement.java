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

import com.dxfeed.ipf.InstrumentProfileField;

import java.util.Map;

class SwitchStatement extends Statement {
    private final Object parameter;
    private final Class<?> parameterType;
    private final Map<Object, Statement> cases;
    private final Statement defaultStatement;

    private final InstrumentProfileField fastKey;

    SwitchStatement(Object parameter, Class<?> parameterType, Map<Object, Statement> cases, Statement defaultStatement) {
        this.parameter = parameter;
        this.parameterType = parameterType;
        this.cases = cases;
        this.defaultStatement = defaultStatement;

        fastKey = parameterType == String.class && parameter instanceof FieldReference ? ((FieldReference) parameter).ipf : null;
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        Object key = fastKey != null ? fastKey.getField(ctx.currentProfile()) : Compiler.getValue(ctx, parameter, parameterType);
        Statement statement = cases.get(key);
        if (statement == null)
            statement = defaultStatement;

        ControlFlow flow = statement.execute(ctx);
        // Consume BREAK control flow
        return flow == ControlFlow.BREAK ? ControlFlow.NORMAL : flow;
    }
}
