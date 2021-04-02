/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ondemand.impl;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.StreamInput;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.LockSupport;

class Cache implements Runnable {
    private static final String FILE_CACHE_NAME = "mdrcache";
    private static final String FILE_CACHE_NAME_SUFFIX = ".tmp";
    private static final long FILE_HEADER = 0x4D44524361636865L; // "MDRCache"

    private static final long MIN_CACHE_INTERVAL = 60 * TimeUtil.SECOND;
    private static final double CACHE_LIMIT_FACTOR = 0.8;

    private static final AtomicLongFieldUpdater<Cache> USAGE_UPDATER = AtomicLongFieldUpdater.newUpdater(Cache.class, "usage");

    // ------------------------ static collection of instances ------------------------

    private static final IndexedSet<CacheConfig, Cache> INSTANCES = SynchronizedIndexedSet.create(Cache::getConfig);

    public static Cache acquireInstance(CacheConfig config) {
        config = config.clone();
        Cache cache;
        synchronized (INSTANCES) {
            cache = INSTANCES.getByKey(config);
            if (cache == null)
                INSTANCES.add(cache = new Cache(config));
        }
        if (cache.acquire())
            cache.startFileCacheWriter();
        return cache;
    }

    // ------------------------ instance ------------------------

    private final CacheConfig config;

    private int acquireCounter;
    private Thread cacheWriter;
    private volatile long version; // incs on each addData
    private volatile long usage; // incs on any operation

    private long writtenUsage;
    private long cacheSize;

    private final IndexedSet<Segment, Segment> segments = new IndexedSet<Segment, Segment>();

    Cache(CacheConfig config) {
        this.config = config;
    }

    public CacheConfig getConfig() {
        return config;
    }

    private synchronized boolean acquire() {
        return acquireCounter++ == 0;
    }

    public Thread release() {
        Thread cacheWriter;
        synchronized (this) {
            if (--acquireCounter > 0)
                return null;
            cacheWriter = this.cacheWriter;
            triggerFileCacheWriting();
            this.cacheWriter = null;
        }
        INSTANCES.remove(this);
        return cacheWriter;
    }

    private void startFileCacheWriter() {
        try {
            readCache();
        } catch (Throwable t) {
            Log.log.error("Unexpected error", t);
        }
        try {
            cacheWriter = new Thread(this, "MarketDataReplay-CacheWriter");
            cacheWriter.setDaemon(true);
            cacheWriter.start();
        } catch (Throwable ignored) {
        }
    }

    private synchronized void triggerFileCacheWriting() {
        if (cacheWriter != null)
            LockSupport.unpark(cacheWriter);
    }

    public long nextUsage() {
        long cur;
        long upd;
        do {
            cur = usage;
            upd = cur + 1;
        } while (!USAGE_UPDATER.compareAndSet(this, cur, upd));
        return upd;
    }

    public synchronized void checkRequestKeys(Current current, long requestTime, IndexedSet<Key, Key> presentKeys, IndexedSet<Key, Key> expiredKeys) {
        long millis = System.currentTimeMillis();
        for (Segment segment : segments)
            if (current.subscription.containsKey(segment.block) && segment.block.containsTime(requestTime)) {
                if (Math.abs(segment.downloadTime - millis) < config.timeToLive)
                    presentKeys.put(segment.block);
                else
                    expiredKeys.put(segment.block);
            }
    }

    public long getVersion() {
        return version;
    }

