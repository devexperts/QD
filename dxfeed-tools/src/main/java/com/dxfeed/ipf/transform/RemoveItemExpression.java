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
import java.util.StringJoiner;

class RemoveItemExpression extends Expression<String> {
    private final Object parameter;
    private final Object item;

    RemoveItemExpression(Compiler compiler) throws IOException {
        super(String.class);
        compiler.skipToken('(');
        parameter = compiler.readExpression();
        compiler.skipToken(',');
        item = compiler.readExpression();
        compiler.skipToken(')');
        Compiler.getString(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getString(Compiler.newTestContext(), item); // Early check of expression constraints (data types)
    }

    @Override
    String evaluate(TransformContext ctx) {
        String list = Compiler.getString(ctx, parameter);
        String itemToRemove = Compiler.getString(ctx, item);
        if (itemToRemove.isEmpty() || !ListFieldUtil.hasItem(list, itemToRemove))
            return list;
        if (list.equals(itemToRemove))
            return "";

        String[] curItems = list.split(ListFieldUtil.LIST_FIELD_SEPARATOR_STRING);
        StringJoiner sj = new StringJoiner(ListFieldUtil.LIST_FIELD_SEPARATOR_STRING);
        for (String curItem : curItems) {
            if (!curItem.equals(itemToRemove))
                sj.add(curItem);
        }

        return sj.toString();
    }
}
