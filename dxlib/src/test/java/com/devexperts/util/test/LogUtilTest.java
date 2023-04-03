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
package com.devexperts.util.test;

import com.devexperts.util.LogUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LogUtilTest {

    @Test
    public void testHideCredentials() {
        doHide("host:7000");
        doHide("host:7000[user=]");
        doHide("host:7000[user=,password=]");
        doHide("host:7000[user=ABC]");
        doHide("host:7000[user=ABC,user=ABC][user=ABC](user=ABC,user=ABC)(user=ABC)");
        doHide("host:7000(user=ABC,password=ABC)[password=ABC](password=ABC,password=ABC)");
        doHide("http://host");
        doHide("hTTp://@host");
        doHide("TttP://ABC@");
        doHide("http://ABC@host");
        doHide("hTTp://ABC:ABC@host");
        doHide("https://ABC:ABC@host");
        doHide("httpS://ABC@ABC@host");
        doHide("HttpS://ABC@ABC:ABC@host");
        doHide("ftp://ABC:ABC@host");
        doHide("FTP://ABC:ABC@host");
        doHide("http://host?password=ABC");
        doHide("http://host?password=ABC&help&user=ABC");
        doHide("http://host?help&password=ABC");
        doHide("http://host?help,password=ABC;hello");
        doHide("address=((feed,chartdata)&ipf[https://tools.dxfeed.com/ipf?user=ABC,user=ABC,password=ABC,update=1h]@mux-retail:7800)");
        doHide("address=((feed,chartdata)&ipf[https://ABC:ABC@tools.dxfeed.com/ipf?user=ABC&user=ABC&password=ABC,update=1h]@mux-retail:7800)");
        doHide("address=((feed,chartdata)&ipf[https://ABC@ABC@tools.dxfeed.com/ipf?user=ABC&user=ABC&password=ABC,update=1h]@mux-retail:7800)");
        doHide("address=((feed,chartdata)&ipf[https://ABC@ABC:ABC@tools.dxfeed.com/ipf?user=ABC&user=ABC&password=ABC,update=1h]@mux-retail:7800)");
    }

    private static void doHide(String s) {
        String expect = s.replaceAll("ABC@ABC", "ABC").replaceAll("ABC:ABC", "ABC").replaceAll("ABC", "****");
        String actual = LogUtil.hideCredentials(s);
        assertEquals(expect, actual);
    }
}