    public synchronized void addData(Collection<Segment> newSegments) {
        if (newSegments.isEmpty())
            return;
        long newSize = 0;
        HashMap<Key, ArrayList<Segment>> map = new HashMap<Key, ArrayList<Segment>>();
        long usage = nextUsage();
        for (Segment segment : newSegments) {
            newSize += segment.size();
            segment.usage = usage;
            ArrayList<Segment> segs = map.get(segment.block);
            if (segs == null)
                map.put(segment.block, segs = new ArrayList<Segment>());
            segs.add(segment);
        }
        int multiples = filterReceivedIntersections(map);
        long replacedSize = 0;
        int replacedSegments = 0;
        long identicalSize = 0;
        int identicalSegments = 0;
        RecordBuffer buffer = new RecordBuffer();
        for (Iterator<Segment> it = segments.iterator(); it.hasNext();) {
            Segment segment = it.next();
            ArrayList<Segment> segs = map.get(segment.block);
            if (segs == null)
                continue;
            boolean replace = false;
            for (Segment seg : segs)
                if (segment.intersects(seg)) {
                    if (segment.block.isIdentical(seg.block)) {
                        identicalSize += segment.size();
                        identicalSegments++;
                        if (Math.abs(segment.downloadTime - seg.downloadTime) < config.timeToLive)
                            Log.log.warn("Identical segment " + seg + " at " + TimeFormat.DEFAULT.withMillis().format(seg.downloadTime) +
                                " replaces " + segment + " at " + TimeFormat.DEFAULT.withMillis().format(segment.downloadTime));
                    } else
                        Log.log.warn("Segment intersection: " + seg + " replaces " + segment);
                    replace = true;
                }
            if (replace) {
                replacedSize += segment.size();
                replacedSegments++;
                it.remove();
                cacheSize -= segment.size();
            }
        }
        for (ArrayList<Segment> segs : map.values())
            for (Segment seg : segs) {
                segments.put(seg);
                cacheSize += seg.size();
            }
        Log.log.info("addData: " + Log.mb(newSize) + " in " + newSegments.size() + " segments (" + multiples + " multiples)" +
            ", replaced " + Log.mb(replacedSize) + " in " + replacedSegments + " segments" +
            ", identical " + Log.mb(identicalSize) + " in " + identicalSegments + " segments");

        version++; // will force rebuild of current segments by reading MDRs
        triggerFileCacheWriting();
    }

    private int filterReceivedIntersections(HashMap<Key, ArrayList<Segment>> map) {
        int multiples = 0;
        for (ArrayList<Segment> segs : map.values())
            if (segs.size() > 1) {
                multiples++;
                Collections.sort(segs, new Comparator<Segment>() {
                    public int compare(Segment segment1, Segment segment2) {
                        // Place most valuable segments first to delete least valuable.
                        Block b1 = segment1.block;
                        Block b2 = segment2.block;
                        if (b1.getEndTime() != b2.getEndTime())
                            return b1.getEndTime() > b2.getEndTime() ? -1 : 1;
                        long d1 = b1.getEndTime() - b1.getStartTime();
                        long d2 = b2.getEndTime() - b2.getStartTime();
                        return d1 > d2 ? -1 : d1 < d2 ? 1 : Block.COMPARATOR.compare(b1, b2);
                    }
                });
                for (int i = 0; i < segs.size(); i++)
                    for (int j = segs.size(); --j > i;) // Traverse backward to simplify deletion code.
                        if (segs.get(i).intersects(segs.get(j))) {
                            Log.log.warn("Received intersection: " + segs.get(i) + " replaces " + segs.get(j));
                            segs.remove(j);
                        }
            }
        return multiples;
    }

    public void rebuildCurrentSegmentsIfNeeded(Current current) {
        if (version > current.version)
            rebuildCurrentSegments(current);
    }

