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
package com.devexperts.management.test;

import com.devexperts.management.Management;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ManagementTest {

    @Test
    public void testAnnotations() throws IntrospectionException, InstanceNotFoundException, ReflectionException {
        SampleBean bean = new SampleBean();
        Management.Registration registration = Management.registerMBean(bean, SampleMXBean.class, "test:name=TestBean");
        assertFalse(registration.hasExisted());
        assertFalse(registration.hasFailed());
        ObjectName objectName = registration.getInstance().getObjectName();
        MBeanInfo info = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(objectName);
        int seen = 0;
        for (MBeanOperationInfo op : info.getOperations()) {
            if (op.getName().equals("setPrevDayClose")) {
                assertEquals(op.getDescription(), "Sets new values for prev day close data", op.getDescription());
                MBeanParameterInfo[] sig = op.getSignature();
                assertEquals(3, sig.length);
                assertEquals("symbol", sig[0].getName());
                assertEquals("the symbol", sig[0].getDescription());
                assertEquals("date", sig[1].getName());
                assertEquals("the prevDayId in yyyyMMdd format", sig[1].getDescription());
                assertEquals("price", sig[2].getName());
                assertEquals("the prevDayClosePrice", sig[2].getDescription());
                seen++;
            }
            if (op.getName().equals("removeDeadSymbols")) {
                assertEquals("Removes all data for all symbols that were inactive for a specified time period",
                    op.getDescription());
                MBeanParameterInfo[] sig = op.getSignature();
                assertEquals(1, sig.length);
                assertEquals("ttlMillis", sig[0].getName());
                assertEquals("inactivity period in milliseconds", sig[0].getDescription());
                seen++;
            }
            if (op.getName().equals("removeSymbol")) {
                assertEquals("Removes all data for specified symbol", op.getDescription());
                MBeanParameterInfo[] sig = op.getSignature();
                assertEquals(1, sig.length);
                assertEquals("symbol", sig[0].getName());
                assertEquals("the symbol", sig[0].getDescription());
                seen++;
            }
            if (op.getName().equals("scan")) {
                assertEquals("Tests that string arrays are supported", op.getDescription());
                MBeanParameterInfo[] sig = op.getSignature();
                assertEquals(1, sig.length);
                assertEquals("symbols", sig[0].getName());
                assertEquals("the symbols", sig[0].getDescription());
                seen++;
            }
            if (op.getName().equals("avoid")) {
                assertEquals("Tests that primitive arrays are supported", op.getDescription());
                MBeanParameterInfo[] sig = op.getSignature();
                assertEquals(1, sig.length);
                assertEquals("indices", sig[0].getName());
                assertEquals("the indices", sig[0].getDescription());
                seen++;
            }
        }
        assertEquals(5, seen);
        registration.unregister();
    }
}
