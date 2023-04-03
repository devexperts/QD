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
package com.devexperts.rmi.test;

import com.devexperts.rmi.impl.ServiceFilter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ServiceFilterTest {

    public static final String[] SERVICE_NAMES =
        {"Service", "feed.service1", "feed.service2", "feed.news.service1", "feed.news.service2"};
    boolean[] actualResults;

    //----------------------------- test Filter -----------------------------

    @Test
    public void testEquals() {
        ServiceFilter filter1 = ServiceFilter.valueOf("Service,News,");
        ServiceFilter filter2 = ServiceFilter.valueOf("Service,News");
        assertEquals(filter2, filter1);
        filter2 = ServiceFilter.valueOf("Service,,News");
        assertEquals(filter2, filter1);
        filter2 = ServiceFilter.valueOf(",Service,News");
        assertEquals(filter2, filter1);
        filter1 = ServiceFilter.valueOf("Service,News,*");
        filter2 = ServiceFilter.valueOf("*,Service,News");
        assertEquals(filter2, ServiceFilter.ANYTHING);
        assertEquals(filter1, ServiceFilter.ANYTHING);
        filter2 = ServiceFilter.valueOf("Service,*,News");
        assertEquals(filter2, ServiceFilter.ANYTHING);

        filter1 = ServiceFilter.valueOf("Service*,Service");
        filter2 = ServiceFilter.valueOf("Service*,Service");
        assertEquals(filter1, filter2);
        filter2 = ServiceFilter.valueOf("Service*,Service*");
        assertFalse(filter1.equals(filter2));
        assertFalse(filter1.equals(filter2));
        filter2 = ServiceFilter.valueOf("service");
        assertFalse(filter1.equals(filter2));

        //TODO uncomment when added sorting in ServiceFilter.valueOf()
        //filter2 = ServiceFilter.valueOf("Service,Service*");
        //assertEquals(filter1, filter2);
    }

    @Test
    public void testAccept() {
        ServiceFilter filter = ServiceFilter.valueOf("Service");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[0] = true;
        accepted(filter);

        filter = ServiceFilter.valueOf("*");
        actualResults = new boolean[] {true, true, true, true, true};
        accepted(filter);

        filter = ServiceFilter.valueOf("*.service2");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[2] = actualResults[4] = true;
        accepted(filter);

        filter = ServiceFilter.valueOf("feed.*");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[1] = actualResults[2] = actualResults[3] = actualResults[4] = true;
        accepted(filter);

        filter = ServiceFilter.valueOf("feed*service1");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[1] = actualResults[3] = true;
        accepted(filter);

        filter = ServiceFilter.valueOf("Service,feed.*");
        actualResults = new boolean[] {true, true, true, true, true};
        accepted(filter);

        filter = ServiceFilter.valueOf("feed*service1,*.service2");
        actualResults = new boolean[] {false, true, true, true, true};
        accepted(filter);

        filter = ServiceFilter.valueOf("");
        actualResults = new boolean[SERVICE_NAMES.length];
        accepted(filter);

        filter = ServiceFilter.valueOf("Oops");
        actualResults = new boolean[SERVICE_NAMES.length];
        accepted(filter);

        filter = ServiceFilter.valueOf("Service,feed.service1");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[0] = actualResults[1] = true;
        accepted(filter);

        filter = ServiceFilter.valueOf("Service,*service2,feed.s*,");
        actualResults = new boolean[SERVICE_NAMES.length];
        actualResults[0] = actualResults[1] = actualResults[2] = actualResults[4] = true;
        accepted(filter);
    }

    @Test
    public void testIntersection() {
        ServiceFilter filter1 = ServiceFilter.valueOf("Service*,Serv*ice,");
        ServiceFilter filter2 = ServiceFilter.valueOf("*Service,Service,,Serv*ice,ServicePrint");
        ServiceFilter result = ServiceFilter.valueOf(
            "Service*Service,Service,Service,Service*ice,Service,ServicePrint,Serv*Service,Service,Service,Serv*ice");
        assertEquals(filter1.intersection(filter2), result);
        //TODO uncomment when added sorting in ServiceFilter.valueOf()
        //assertEquals(filter2.intersection(filter1), result);
        assertTrue(result.accept("ServService"));
        assertEquals(filter1.intersection(ServiceFilter.NOTHING), ServiceFilter.NOTHING);
        assertEquals(ServiceFilter.NOTHING.intersection(filter1), ServiceFilter.NOTHING);
        assertEquals(ServiceFilter.ANYTHING.intersection(filter1), filter1);
        assertEquals(filter1.intersection(ServiceFilter.ANYTHING), filter1);

    }

    void accepted(ServiceFilter filter) {
        for (int i = 0; i < SERVICE_NAMES.length; i++)
            assertEquals("i = " + i, filter.accept(SERVICE_NAMES[i]), actualResults[i]);

    }

    //----------------------------- test Atom Filter -----------------------------

    @Test
    public void testAtomEquals() {
        ServiceFilter filter1 = ServiceFilter.valueOf("");
        ServiceFilter filter2 = ServiceFilter.valueOf("Service");
        assertEquals(filter1, ServiceFilter.NOTHING);
        assertNotEquals(filter1, ServiceFilter.ANYTHING);
        assertNotEquals(filter1, filter2);
        filter2 = ServiceFilter.valueOf("service*1");
        assertNotEquals(filter1, filter2);

        filter1 = ServiceFilter.valueOf("*");
        assertEquals(filter1, ServiceFilter.ANYTHING);
        assertNotEquals(filter1, ServiceFilter.NOTHING);
        assertNotEquals(filter1, filter2);
        filter2 = ServiceFilter.valueOf("service");
        assertNotEquals(filter1, filter2);

        filter1 = ServiceFilter.valueOf("service");
        filter2 = ServiceFilter.valueOf("service");
        assertEquals(filter1, filter2);
        filter2 = ServiceFilter.valueOf("service*1");
        assertNotEquals(filter1, filter2);

        filter1 = ServiceFilter.valueOf("*service");
        filter2 = ServiceFilter.valueOf("*service");
        assertEquals(filter1, filter2);
        filter2 = ServiceFilter.valueOf("service*");
        assertNotEquals(filter1, filter2);
    }

    @Test
    public void testAtomAccept() {
        ServiceFilter filter = ServiceFilter.valueOf("");
        assertFalse(filter.accept("service"));

        filter = ServiceFilter.valueOf("*");
        assertTrue(filter.accept("service"));

        filter = ServiceFilter.valueOf("service");
        assertTrue(filter.accept("service"));

        filter = ServiceFilter.valueOf("service");
        assertFalse(filter.accept("service1"));

        filter = ServiceFilter.valueOf("ser*");
        assertTrue(filter.accept("service"));

        filter = ServiceFilter.valueOf("*ce");
        assertTrue(filter.accept("service"));

        filter = ServiceFilter.valueOf("ser*");
        assertFalse(filter.accept("1service"));

        filter = ServiceFilter.valueOf("*ce");
        assertFalse(filter.accept("service1"));

        filter = ServiceFilter.valueOf("Service|Service*Service");
        assertTrue(filter.accept("Service"));
        assertTrue(filter.accept("ServiceService"));
        assertTrue(filter.accept("ServiceSomethingService"));
        assertFalse(filter.accept("ServicePrintServ"));
    }

    @Test
    public void testAtomIntersection() {
        ServiceFilter filter1 = ServiceFilter.valueOf("Service*");
        ServiceFilter filter2 = ServiceFilter.valueOf("Service");

        //NOTHING TEST
        assertEquals(ServiceFilter.NOTHING.intersection(filter1), ServiceFilter.NOTHING);
        assertEquals(filter1.intersection(ServiceFilter.NOTHING), ServiceFilter.NOTHING);
        assertEquals(ServiceFilter.NOTHING.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(ServiceFilter.NOTHING), ServiceFilter.NOTHING);
        assertEquals(ServiceFilter.NOTHING.intersection(ServiceFilter.ANYTHING), ServiceFilter.NOTHING);
        assertEquals(ServiceFilter.ANYTHING.intersection(ServiceFilter.NOTHING), ServiceFilter.NOTHING);

        //ANYTHING TEST
        assertEquals(ServiceFilter.ANYTHING.intersection(filter1), filter1);
        assertEquals(filter1.intersection(ServiceFilter.ANYTHING), filter1);
        assertEquals(ServiceFilter.ANYTHING.intersection(filter2), filter2);
        assertEquals(filter2.intersection(ServiceFilter.ANYTHING), filter2);

        //SIMPLE VS SIMPLE TEST
        filter1 = ServiceFilter.valueOf("Service1");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter1.intersection(filter1), filter1);

        //SIMPLE vs PATTERN & PATTERN vs SIMPLE test
        filter1 = ServiceFilter.valueOf("Service*");
        assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("ServicePrint");
        assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("PrintServicePrint");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        filter2 = ServiceFilter.valueOf("ervicePrint");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);

        filter1 = ServiceFilter.valueOf("*Service");
        filter2 = ServiceFilter.valueOf("Service"); assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("PrintService"); assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("PrintServicePrint");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        filter2 = ServiceFilter.valueOf("PrintServ");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);

        filter1 = ServiceFilter.valueOf("Service*Print");
        filter2 = ServiceFilter.valueOf("ServicePrint");
        assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("ServiceFirstPrint"); assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("PrintServicePrint");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        filter2 = ServiceFilter.valueOf("PrintServ");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);

        //PATTERN VS PATTERN TEST
        // ...* vs ...*
        filter1 = ServiceFilter.valueOf("Service*");
        filter2 = ServiceFilter.valueOf("Print*");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        filter2 = ServiceFilter.valueOf("Serv*");
        assertEquals(filter1.intersection(filter2), filter1);
        assertEquals(filter2.intersection(filter1), filter1);
        filter2 = ServiceFilter.valueOf("ervice*");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        // *... vs ...*
        filter2 = ServiceFilter.valueOf("*Service");
        ServiceFilter result = ServiceFilter.valueOf("Service*Service,Service");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter2 = ServiceFilter.valueOf("*1Service");
        result = ServiceFilter.valueOf("Service*1Service");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter2 = ServiceFilter.valueOf("*Service1");
        result = ServiceFilter.valueOf("Service*Service1,Service1");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter1 = ServiceFilter.valueOf("1Service*");
        result = ServiceFilter.valueOf("1Service*Service1,1Service1");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter1 = ServiceFilter.valueOf("Service2*");
        result = ServiceFilter.valueOf("Service2*Service1");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        // *... vs ..*..
        filter1 = ServiceFilter.valueOf("*Service");
        filter2 = ServiceFilter.valueOf("Serv*ice");
        result = ServiceFilter.valueOf("Serv*Service,Service");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter2 = ServiceFilter.valueOf("1*Service");
        assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        filter2 = ServiceFilter.valueOf("Service*1");
        assertEquals(filter1.intersection(filter2), ServiceFilter.NOTHING);
        assertEquals(filter2.intersection(filter1), ServiceFilter.NOTHING);
        filter2 = ServiceFilter.valueOf("First*PrintService");
        assertEquals(filter1.intersection(filter2), filter2);
        assertEquals(filter2.intersection(filter1), filter2);
        // ..*.. vs ..*..
        filter1 = ServiceFilter.valueOf("Service*feed.news");
        filter2 = ServiceFilter.valueOf("Service.feed*news");
        result = ServiceFilter.valueOf("Service.feed*feed.news,Service.feed.news");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter2 = ServiceFilter.valueOf("Service.feed.news*news");
        result = ServiceFilter.valueOf("Service.feed.news*feed.news");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
        filter2 = ServiceFilter.valueOf("Servicefeed.*news");
        result = ServiceFilter.valueOf("Servicefeed.*feed.news,Servicefeed.news");
        assertEquals(filter1.intersection(filter2), result);
        assertEquals(filter2.intersection(filter1), result);
    }
}
