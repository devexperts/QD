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

import com.dxfeed.ipf.InstrumentProfileType;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

class OSIStatement extends Statement {
    OSIStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        compiler.skipToken(')');
        compiler.skipToken(';');
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        String symbol = ctx.currentProfile().getSymbol();
        if (!ctx.currentProfile().getType().equals(InstrumentProfileType.OPTION.name()) || symbol.length() < 4 || symbol.charAt(0) != '.' || symbol.charAt(1) == '/')
            return ControlFlow.NORMAL;
        String mmy = ctx.currentProfile().getMMY();
        String suffix = mmy.substring(Math.max(mmy.length() - 6, 0)) + ctx.currentProfile().getCFI().charAt(1) + formatDouble(ctx.currentProfile().getStrike());
        if (symbol.endsWith(suffix))
            return ControlFlow.NORMAL;

        ctx.copyProfile().setSymbol(symbol.substring(0, symbol.length() - 2) + suffix);
        incModificationCounter(ctx);
        return ControlFlow.NORMAL;
    }

    private static String formatDouble(double d) {
        return new DecimalFormat("0.######", new DecimalFormatSymbols(Locale.US)).format(d);
    }
}
