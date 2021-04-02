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

import com.dxfeed.glossary.AdditionalUnderlyings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class RenameStatement extends Statement {
    private final Object oldUnderlying;
    private final Object newUnderlying;

    RenameStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        oldUnderlying = compiler.readExpression();
        Compiler.getString(Compiler.newTestContext(), oldUnderlying); // Early check of expression constraints (data types)
        compiler.skipToken(',');
        newUnderlying = compiler.readExpression();
        Compiler.getString(Compiler.newTestContext(), newUnderlying); // Early check of expression constraints (data types)
        compiler.skipToken(')');
        compiler.skipToken(';');
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        String oldUnd = Compiler.getString(ctx, oldUnderlying);
        String newUnd = Compiler.getString(ctx, newUnderlying);
        boolean modified = false;
        if (ctx.currentProfile().getUnderlying().equals(oldUnd)) {
            ctx.copyProfile().setUnderlying(newUnd);
            modified = true;
        }
        Map<String, Double> map = AdditionalUnderlyings.valueOf(ctx.currentProfile().getAdditionalUnderlyings()).getMap();
        if (map.containsKey(oldUnd)) {
            map = new HashMap<String, Double>(map);
            map.put(newUnd, map.remove(oldUnd));
            ctx.copyProfile().setAdditionalUnderlyings(AdditionalUnderlyings.valueOf(map).getText());
            modified = true;
        }
        if (modified)
            incModificationCounter(ctx);
        return ControlFlow.NORMAL;
    }
}
