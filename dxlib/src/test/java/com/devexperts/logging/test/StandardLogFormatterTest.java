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
package com.devexperts.logging.test;

import com.devexperts.logging.LogFormatter;
import com.devexperts.util.TimeUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link LogFormatter} for configuration file loading and thread name conversion.
 */
public class StandardLogFormatterTest extends LogFormatterTestBase {

    @Before
    public void setUp() throws Exception {
        initLogFormatter();
        formatter = new LogFormatter();
    }

    protected void initLogFormatter() throws Exception {
        System.setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
            StandardLogFormatterTest.class.getResource("/test.logformatter.properties").toExternalForm());
    }

    @Test
    public void testFormatting() {
        checkResultMatches("NotMatchingThread", "NotMatchingThread");
        checkResultDoesNotMatch("#a", "ABC");
        checkResultMatches("=Thread1", "Thread2");
        checkResultMatches("A=B=C=Thread1", "Thread3");
        checkResultMatches("Thread123Number", "T123N");
        checkResultMatches("Butrqwerty123", "Butrqwerty123But");
    }

    /**
     * Usage of incorrect replacement should not broke code and should print thread name as is.
     */
    @Test
    public void testIncorrectPattern() {
        checkResultMatches("_Thread", "_Thread");
    }

    @Test
    public void testDaylightSwitchFormatting() {
        LogFormatter log = new LogFormatter(TimeUtil.getTimeZone("Europe/Moscow"));
        long time = 1269725412345L; // 2010-03-28 - from winter time to summer time
        check(log, time, 0, "D 100328 003012.345 [qwe] asd - zxc");
        check(log, time, 1, "D 100328 013012.345 [qwe] asd - zxc");
        check(log, time, 2, "D 100328 033012.345 [qwe] asd - zxc");
        check(log, time, 3, "D 100328 043012.345 [qwe] asd - zxc");
        time = 1288470612345L; // 2010-10-31 - from summer time to winter time
        check(log, time, 0, "D 101031 003012.345 [qwe] asd - zxc");
        check(log, time, 1, "D 101031 013012.345 [qwe] asd - zxc");
        check(log, time, 2, "D 101031 023012.345 [qwe] asd - zxc");
        check(log, time, 3, "D 101031 023012.345 [qwe] asd - zxc");
        check(log, time, 4, "D 101031 033012.345 [qwe] asd - zxc");
        check(log, time, 5, "D 101031 043012.345 [qwe] asd - zxc");
    }

    private void check(LogFormatter log, long time, int hour, String expected) {
        String s = log.format('D', time + hour * 3600000L, "qwe", "asd", "zxc");
        while (s.charAt(s.length() - 1) < ' ')
            s = s.substring(0, s.length() - 1);
        assertEquals(s, expected);
    }
}
