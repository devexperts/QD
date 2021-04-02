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

import java.util.Set;

class InExpression extends Expression<Boolean> {
    private final Class<?> parameterType;
    private final Object parameter;
    private final Set<?> values;

    private final InstrumentProfileField fastKey;

    InExpression(Class<?> parameterType, Object parameter, Set<?> values) {
        super(Boolean.class);
        this.parameterType = parameterType;
        this.parameter = parameter;
        this.values = values;
        Compiler.getValue(Compiler.newTestContext(), parameter, parameterType); // Early check of expression constraints (data types)

        fastKey = parameterType == String.class && parameter instanceof FieldReference ? ((FieldReference) parameter).ipf : null;
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        Object key = fastKey != null ? fastKey.getField(ctx.currentProfile()) : Compiler.getValue(ctx, parameter, parameterType);
        return values.contains(key);
    }
}
