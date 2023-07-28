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
package com.dxfeed.ondemand.impl;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.util.TimeUtil;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.Assert.assertArrayEquals;

public class BlockTest {

    @Test
    public void testIO() throws ParseException, IOException {
        Block block = new Block();
        block.setSymbol("A");
        block.setType('Q');
        block.setStartTime(getTime("2010-01-16 14:30:00"));
        block.setEndTime(block.getStartTime() + 60 * 1000);
        block.setData(new byte[0], 0, 0);
        ByteArrayOutput out = new ByteArrayOutput();
        block.writeBlock(out);
        assertArrayEquals(new byte[] {
            // Block #1 - with symbol latin 'A', actual length == estimate length
            11, 0, 1, 65, 0, 81, -16, 75, 81, -51, 104, 60,
        }, out.toByteArray());
        block.setSymbol("\u0410");
        block.writeBlock(out);
        assertArrayEquals(new byte[] {
            // Block #1 - with symbol latin 'A', actual length == estimate length
            11, 0, 1, 65, 0, 81, -16, 75, 81, -51, 104, 60,
            // Block #2 - with symbol cyrillic 'A', actual length == estimate length + 1 - triggers length rewriting
            12, 0, 2, -48, -112, 0, 81, -16, 75, 81, -51, 104, 60,
        }, out.toByteArray());
        block.setSymbol(
            "\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410" +
            "\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410" +
            "\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410");
        block.writeBlock(out);
        assertArrayEquals(new byte[] {
            // Block #1 - with symbol latin 'A', actual length == estimate length
            11, 0, 1, 65, 0, 81, -16, 75, 81, -51, 104, 60,
            // Block #2 - with symbol cyrillic 'A', actual length == estimate length + 1 - triggers length rewriting
            12, 0, 2, -48, -112, 0, 81, -16, 75, 81, -51, 104, 60,
            // Block #3 - with long symbol, actual length == 64 - triggers copy of block data
            -128, 64, 0, 54, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48,
            -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48,
            -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48, -112, -48,
            -112, -48, -112, -48, -112, -48, -112, -48, -112, 0, 81, -16, 75, 81, -51, 104, 60,
        }, out.toByteArray());
    }

    private long getTime(String s) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeUtil.getTimeZoneGmt());
        return sdf.parse(s).getTime();
    }
}
