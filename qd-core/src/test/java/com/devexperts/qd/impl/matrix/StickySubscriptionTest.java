/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.stats.QDStats;
import org.junit.Assert;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class StickySubscriptionTest {

    private static final MethodHandle STOP_SCHEDULER = getMethodHandle("stopScheduler");

    private static MethodHandle getMethodHandle(String name, Class<?>... parameterTypes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method method = StickySubscription.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            MethodHandle methodHandle = lookup.unreflect(method);
            method.setAccessible(false);
            return methodHandle;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(String fieldName, Object obj, long value) {
        try {
            Field field = StickySubscription.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setLong(obj, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final DataRecord RECORD = new DefaultRecord(0, "Haba", false, null, null);
    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalStickyPeriodMin() {
        new StickySubscription(-1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalStickyPeriodMax() {
        new StickySubscription(TimeUnit.DAYS.toMillis(366), null);
    }

    @Test(expected = NullPointerException.class)
    public void testNPECollector() {
        new StickySubscription(0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetIllegalStickyPeriod() {
        StickySubscription stickySubscription = new StickySubscription(1, getTicker());
        stickySubscription.setStickyPeriodGLocked(-1);
    }

    @Test
    public void testInitDisabled() {
        StickySubscription stickySubscription = new StickySubscription(0, getTicker());
        Assert.assertTrue(stickySubscription.isEmpty());
        Assert.assertFalse(stickySubscription.isEnabled());
        Assert.assertEquals(0, stickySubscription.getStickyPeriod());
    }

    @Test
    public void testInitEnabled() {
        StickySubscription stickySubscription = new StickySubscription(1, getTicker());
        Assert.assertTrue(stickySubscription.isEmpty());
        Assert.assertTrue(stickySubscription.isEnabled());
        Assert.assertEquals(StickySubscription.MIN_STICKY_PERIOD, stickySubscription.getStickyPeriod());
    }

    @Test
    public void testChangeStickyPeriod() {
        StickySubscription stickySubscription = new StickySubscription(StickySubscription.MIN_STICKY_PERIOD, getTicker());
        Assert.assertTrue(stickySubscription.isEmpty());
        Assert.assertTrue(stickySubscription.isEnabled());
        Assert.assertEquals(StickySubscription.MIN_STICKY_PERIOD, stickySubscription.getStickyPeriod());

        long stickyPeriod = StickySubscription.MIN_STICKY_PERIOD * 2;
        stickySubscription.setStickyPeriodGLocked(stickyPeriod);
        Assert.assertEquals(stickyPeriod, stickySubscription.getStickyPeriod());
    }

    @Test
    public void testStickyBucketIndex() {
        Stream.of(1, 7, 11, 33, 57, 81, StickySubscription.STICKY_BUCKETS,
                StickySubscription.STICKY_BUCKETS + 17, StickySubscription.STICKY_BUCKETS * 3 + 42)
            .map(stamp -> stamp * StickySubscription.STICKY_STAMP_DURATION)
            .forEach(period -> {
                try {
                    long periodNanos = TimeUnit.MILLISECONDS.toNanos(period);
                    int stickyBuckets = Math.toIntExact(Math.max(Math.min(
                        StickySubscription.nanosToStamp(periodNanos), StickySubscription.STICKY_BUCKETS), 1));
                    long step = periodNanos / stickyBuckets;

                    StickySubscription stickySubscription = new StickySubscription(period, getTicker());

                    for (int stamp = 0; stamp < 10_000; stamp++) {
                        int index = stickySubscription.getStickyBucketIndex(stamp);

                        long stampNanos = StickySubscription.stampToNanos(stamp);

                        int expectedIndex = Math.toIntExact(
                            stampNanos / step % StickySubscription.RING_BUCKETS * StickySubscription.BUCKET_SIZE);
                        Assert.assertEquals(expectedIndex, index);
                    }
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
    }

    @Test
    public void testSimpleStickyCleanupTask() throws Throwable {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Ticker ticker = getTicker(countDownLatch::countDown);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(100, ticker);

        stickySubscription.updateCurrentStickyStamp();
        addStickySubscription(stickySubscription, ticker, 1);

        Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testDisableStickyPeriod() throws Throwable {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Ticker ticker = getTicker(countDownLatch::countDown);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(TimeUnit.DAYS.toMillis(365), ticker);

        stickySubscription.updateCurrentStickyStamp();
        addStickySubscription(stickySubscription, ticker, 1);
        addStickySubscription(stickySubscription, ticker, 2);

        // disable sticky subscription
        stickySubscription.setStickyPeriodGLocked(0);

        Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
        Assert.assertFalse(stickySubscription.isEnabled());
        Assert.assertTrue(stickySubscription.isEmpty());
    }

    @Test
    public void testCloseStickySubscription() {
        AtomicInteger count = new AtomicInteger(0);
        Ticker ticker = getTicker(count::incrementAndGet);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(TimeUnit.DAYS.toSeconds(365), ticker);

        stickySubscription.updateCurrentStickyStamp();
        addStickySubscription(stickySubscription, ticker, 1);
        addStickySubscription(stickySubscription, ticker, 2);

        // make cleanup immediate enable
        setField("stickyPeriod", stickySubscription, 1);
        stickySubscription.close();

        Assert.assertEquals(0, count.get());
        Assert.assertFalse(stickySubscription.isEnabled());
        Assert.assertFalse(stickySubscription.isEmpty());
    }

    @Test
    public void testDirectInvokeDoCleanupStickySubscription() throws Throwable {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Ticker ticker = getTicker(countDownLatch::countDown);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(TimeUnit.SECONDS.toMillis(10), ticker);

        stickySubscription.updateCurrentStickyStamp();
        addStickySubscription(stickySubscription, ticker, 1);
        addStickySubscription(stickySubscription, ticker, 2);

        // stop scheduler to make manual cleanup
        STOP_SCHEDULER.invoke(stickySubscription);

        // set sticky period to min value for direct invoke cleanup
        setField("stickyPeriod", stickySubscription, 0);
        stickySubscription.doCleanupStickySubscription();
        // return valid value
        setField("stickyPeriod", stickySubscription, 1);

        Assert.assertTrue(countDownLatch.await(3, TimeUnit.SECONDS));
        Assert.assertTrue(stickySubscription.isEnabled());
        Assert.assertTrue(stickySubscription.isEmpty());
    }

    @Test
    public void testInactivateStickySubscription() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        Ticker ticker = getTicker(count::incrementAndGet);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(TimeUnit.SECONDS.toMillis(1), ticker);

        stickySubscription.updateCurrentStickyStamp();
        addStickySubscription(stickySubscription, ticker, 1);
        // stop scheduler to make manual cleanup
        STOP_SCHEDULER.invoke(stickySubscription);

        dropStickySubscription(stickySubscription, ticker, 1);

        setField("stickyPeriod", stickySubscription, 0);
        stickySubscription.doCleanupStickySubscription();
        setField("stickyPeriod", stickySubscription, 1);

        Assert.assertEquals(0, count.get());
        Assert.assertTrue(stickySubscription.isEnabled());
        Assert.assertTrue(stickySubscription.isEmpty());
    }

    @Test
    public void testRebuildStickySubscription() {
        AtomicInteger count = new AtomicInteger(0);
        Ticker ticker = getTicker(count::incrementAndGet);
        ticker.startSubChangeBatch(0);
        StickySubscription stickySubscription = new StickySubscription(TimeUnit.MINUTES.toMillis(10), ticker);

        addStickySubscription(stickySubscription, ticker, 1);
        addStickySubscription(stickySubscription, ticker, 2);
        addStickySubscription(stickySubscription, ticker, 3);

        stickySubscription.setStickyPeriodGLocked(TimeUnit.SECONDS.toMillis(1));

        setField("stickyPeriod", stickySubscription, 0);

        stickySubscription.doCleanupStickySubscription();

        Assert.assertEquals(3, count.get());
        Assert.assertTrue(stickySubscription.isEmpty());
    }

    private void addStickySubscription(StickySubscription stickySubscription, Ticker ticker, int index) {
        stickySubscription.addStickySubscription(ticker.total.sub, index * Collector.TOTAL_AGENT_STEP);
    }

    private void dropStickySubscription(StickySubscription stickySubscription, Ticker ticker, int index) {
        stickySubscription.dropStickySubscription(ticker.total.sub, index * Collector.TOTAL_AGENT_STEP);
    }

    private Ticker getTicker() {
        return new TestTicker(null);
    }

    private Ticker getTicker(Runnable runnable) {
        return new TestTicker(runnable);
    }

    private static class TestTicker extends Ticker {

        private final Runnable runnable;

        TestTicker(Runnable runnable) {
            super(QDFactory.getDefaultFactory().tickerBuilder()
                .withScheme(SCHEME)
                .withStats(QDStats.VOID));
            this.runnable = runnable;
        }

        @Override
        public void removeStickySubscription(int tindex) {
            runnable.run();
        }
    }
}