    public synchronized void rebuildCurrentSegments(Current current) {
        long usage = nextUsage();
        current.resetInterval();
        // add/replace new current segments
        for (Segment segment : segments) {
            if (isCurrentSegment(segment, current, current.time)) {
                segment.usage = usage;
                current.size += segment.size();
                current.startTime = Math.max(current.startTime, segment.block.getStartTime());
                current.endTime = Math.min(current.endTime, segment.block.getEndTime());
                CurrentSegment old = current.segments.getByKey(segment.block);
                if (old != null) {
                    // replace existing current segment
                    current.size -= old.segment.size();
                    old.replaceSegment(segment, current.time, usage);
                } else {
                    // add fresh segment
                    current.segments.add(new CurrentSegment(segment));
                }
            }
        }
        // release segments that are no longer current
        for (Iterator<CurrentSegment> it = current.segments.iterator(); it.hasNext();) {
            CurrentSegment cur = it.next();
            if (!isCurrentSegment(cur.segment, current, current.time)) {
                current.size -= cur.segment.size();
                cur.release();
                it.remove();
            }
        }
        // finish
        if (current.segments.isEmpty()) {
            current.startTime = current.time / MIN_CACHE_INTERVAL * MIN_CACHE_INTERVAL;
            current.endTime = current.startTime + MIN_CACHE_INTERVAL;
        }
        // only log something interesting
        if (current.size > 0 || !current.segments.isEmpty())
            Log.log.info("rebuildCurrent at " + TimeFormat.DEFAULT.withMillis().format(current.time) + ": " +
                Log.mb(current.size) + " in " + current.segments.size() + " segments" +
                " from " + TimeFormat.DEFAULT.withMillis().format(current.startTime) + " to " + TimeFormat.DEFAULT.withMillis().format(current.endTime) +
                ", replay speed " + current.replaySpeed);
        // update version and cleanup cache if needed
        current.version = version;
        checkCacheLimit(current);
    }


    public synchronized void releaseSegments(Current current) {
        for (CurrentSegment currentSegment : current.segments)
            currentSegment.release();
        current.segments.clear();
        current.size = 0;
        current.resetInterval();
    }

    public synchronized double getAvailableData(Current current, long time) {
        if (current.subscription.isEmpty())
            return 1;
        rebuildCurrentSegmentsIfNeeded(current);
        int available;
        if (current.isCurrentInterval(time)) {
            available = current.segments.size();
        } else {
            available = 0;
            for (Segment segment : segments)
                if (isCurrentSegment(segment, current, time))
                    available++;
        }
        return (double) available / current.subscription.size();
    }

    private boolean isCurrentSegment(Segment segment, Current current, long time) {
        return segment.block.containsTime(time) && current.subscription.containsKey(segment.block);
    }

    // SYNC(this)
    private void checkCacheLimit(Current current) {
        long oldCacheSize = cacheSize;
        int oldSegments = segments.size();
        if (cacheSize > config.cacheLimit && current.size < cacheSize * CACHE_LIMIT_FACTOR) {
            Segment[] sorted = segments.toArray(new Segment[segments.size()]);
            Arrays.sort(sorted, Segment.USAGE_COMPARATOR);
            for (Segment segment : sorted)
                if (segment.currentCounter == 0) {
                    segments.remove(segment);
                    cacheSize -= segment.size();
                    if (cacheSize <= config.cacheLimit * CACHE_LIMIT_FACTOR)
                        break;
                }
        }
        // only log something interesting
        if (cacheSize > 0 || current.size > 0 || oldCacheSize > 0 || !segments.isEmpty() || !current.segments.isEmpty() || oldSegments > 0)
            Log.log.info("Cache: limit " + Log.mb(config.cacheLimit) +
                ", used " + Log.mb(cacheSize) + " in " + segments.size() + " segments" +
                ", current " + Log.mb(current.size) + " in " + current.segments.size() + " segments" +
                ", removed " + Log.mb(oldCacheSize - cacheSize) + " in " + (oldSegments - segments.size()) + " segments");
    }

    private synchronized boolean needsWriteCache() {
        return writtenUsage != usage;
    }

    private synchronized void updateWrittenUsage(long writeUsage) {
        writtenUsage = writeUsage;
    }

