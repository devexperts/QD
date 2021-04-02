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

import com.devexperts.util.DayUtil;
import com.dxfeed.schedule.Day;
import com.dxfeed.schedule.Schedule;

import java.io.IOException;
import java.util.Date;

class SetDayOfMonthExpression extends Expression<Date> {
    private final Object parameter;
    private final Object dayOfMonth;

    SetDayOfMonthExpression(Compiler compiler) throws IOException {
        super(Date.class);
        compiler.skipToken('(');
        parameter = compiler.readExpression();
        compiler.skipToken(',');
        dayOfMonth = compiler.readExpression();
        compiler.skipToken(')');
        Compiler.getDate(Compiler.newTestContext(), parameter); // Early check of expression constraints (data types)
        Compiler.getDouble(Compiler.newTestContext(), dayOfMonth); // Early check of expression constraints (data types)
    }

    @Override
    Date evaluate(TransformContext ctx) {
        Day day = Schedule.getInstance(ctx.currentProfile().getTradingHours()).getDayById(Compiler.getDayId(Compiler.getDate(ctx, parameter)));
        int newDay = Compiler.getDouble(ctx, dayOfMonth).intValue();
        if (newDay >= 0)
            return Compiler.getDate(day.getDayId() - day.getDayOfMonth() + newDay);
        int year = day.getYear();
        int month = day.getMonthOfYear();
        int nextMonth = month == 12 ? DayUtil.getDayIdByYearMonthDay(year + 1, 1, 1) : DayUtil.getDayIdByYearMonthDay(year, month + 1, 1);
        return Compiler.getDate(nextMonth + newDay);
    }
}
