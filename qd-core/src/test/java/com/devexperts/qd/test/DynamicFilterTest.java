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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DynamicFilterTest {
    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    TestFilter one = new TestFilter(SCHEME, "one");
    TestFilter two = new TestFilter(SCHEME, "two");
    QDFilter curFilter;
    TestListener filter = new TestListener();

    @Test
    public void testDynamicCompositeFireAlways() {
        curFilter = CompositeFilters.makeAnd(one, two);
        // will fire on all updates
        curFilter.getUpdated().addUpdateListener(filter);
        assertCurValid();
        assertTracking();
        for (int i = 0; i < 100; i++) {
            one.updateIt();
            assertFired();
            assertTracking();
            two.updateIt();
            assertFired();
            assertTracking();
        }
        // now remove filter and make sure tracking stops
        curFilter.getUpdated().removeUpdateListener(filter);
        assertCurValid();
        assertNotTracking();
        // update and see it is not fired any more
        one.updateIt();
        assertNotFired();
    }

    @Test
    public void testDynamicCompositeFireOnce() {
        curFilter = CompositeFilters.makeAnd(one, two);
        // will fire once
        curFilter.addUpdateListener(filter);
        assertCurValid();
        assertTracking();
        one.updateIt();
        assertFired();
        assertNotTracking();
        two.updateIt();
        assertNotFired();
        assertNotTracking();
    }

    private void assertCurValid() {
        assertEquals("one&two", curFilter.toString());
    }

    private void update() {
        one = (TestFilter) one.getUpdatedFilter();
        two = (TestFilter) two.getUpdatedFilter();
    }

    private void assertTracking() {
        update();
        assertTrue(one.dynamicTracking);
        assertTrue(two.dynamicTracking);
    }

    private void assertNotTracking() {
        update();
        assertFalse(one.dynamicTracking);
        assertFalse(two.dynamicTracking);
    }

    private void assertFired() {
        assertNotNull(filter.fired);
        assertSame(filter.fired, curFilter);
        curFilter = curFilter.getUpdatedFilter();
        assertNotSame(curFilter, filter.fired);
        filter.fired = null;
        assertCurValid();
    }

    private void assertNotFired() {
        assertNull(filter.fired);
    }

    private static class TestFilter extends QDFilter {
        boolean dynamicTracking;

        TestFilter(DataScheme scheme, String name) {
            super(scheme);
            setName(name);
        }

        TestFilter(DataScheme scheme, QDFilter source) {
            super(scheme, source);
            setName(source.toString());
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return false;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }

        public void updateIt() {
            fireFilterUpdated(new TestFilter(getScheme(), this));
        }

        @Override
        protected void dynamicTrackingStart() {
            assertFalse(dynamicTracking);
            dynamicTracking = true;
        }

        @Override
        protected void dynamicTrackingStop() {
            assertTrue(dynamicTracking);
            dynamicTracking = false;
        }
    }

    private class TestListener implements QDFilter.UpdateListener {
        QDFilter fired;

        @Override
        public void filterUpdated(QDFilter filter) {
            assertNull(fired);
            assertSame(filter, curFilter);
            fired = filter;
        }
    }
}