    // returns written usage
    private long writeCache() {
        long millis = System.currentTimeMillis();
        long writeUsage;
        Segment[] sorted;
        synchronized (this) {
            writeUsage = usage;
            sorted = segments.toArray(new Segment[segments.size()]);
        }
        Arrays.sort(sorted, Segment.USAGE_COMPARATOR);
        FileOutputStream fos = null;
        try {
            File tmp = File.createTempFile(FILE_CACHE_NAME, FILE_CACHE_NAME_SUFFIX, getCacheFileParent());
            if (tmp.getParentFile() != null)
                tmp.getParentFile().mkdirs();
            fos = new FileOutputStream(tmp);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1000000);
            ByteArrayOutput bao = new ByteArrayOutput(100000);
            long writeSize = 0;
            long size = 0;
            int count = 0;
            bao.writeLong(FILE_HEADER);
            bao.writeCompactLong(writeUsage);
            bao.writeCompactLong(millis);
            for (int i = sorted.length; --i >= 0;) { // Write most recent first
                Segment segment = sorted[i];
                size += segment.size();
                count++;
                segment.block.writeBlock(bao);
                bao.writeCompactLong(segment.downloadTime);
                bao.writeCompactLong(segment.usage);
                bos.write(bao.getBuffer(), 0, bao.getPosition());
                writeSize += bao.getPosition();
                bao.setPosition(0);
                if (writeSize > config.fileCacheLimit)
                    break;
            }
            bos.write(bao.getBuffer(), 0, bao.getPosition());
            writeSize += bao.getPosition();
            bos.close();
            File file = new File(getCacheFileParent(), FILE_CACHE_NAME);
            file.delete();
            tmp.renameTo(file);
            Log.log.info("writeCache: written " + Log.mb(writeSize) + " as " + Log.mb(size) + " in " + count + " segments" +
                " out of " + Log.mb(cacheSize) + " in " + segments.size() + " segments at " + TimeFormat.DEFAULT.withMillis().format(millis) +
                " in " + (System.currentTimeMillis() - millis) / 1000.0 + " seconds");
        } catch (Throwable t) {
            Log.log.error("Unexpected error writing cache", t);
        } finally {
            if (fos != null)
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
        }
        return writeUsage;
    }

    private void readCache() {
        if (!segments.isEmpty())
            return;
        File f = new File(getCacheFileParent(), FILE_CACHE_NAME);
        if (!f.isFile() || f.length() < 16)
            return;
        long millis = System.currentTimeMillis();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            final FileInputStream fileInput = fis;
            final long[] readSize = new long[1];
            BufferedInput in = new StreamInput(fis);
            if (in.readLong() != FILE_HEADER)
                throw new IOException("Unknown file header");
            long readUsage = in.readCompactLong();
            long readMillis = in.readCompactLong();
            ArrayList<Segment> newSegments = new ArrayList<Segment>();
            long size = 0;
            try {
                while (size < config.cacheLimit) {
                    Block block = new Block();
                    block.readBlock(in);
                    block.decompress();
                    Segment segment = new Segment(block, in.readCompactLong());
                    segment.usage = in.readCompactLong();
                    size += segment.size();
                    newSegments.add(segment);
                }
            } catch (EOFException ignored) {
            } catch (Throwable t) {
                Log.log.error("Unexpected error reading cache at " + (readSize[0] - in.available()), t);
            }
            Log.log.info("readCache: read " + Log.mb(readSize[0]) + " ouf of " + Log.mb(f.length()) +
                ", " + Log.mb(size) + " in " + newSegments.size() + " segments at " + TimeFormat.DEFAULT.withMillis().format(readMillis) +
                " in " + (System.currentTimeMillis() - millis) / 1000.0 + " seconds");
            synchronized (this) {
                for (Segment segment : newSegments) {
                    segments.put(segment);
                    cacheSize += segment.size();
                }
                usage = Math.max(usage, readUsage);
                writtenUsage = usage; // we've just read cache, so no need to write it again
            }
        } catch (Throwable t) {
            Log.log.error("Unexpected error reading cache", t);
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
        }
    }

    private File getCacheFileParent() {
        String fileCachePath = config.fileCachePath;
        return fileCachePath.length() == 0 ? null : new File(fileCachePath);
    }

    // Cache writer thread main method
    public void run() {
        do {
            try {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(config.fileCacheDumpPeriod));
                if (needsWriteCache())
                    updateWrittenUsage(writeCache());
            } catch (Throwable t) {
                Log.log.error("Unexpected error", t);
            }
        } while (Thread.currentThread() == cacheWriter);
    }
}
