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

import com.devexperts.logging.Logging;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.DxTimer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * StickySubscription class is used to support periodic cleaning of expired subscriptions.
 * This class contains a ring buffer-based structure for time buckets. Each time bucket represents the time range
 * corresponded with sticky subscriptions expiration time. The bucket contains: unsubscribe time, head of a linked list,
 * tail of a linked list and number of sticky subscriptions in the list. Additionally, this class runs schedule
 * tasks to clean up sticky subscriptions.
 */
class StickySubscription {

    private final Logging log;

    // --------- Configuration constants ------
    static final int MIN_STICKY_PERIOD = 100; // min sticky period 100 ms
    static final int STICKY_STAMP_DURATION = 100; // 100 ms one sticky stamp unit
    static final int STICKY_BUCKETS = 100; // number of time buckets
    static final int RING_BUCKETS = 2 * STICKY_BUCKETS; // number of ring buckets
    static final int BUCKET_SIZE = 4; // the number of bucket elements

    //  ------- Layout of sticky subscription bucket -------
    private static final int STICKY_STAMP = 0; // sticky subscription stamp for the bucket
    private static final int STICKY_COUNT = 1; // the number of items in the bucket
    private static final int HEAD_TINDEX = 2;  // head total index of the bucket list
    private static final int TAIL_TINDEX = 3;  // tail total index of the bucket list

    private static final long NANOS_IN_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long STAMP_DURATION_NANOS = STICKY_STAMP_DURATION * NANOS_IN_MILLISECOND;

    static long stampToNanos(long stamp) {
        return stamp * STAMP_DURATION_NANOS;
    }

    static int nanosToStamp(long nanos) {
        return Math.toIntExact(nanos / STAMP_DURATION_NANOS);
    }

    private static class StickyBucketCleanupStat {
        private int iterations;
        private int revived;
        private int relocated;
        private int released;
        private long processingNanos;
        private long maxProcessingNanos;

        public void append(int revived, int relocated, int released, long processingNanos) {
            iterations++;
            this.revived += revived;
            this.relocated += relocated;
            this.released += released;
            this.processingNanos += processingNanos;
            if (this.maxProcessingNanos < processingNanos) {
                this.maxProcessingNanos = processingNanos;
            }
        }

        public void clear() {
            iterations = 0;
            revived = 0;
            relocated = 0;
            released = 0;
            processingNanos = 0;
            maxProcessingNanos = 0;
        }

        @Override
        public String toString() {
            return "iterations=" + iterations + ", revived=" + revived +
                ", relocated=" + relocated + ", released=" + released +
                ", overall time=" + Duration.ofNanos(processingNanos) +
                ", max iteration time=" + Duration.ofNanos(maxProcessingNanos);
        }
    }

    // sticky subscription was closed
    private volatile boolean closed;

    private final DxTimer stickyTimer;
    private DxTimer.Cancellable cancellable;

    private long lastFlushStatNanos;
    private final StickyBucketCleanupStat stat;
    private final Collector collector;

    private final QDStats stats;

    // nano time it is used as a starting point for time measure
    private final long baseNanosStamp;

    // the calculated sticky stamp that will be used when adding a sticky subscription
    private int currentStickyStamp;

    // internal state of sticky subscription
    private long stickyPeriod; // sticky period in nanos
    private long stickyPeriodStep; // step between time buckets in nanos
    private final int[] bucketRing; // sticky item queue in the form of a ring buffer

    // it is used to prevent concurrent scheduled tasks of cleaning iterations
    private int taskGenerationId;

    // internal state of current sticky cleanup iteration
    private int cleanupBucketIndex; // current read index in sticky item queue
    private int cleanupBucketStamp;
    private int cleanupBucketCount;

    StickySubscription(long period, Collector collector) {
        this.log = Logging.getLogging(StickySubscription.class.getName() + "-" + collector);

        this.stickyPeriod = computeStickyPeriod(period);
        Objects.requireNonNull(collector, "collector");

        // currentStickyStamp starts from shift 10 sec to prevent 0 sticky stamps
        this.baseNanosStamp = System.nanoTime() - TimeUnit.SECONDS.toNanos(10);

        this.closed = false;
        this.collector = collector;
        this.stickyTimer = DxTimer.getInstance();
        this.stat = new StickyBucketCleanupStat();
        this.bucketRing = new int[RING_BUCKETS * BUCKET_SIZE];
        this.stats = collector.stats.create(QDStats.SType.STICKY_SUB);

        resetCleanupBucket();

        if (isEnabled()) {
            initStickyPeriodStep(stickyPeriod);
            updateCurrentStickyStamp();
        }
    }

