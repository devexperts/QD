/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.transform;

import com.devexperts.util.TimeUtil;
import com.dxfeed.glossary.PriceIncrements;
import com.dxfeed.ipf.InstrumentProfileType;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

class CMEProductStatement extends Statement {
    private final Object description;
    private final Object multiplier;
    private final Object futureConversion;
    private final Object optionConversion;
    private final Object strikeConversion;

    CMEProductStatement(Compiler compiler) throws IOException {
        super(compiler);
        compiler.skipToken('(');
        description = compiler.readExpression();
        Compiler.getString(Compiler.newTestContext(), description); // Early check of expression constraints (data types)
        compiler.skipToken(',');
        multiplier = compiler.readExpression();
        Compiler.getDouble(Compiler.newTestContext(), multiplier); // Early check of expression constraints (data types)
        compiler.skipToken(',');
        futureConversion = compiler.readExpression();
        Compiler.getDouble(Compiler.newTestContext(), futureConversion); // Early check of expression constraints (data types)
        compiler.skipToken(',');
        optionConversion = compiler.readExpression();
        Compiler.getDouble(Compiler.newTestContext(), optionConversion); // Early check of expression constraints (data types)
        compiler.skipToken(',');
        strikeConversion = compiler.readExpression();
        Compiler.getDouble(Compiler.newTestContext(), strikeConversion); // Early check of expression constraints (data types)
        compiler.skipToken(')');
        compiler.skipToken(';');
    }

    @Override
    ControlFlow execute(TransformContext ctx) {
        if (ctx.currentProfile().getType().equals(InstrumentProfileType.PRODUCT.name())) {
            String desc = Compiler.getString(ctx, description);
            if (desc.length() > 0) {
                int commas = desc.indexOf(",,");
                if (commas >= 0)
                    desc = desc.substring(0, commas) + desc.substring(commas + 1);
                ctx.copyProfile().setDescription(desc);
                incModificationCounter(ctx);
            }
            return ControlFlow.NORMAL;
        }
        String[] data = ctx.currentProfile().getExchangeData().split(";");
        if (data.length != 3)
            throw new IllegalArgumentException("Illegal value of EXCHANGE_DATA: \"" + ctx.currentProfile().getExchangeData() + "\" for instrument profile " + ctx.currentProfile());
        double conversion;
        if (ctx.currentProfile().getType().equals(InstrumentProfileType.FUTURE.name())) {
            String desc = Compiler.getString(ctx, description);
            if (desc.length() > 0) {
                int commas = desc.indexOf(",,");
                Date date = null;
                if (commas >= 0 && ctx.currentProfile().getMMY().length() == 6) {
                    Calendar c = Calendar.getInstance(TimeUtil.getTimeZoneGmt(), Locale.US);
                    c.set(Calendar.YEAR, Integer.parseInt(ctx.currentProfile().getMMY().substring(0, 4)));
                    c.set(Calendar.MONTH, Integer.parseInt(ctx.currentProfile().getMMY().substring(4)) - 1);
                    c.set(Calendar.DATE, 1);
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    date = c.getTime();
                } else if (commas >= 0 && ctx.currentProfile().getExpiration() > 0)
                    date = new Date(ctx.currentProfile().getExpiration() * 24L * 3600 * 1000);
                if (date != null)
                    desc = desc.substring(0, commas + 1) + formatDate(date) + desc.substring(commas + 1);
                ctx.copyProfile().setDescription(desc);
            }
            conversion = Compiler.getDouble(ctx, futureConversion);
        } else if (ctx.currentProfile().getType().equals(InstrumentProfileType.OPTION.name())) {
            double strikeConv = Compiler.getDouble(ctx, strikeConversion);
            if (strikeConv != 0 && strikeConv != 1) {
                ctx.copyProfile().setStrike(Compiler.round(ctx.currentProfile().getStrike() * strikeConv));
                String oldSymbol = ctx.currentProfile().getSymbol();
                int ci = oldSymbol.indexOf(':');
                if (ci < 0)
                    ci = oldSymbol.length();
                int ti = ci - 1;
                while (ti >= 0) {
                    char c = oldSymbol.charAt(ti);
                    if (c >= '0' && c <= '9' || c == '.' || c == '-')
                        ti--;
                    else
                        break;
                }
                ctx.copyProfile().setSymbol(oldSymbol.substring(0, ti + 1) + formatDouble(ctx.currentProfile().getStrike()) + oldSymbol.substring(ci));
            }
            conversion = Compiler.getDouble(ctx, optionConversion);
        } else
            return ControlFlow.NORMAL;
        double mult = Compiler.getDouble(ctx, multiplier);
        if (mult != 0)
            ctx.copyProfile().setMultiplier(mult);
        double oldConversion = Double.parseDouble(data[2]);
        if (conversion != 0 && conversion != oldConversion) {
            ctx.copyProfile().setExchangeData(data[0] + ";" + data[1] + ";" + formatDouble(conversion));
            double[] pi = PriceIncrements.valueOf(ctx.currentProfile().getPriceIncrements()).getPriceIncrements();
            for (int i = 0; i < pi.length; i++)
                pi[i] = Compiler.round(pi[i] * oldConversion / conversion);
            ctx.copyProfile().setPriceIncrements(PriceIncrements.valueOf(pi).getText());
        }
        incModificationCounter(ctx);
        return ControlFlow.NORMAL;
    }

    private static String formatDouble(double d) {
        return new DecimalFormat("0.######", new DecimalFormatSymbols(Locale.US)).format(d);
    }

    private static String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM-yyyy", Locale.US);
        sdf.setTimeZone(TimeUtil.getTimeZoneGmt());
        return sdf.format(date);
    }
}
