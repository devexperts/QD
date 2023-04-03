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
package com.devexperts.qd.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.management.impl.CollectorManagementImplOneContract;

import java.lang.management.ManagementFactory;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import static org.junit.Assert.fail;

class Tweaks {
    static void setTickerLockWaitLogInterval(DataScheme scheme, String interval) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(
                CollectorManagementImplOneContract.getBeanName(scheme, QDContract.TICKER, null));
            mbs.setAttribute(oname, new Attribute("LockWaitLogInterval", interval));
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    static void setTickerInterleave(DataScheme scheme, int interleave) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(
                CollectorManagementImplOneContract.getBeanName(scheme, QDContract.TICKER, null));
            mbs.setAttribute(oname, new Attribute("Interleave", interleave));
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    static void setTickerStress(DataScheme scheme) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(
                CollectorManagementImplOneContract.getBeanName(scheme, QDContract.TICKER, null));
            mbs.setAttribute(oname, new Attribute("SubscriptionBucket", 1));
            mbs.setAttribute(oname, new Attribute("DistributionBucket", 1));
            mbs.setAttribute(oname, new Attribute("Interleave", 4));
            mbs.setAttribute(oname, new Attribute("MaxDistributionSpins", 1));
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    static void setTickerDefaults(DataScheme scheme) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(
                CollectorManagementImplOneContract.getBeanName(scheme, QDContract.TICKER, null));
            mbs.invoke(oname, "resetToDefaults", new Object[0], new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }
}