    private void initStickyPeriodStep(long stickyPeriod) {
        long desiredBuckets = nanosToStamp(stickyPeriod);
        long boundedBuckets = Math.max(Math.min(desiredBuckets, STICKY_BUCKETS), 1);
        this.stickyPeriodStep = stickyPeriod / boundedBuckets;
    }

    public boolean isEnabled() {
        return stickyPeriod > 0 && !closed;
    }

    public boolean isEmpty() {
        return cleanupBucketIndex < 0;
    }

    /**
     * The method for check and clean up sticky subscriptions based on the internal state of the class.
     * @return true if the current bucket have indexes for processing otherwise false.
     */
    // SYNC: global
    private boolean cleanupStickySubscription() {
        if (isEmpty()) {
            return false;
        }

        // first iteration for new bucket
        boolean startNewBucket = cleanupBucketCount < 0;

        // check if the appropriate time is coming
        if (isEnabled() && startNewBucket && getRemainBucketTime(bucketRing[cleanupBucketIndex + STICKY_STAMP]) > 0) {
            return false;
        }

        // prepare for new bucket processing
        if (startNewBucket) {
            cleanupBucketStamp = bucketRing[cleanupBucketIndex + STICKY_STAMP];
            cleanupBucketCount = bucketRing[cleanupBucketIndex + STICKY_COUNT];
            // drop sticky stamp to allow external modification of bucket stamp
            bucketRing[cleanupBucketIndex + STICKY_STAMP] = Integer.MAX_VALUE;
        }

        int expirationStamp;
        int chunkSize;
        if (isEnabled()) {
            // normal cleanup
            expirationStamp = nanosToStamp(System.nanoTime() - baseNanosStamp - stickyPeriod);
            chunkSize = collector.subStepsRemaining > 0 ?
                Math.min(cleanupBucketCount, collector.subStepsRemaining) : cleanupBucketCount;
        } else {
            // use to fast cleanup all
            expirationStamp = Integer.MAX_VALUE;
            chunkSize = Integer.MAX_VALUE;
        }

        int processedCount = cleanupStickyBucket(expirationStamp, cleanupBucketIndex, chunkSize);

        cleanupBucketCount -= processedCount;
        collector.subStepsRemaining -= processedCount;

        // check if we need to keep clearing the current bucket,
        // and in the last iteration we had progress (to prevent an infinite loop)
        if (cleanupBucketCount > 0 && processedCount > 0) {
            return true;
        }

        // find the new appropriate bucket for next iteration
        advanceCleanupBucketIndex();

        // print cleanup stat to log
        printCleanupStat();

        return false;
    }

    private void printCleanupStat() {
        long currentNanos = System.nanoTime();
        if (currentNanos - lastFlushStatNanos > collector.getManagement().getStickyLogIntervalNanos()) {
            lastFlushStatNanos = currentNanos;
            if (log.debugEnabled()) {
                log.debug("Cleanup sticky bucket stat: " + stat);
            }
            stat.clear();
        }
    }

    // SYNC: global
    public void doCleanupStickySubscription() {
        updateCurrentStickyStamp();
        cleanupStickySubscription();
    }

    // SYNC: global
    public boolean isStickySubscription(SubMatrix tsub, int tindex) {
        return tsub.getInt(tindex + Collector.STICKY_STAMP) > 0;
    }

    // SYNC: global
    public void addStickySubscription(SubMatrix tsub, int tindex) {
        int stickyStamp = tsub.getInt(tindex + Collector.STICKY_STAMP);
        tsub.setInt(tindex + Collector.STICKY_STAMP, currentStickyStamp);
        if (stickyStamp == 0) {
            // not linked yet with tindex sticky subscription
            insertStickySubscription(tsub, tindex, currentStickyStamp);
            // increment sticky subscription
            stats.updateAdded(tsub.getInt(tindex + Collector.RID));
        } else if (stickyStamp == -1) {
            // dropped sticky subscription but still in sticky buckets
            // increment sticky subscription
            stats.updateAdded(tsub.getInt(tindex + Collector.RID));
        }
    }

