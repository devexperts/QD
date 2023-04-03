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
package com.dxfeed.schedule.test;

import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import com.dxfeed.schedule.Schedule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(IsolatedRunner.class)
@Isolated({"com.dxfeed.schedule"})
public class ScheduleDefaultTest {

    @Test
    public void testDefaults() throws IOException {
        int goodHoliday = 20170111;
        int badHoliday = 20170118;
        int worstHoliday = 20170125;
        String def = "date=30000101-000000+0000\n\nhd.GOOD=\\\n" + goodHoliday + ",\\\n\n";
        // test that changing defaults works by adding new holidays list
        Schedule.setDefaults(def.getBytes());
        Schedule goodSchedule = Schedule.getInstance("(tz=GMT;0=;hd=GOOD)");
        checkHoliday(goodSchedule, goodHoliday);
        // test that changing defaults works again by adding yet another holidays list
        Schedule.setDefaults((def + "hd.BAD=\\\n" + badHoliday + ",\\").getBytes());
        Schedule badSchedule = Schedule.getInstance("(tz=GMT;0=;hd=BAD)");
        checkHoliday(goodSchedule, goodHoliday);
        checkHoliday(badSchedule, badHoliday);
        // test that replacing holidays list with new value works and affects old schedule instance
        Schedule.setDefaults((def + "hd.BAD=\\\n" + worstHoliday + ",\\").getBytes());
        checkHoliday(goodSchedule, goodHoliday);
        assertFalse(badSchedule.getDayByYearMonthDay(badHoliday).isHoliday());
        checkHoliday(badSchedule, worstHoliday);
    }

    private void checkHoliday(Schedule schedule, int holiday) {
        for (int i = holiday - 1; i <= holiday + 1; i++) {
            assertEquals(i == holiday, schedule.getDayByYearMonthDay(i).isHoliday());
        }
    }
}
