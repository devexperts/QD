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

class PrimaryStatement extends Statement {
    private final Object primaryUnderlying;

    PrimaryStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        primaryUnderlying = compiler.readExpression();
        Compiler.getString(Compiler.newTestContext(), primaryUnderlying); // Early check of expression constraints (data types)
        compiler.skipToken(')');
        compiler.skipToken(';');
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        String primaryUnd = Compiler.getString(ctx, primaryUnderlying);
        if (ctx.currentProfile().getUnderlying().equals(primaryUnd))
            return ControlFlow.NORMAL;
        Map<String, Double> map = AdditionalUnderlyings.valueOf(ctx.currentProfile().getAdditionalUnderlyings()).getMap();
        if (map.containsKey(primaryUnd)) {
            map = new HashMap<String, Double>(map);
            map.put(ctx.currentProfile().getUnderlying(), ctx.currentProfile().getSPC());
            ctx.copyProfile().setUnderlying(primaryUnd);
            ctx.copyProfile().setSPC(map.remove(primaryUnd));
            ctx.copyProfile().setAdditionalUnderlyings(AdditionalUnderlyings.valueOf(map).getText());
            incModificationCounter(ctx);
        }
        return ControlFlow.NORMAL;
    }
}