    // SYNC: global
    public void dropStickySubscription(SubMatrix tsub, int tindex) {
        if (isStickySubscription(tsub, tindex)) {
            // decrement sticky subscription
            stats.updateRemoved(tsub.getInt(tindex + Collector.RID));
            // mark to define removed sticky subscription
            tsub.setInt(tindex + Collector.STICKY_STAMP, -1);
        }
    }

    // SYNC: global
    public void rebuildStickySubscription() {
        // cleanup bucket ring before rebuild
        Arrays.fill(bucketRing, 0);

        SubMatrix tsub = collector.total.sub;
        int size = tsub.matrix.length;
        for (int tindex = tsub.step; tindex < size; tindex += tsub.step) {
            tsub.setInt(tindex + Collector.STICKY_NEXT, 0);
            int stickyStamp = tsub.getInt(tindex + Collector.STICKY_STAMP);
            if (stickyStamp > 0) {
                insertStickySubscription(tsub, tindex, stickyStamp);
            } else if (stickyStamp < 0) {
                tsub.setInt(tindex + Collector.STICKY_STAMP, 0);
            }
        }
        initCleanupBucket();
    }

    public long getStickyPeriod() {
        return TimeUnit.NANOSECONDS.toMillis(stickyPeriod);
    }

    // SYNC: global
    public void setStickyPeriodGLocked(long period) {
        long stickyPeriod = computeStickyPeriod(period);
        if (this.stickyPeriod == stickyPeriod) {
            return;
        }
        log.info("Set sticky period=" + Duration.ofNanos(stickyPeriod));

        this.stickyPeriod = stickyPeriod;

        updateCurrentStickyStamp();

        if (isEnabled()) {
            initStickyPeriodStep(stickyPeriod);
            // rebuild sticky subscriptions and relaunch a new cleanup task
            rebuildStickySubscription();
            if (!isEmpty()) {
                startScheduler();
            }
        } else {
            // stop a cleanup task and force clenup sticky subscription
            stopScheduler();
            while (!isEmpty()) {
                cleanupStickySubscription();
            }
            resetCleanupBucket();
        }
    }

    public void close() {
        closed = true;
        stopScheduler();
    }

    /**
     * This method is used to check the expiration time of sticky subscription.
     * This method checks the list of indexes corresponding to a specific time bucket.
     * Possible options for processing sticky subscriptions:
     * unsubscribe in case of time expiration, save in the same bucket or transfer to another
     * suitable one, or delete as irrelevant if there was a repeated re-subscription.
     */
    private int cleanupStickyBucket(int expirationStamp, int bucketIndex, int chunkSize) {
        long startTimeNanos = System.nanoTime();

        int revived = 0;
        int relocated = 0;
        int released = 0;

        try {
            int count = 0;
            SubMatrix tsub = collector.total.sub;
            int tindex = bucketRing[bucketIndex + HEAD_TINDEX];
            for (int nextIndex; tindex > 0 && chunkSize-- > 0; tindex = nextIndex) {
                int stickyStamp = tsub.getInt(tindex + Collector.STICKY_STAMP);
                // get the next index
                nextIndex = tsub.getInt(tindex + Collector.STICKY_NEXT);
                // cleanup sticky
                tsub.setInt(tindex + Collector.STICKY_NEXT, 0);

                // remove element from bucket
                bucketRing[bucketIndex + HEAD_TINDEX] = nextIndex;
                count = --bucketRing[bucketIndex + STICKY_COUNT];
                if (count == 0) {
                    // clean bucket
                    bucketRing[bucketIndex + STICKY_STAMP] = 0;
                    bucketRing[bucketIndex + HEAD_TINDEX] = 0;
                    bucketRing[bucketIndex + TAIL_TINDEX] = 0;
                }

                if (stickyStamp == -1) {
                    // ignore empty (subscribed again) sticky subscription
                    tsub.setInt(tindex + Collector.STICKY_STAMP, 0);
                    revived++;
                } else if (stickyStamp > expirationStamp) {
                    // put into the correct subscription bucket
                    insertStickySubscription(tsub, tindex, stickyStamp);
                    relocated++;
                } else {
                    // clear the sticky stamp before deleting the sticky subscription to enable the ability to create
                    // a new sticky subscription (relevant only for the history contract).
                    tsub.setInt(tindex + Collector.STICKY_STAMP, 0);
                    // decrement sticky subscription counter
                    stats.updateRemoved(tsub.getInt(tindex + Collector.RID));
                    // remove sticky subscription
                    collector.removeStickySubscription(tindex);
                    released++;
                }
            }
            // check consistency of bucket
            if (chunkSize > 0 && (tindex <= 0 || count <= 0)) {
                log.error("Inconsistency  detected for bucket: " + bucketIndex + ", chunk size: " + chunkSize +
                    ", bucket stamp: " + bucketRing[bucketIndex + STICKY_STAMP] +
                    ", bucket count: " + bucketRing[bucketIndex + STICKY_COUNT] +
                    ", bucket head: " + bucketRing[bucketIndex + HEAD_TINDEX] +
                    ", bucket tail: " + bucketRing[bucketIndex + TAIL_TINDEX]);
            }
        } catch (Throwable t) {
            log.error("Error in remove subscription process", t);
        }
        // update stat
        stat.append(revived, relocated, released, System.nanoTime() - startTimeNanos);

        return revived + relocated + released;
    }

