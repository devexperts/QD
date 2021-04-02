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

class HasItemExpression extends Expression<Boolean> {
    private final Object parameter;
    private final Object item;

    HasItemExpression(Object parameter, Object item) {
        super(Boolean.class);
        this.parameter = parameter;
        this.item = item;
        Compiler.getString(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getString(Compiler.newTestContext(), item); // Early check of expression constraints (data types)
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        String list = Compiler.getString(ctx, parameter);
        String itemToCheck = Compiler.getString(ctx, item);
        return ListFieldUtil.hasItem(list, itemToCheck);
    }
}
