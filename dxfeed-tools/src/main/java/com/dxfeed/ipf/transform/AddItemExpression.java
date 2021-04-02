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

class AddItemExpression extends Expression<String> {
    private final Object parameter;
    private final Object item;

    AddItemExpression(Compiler compiler) throws IOException {
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
        String itemToAdd = Compiler.getString(ctx, item);
        if (itemToAdd.isEmpty() || ListFieldUtil.hasItem(list, itemToAdd))
            return list;
        if (list.isEmpty())
            return itemToAdd;

        // new item will be added before first element bigger than itemToAdd
        String[] curItems = list.split(ListFieldUtil.LIST_FIELD_SEPARATOR_STRING);
        StringJoiner sj = new StringJoiner(ListFieldUtil.LIST_FIELD_SEPARATOR_STRING);
        boolean itemAdded = false;
        for (String curItem : curItems) {
            if (!itemAdded && curItem.compareTo(itemToAdd) > 0) {
                sj.add(itemToAdd);
                itemAdded = true;
            }
            sj.add(curItem);
        }
        if (!itemAdded)
            sj.add(itemToAdd);

        return sj.toString();
    }
}