    private void insertStickySubscription(SubMatrix tsub, int tindex, int stickyStamp) {
        int bucketIndex = getStickyBucketIndex(stickyStamp);

        int currentBucketStamp = bucketRing[bucketIndex + STICKY_STAMP];
        if (currentBucketStamp == 0) {
            // create new sticky record
            bucketRing[bucketIndex + STICKY_STAMP] = stickyStamp;
            bucketRing[bucketIndex + HEAD_TINDEX] = tindex;
            bucketRing[bucketIndex + TAIL_TINDEX] = tindex;
            bucketRing[bucketIndex + STICKY_COUNT] = 1;
        } else {
            // update tail index
            if (currentBucketStamp > stickyStamp) {
                bucketRing[bucketIndex + STICKY_STAMP] = stickyStamp;
            }

            int tailIndex = bucketRing[bucketIndex + TAIL_TINDEX];
            // update the tail index if it is not equal to itself
            if (tailIndex != tindex) {
                bucketRing[bucketIndex + STICKY_COUNT]++;
                bucketRing[bucketIndex + TAIL_TINDEX] = tindex;
                tsub.setInt(tailIndex + Collector.STICKY_NEXT, tindex);
            }
        }

        // start scheduler for first sticky subscription
        if (isEmpty()) {
            cleanupBucketIndex = bucketIndex;
            startScheduler();
        }
    }

    void updateCurrentStickyStamp() {
        currentStickyStamp = nanosToStamp(System.nanoTime() - baseNanosStamp);
    }

    int getStickyBucketIndex(int stickyStamp) {
        return Math.toIntExact(stampToNanos(stickyStamp) / stickyPeriodStep % RING_BUCKETS * BUCKET_SIZE);
    }

