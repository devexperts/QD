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

import com.dxfeed.schedule.Day;
import com.dxfeed.schedule.Schedule;

import java.io.IOException;
import java.util.Date;

class SetDayOfWeekExpression extends Expression<Date> {
    private final Object parameter;
    private final Object dayOfWeek;

    SetDayOfWeekExpression(Compiler compiler) throws IOException {
        super(Date.class);
        compiler.skipToken('(');
        parameter = compiler.readExpression();
        compiler.skipToken(',');
        dayOfWeek = compiler.readExpression();
        compiler.skipToken(')');
        Compiler.getDate(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getDouble(Compiler.newTestContext(), dayOfWeek); // Early check of expression constraints (data types)
    }

    @Override
    Date evaluate(TransformContext ctx) {
        Day day = Schedule.getInstance(ctx.currentProfile().getTradingHours()).getDayById(Compiler.getDayId(Compiler.getDate(ctx, parameter)));
        return Compiler.getDate(day.getDayId() - day.getDayOfWeek() + Compiler.getDouble(ctx, dayOfWeek).intValue());
    }
}