    private long getDelayMillis(int stickyStamp) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(getRemainBucketTime(stickyStamp) + NANOS_IN_MILLISECOND, 0));
    }

    private long getRemainBucketTime(int stickyStamp) {
        // max stamp in bucket for sticky stamp
        long bucketStampNanos = stampToNanos(stickyStamp) / stickyPeriodStep * stickyPeriodStep + stickyPeriodStep;
        return baseNanosStamp + bucketStampNanos + stickyPeriod - System.nanoTime();
    }

    private void resetCleanupBucket() {
        cleanupBucketIndex = -1;
        cleanupBucketCount = -1;
        cleanupBucketStamp = -1;
    }

    private void initCleanupBucket() {
        resetCleanupBucket();
        int minStickyStamp = Integer.MAX_VALUE;
        for (int i = 0; i < bucketRing.length; i += BUCKET_SIZE) {
            int stickyStamp = bucketRing[i + STICKY_STAMP];
            if (stickyStamp > 0 && stickyStamp < minStickyStamp) {
                cleanupBucketIndex = i;
                minStickyStamp = stickyStamp;
            }
        }
    }

    private int nextBucketIndex(int cleanupBucketIndex) {
        return (cleanupBucketIndex + BUCKET_SIZE) % bucketRing.length;
    }

    private void advanceCleanupBucketIndex() {
        assert cleanupBucketIndex >= 0;

        int index = cleanupBucketIndex;
        while ((index = nextBucketIndex(index)) != cleanupBucketIndex && bucketRing[index + STICKY_STAMP] <= 0) {}

        int stamp = bucketRing[index + STICKY_STAMP];
        if (index == cleanupBucketIndex) {
            // all other buckets are empty - check the current bucket again
            if (stamp <= 0) {
                cleanupBucketIndex = -1;
            }
        } else if (stampToNanos(stamp - cleanupBucketStamp) > stickyPeriodStep * RING_BUCKETS) {
            // long stamp gap is detected - search bucket with min stamp
            int oldIndex = cleanupBucketIndex;
            int oldStamp = cleanupBucketStamp;

            initCleanupBucket();

            int newIndex = cleanupBucketIndex;
            int newStamp = isEmpty() ? 0 : bucketRing[cleanupBucketIndex + STICKY_STAMP];

            if (log.debugEnabled()) {
                log.debug("Sticky stamp overflow detected for index/stamp: old=" +
                    oldIndex + "/" + oldStamp + ", triggered=" + index + "/" + stamp +
                    ", redefined=" + newIndex + "/" + newStamp);
            }
        } else {
            // next appropriate bucket found
            cleanupBucketIndex = index;
        }
        cleanupBucketCount = -1;
    }

    // SYNC: global
    private Runnable createScheduleTask(int taskId) {
        return () -> executeScheduledTask(taskId);
    }

    // SYNC: none
    private void executeScheduledTask(int taskId) {
        int notify = 0;
        collector.globalLock.lock(CollectorOperation.REMOVE_STICKY_SUBSCRIPTION);
        try {
            collector.startSubChangeBatch(notify);
            executeScheduledTaskGLocked(taskId);
            notify = collector.doneSubChangeBatch();
        } finally {
            collector.globalLock.unlock();
        }
        collector.notifySubChange(notify, null);
    }

    // SYNC: global
    private void executeScheduledTaskGLocked(int taskId) {
        if (taskGenerationId != taskId) {
            // outdated task, skip it
            return;
        }
        if (cleanupStickySubscription()) {
            // continue process current bucket
            long delay = collector.getManagement().getStickyScheduleMinDelay();
            cancellable = stickyTimer.runOnce(createScheduleTask(taskId), delay);
        } else {
            // rehash total matrix
            collector.totalChangeComplete();

            if (!isEmpty()) {
                // there is something to process in the future
                startScheduler();
            }
        }
    }

    private void startScheduler() {
        stopScheduler();
        if (isEnabled()) {
            int stickyStamp = bucketRing[cleanupBucketIndex + STICKY_STAMP];
            long startDelayMillis = getDelayMillis(stickyStamp);

            if (collector.getManagement().getStickyLogIntervalNanos() == 0) {
                // enable logging only for the disabled min interval
                if (log.debugEnabled()) {
                    log.debug("Start scheduler delay=" + Duration.ofMillis(startDelayMillis) +
                        ", stickyStamp=" + stickyStamp + ", cleanupBucketIndex=" + cleanupBucketIndex);
                }
            }

            cleanupBucketCount = -1;
            cancellable = stickyTimer.runOnce(createScheduleTask(++taskGenerationId), startDelayMillis);
        }
    }

    private void stopScheduler() {
        if (cancellable != null) {
            cancellable.cancel();
            cancellable = null;
        }
    }

    private long computeStickyPeriod(long period) {
        if (period < 0 || period > TimeUnit.DAYS.toMillis(365)) {
            throw new IllegalArgumentException("illegal stickyPeriod=" + stickyPeriod);
        }
        if (period != 0 && period < MIN_STICKY_PERIOD) {
            log.warn("Too small sticky subscription check period=" + Duration.ofMillis(period) +
                ", default check period=" + Duration.ofMillis(MIN_STICKY_PERIOD));

            period = MIN_STICKY_PERIOD;
        }
        return TimeUnit.MILLISECONDS.toNanos(period);
    }
}
