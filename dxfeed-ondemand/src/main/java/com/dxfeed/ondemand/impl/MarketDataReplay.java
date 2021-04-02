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

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.ondemand.impl.event.MDREventUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * All methods are blocking.
 * Thread-safe.
 */
public class MarketDataReplay implements Runnable {

    // ------------------------ instance ------------------------

    private volatile Thread worker;

    private volatile MarketDataToken token = new MarketDataToken(Collections.<String,String>emptyMap(), null);
    private volatile MarketDataToken badToken; // Last token for which Access Error was received.
    private final Map<String, Long> badAddresses = Collections.synchronizedMap(new HashMap<String, Long>());
    private long badAddressTimeout = 10 * TimeUtil.MINUTE;

    private final CacheConfig config = new CacheConfig();

    private Cache cache; // will be initialized on start according to config
    private final Current current = new Current();

    private long prefetchInterval = 59 * TimeUtil.SECOND;

    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private volatile String status;

    private long prevReplayTime;
    private long prevReplayMillis;

    private final Thread[] stoppedThreads = new Thread[2];

    public long getCacheLimit() {
        return config.cacheLimit;
    }

    public void setCacheLimit(long amount) {
        config.cacheLimit = amount;
    }

    public long getFileCacheLimit() {
        return config.fileCacheLimit;
    }

    public void setFileCacheLimit(long amount) {
        config.fileCacheLimit = amount;
    }

    public String getFileCachePath() {
        return config.fileCachePath;
    }

    public void setFileCachePath(String path) {
        if (path == null)
            throw new NullPointerException();
        config.fileCachePath = path;
    }

    public long getFileCacheDumpPeriod() {
        return config.fileCacheDumpPeriod;
    }

    public void setFileCacheDumpPeriod(long fileCacheDumpPeriod) {
        config.fileCacheDumpPeriod = fileCacheDumpPeriod;
    }

    public long getTimeToLive() {
        return config.timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        config.timeToLive = timeToLive;
    }

    public synchronized void setTime(long time) {
        if (time == current.time)
            return; // nothing changes
        Log.log.info("setTime(" + TimeFormat.DEFAULT.withMillis().format(time) + ")");
        for (CurrentSegment segment : current.segments)
            segment.restart();
        current.time = time;
        if (!current.isCurrentInterval(time) && cache != null)
            cache.rebuildCurrentSegments(current);
        awaken();
    }

    public synchronized long getTime() {
        return current.time;
    }

    public synchronized void clearSubscription() {
        current.subscription.clear();
    }

    /** @deprecated Use {@link #setSubscription(RecordBuffer)} */
    public void setSubscription(SubscriptionBuffer sb) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.processSubscription(sb);
        setSubscription(sub);
        sub.release();
    }

    public synchronized void setSubscription(RecordBuffer sub) {
        current.subscription.clear();
        current.subscription.addAll(buildSubscription("setSubscription", sub));
        if (cache != null)
            cache.rebuildCurrentSegments(current);
        awaken();
    }

    public synchronized void addSubscription(RecordBuffer sub) {
        if (sub.isEmpty())
            return;
        for (Key key : buildSubscription("addSubscription", sub)) {
            Key old = current.subscription.put(key);
            if (old != null)
                key.subscriptionCount += old.subscriptionCount;
        }
        if (cache != null)
            cache.rebuildCurrentSegments(current);
        awaken();
    }

    public synchronized void removeSubscription(RecordBuffer sub) {
        if (sub.isEmpty())
            return;
        for (Key key : buildSubscription("removeSubscription", sub)) {
            Key old = current.subscription.getByKey(key);
            if (old != null) {
                old.subscriptionCount -= key.subscriptionCount;
                if (old.subscriptionCount <= 0)
                    current.subscription.removeKey(key);
            }
        }
        if (cache != null)
            cache.rebuildCurrentSegments(current);
        awaken();
    }

    private IndexedSet<Key, Key> buildSubscription(String method, RecordBuffer sub) {
        IndexedSet<Key, Key> subscription = new IndexedSet<Key, Key>();
        Set<String> goodRecords = new IndexedSet<String, String>();
        Set<String> goodSymbols = new IndexedSet<String, String>();
        Set<String> badRecords = new TreeSet<String>();
        Set<String> badSymbols = new TreeSet<String>();
        RecordCursor cur;
        while ((cur = sub.next()) != null) {
            DataRecord record = cur.getRecord();
            char type = MDREventUtil.getType(record);
            String symbol = record.getScheme().getCodec().decode(cur.getCipher(), cur.getSymbol()).intern();
            boolean goodSymbol = MDREventUtil.isGoodSymbol(symbol);
            if (type != 0)
                goodRecords.add(record.getName());
            else
                badRecords.add(record.getName());
            if (goodSymbol)
                goodSymbols.add(symbol);
            else
                badSymbols.add(symbol == null ? "<null>" : symbol.length() == 0 ? "<empty>" : symbol);
            if (type != 0 && goodSymbol) {
                Key key = subscription.putIfAbsentAndGet(new Key(symbol, MDREventUtil.getExchange(record), type));
                key.subscriptionCount++;
            }
        }
        Log.log.info(method + ": " + goodRecords.size() + " records, " + goodSymbols.size() + " symbols" +
            ", ignored " + badRecords + " " + badSymbols + ", categories:" + MDREventUtil.countCategories(subscription));
        return subscription;
    }

    public synchronized void setToken(MarketDataToken token) {
        if (token == null)
            throw new NullPointerException("token is null");
        if (token == this.token)
            return;
        this.token = token;
        Log.log.info("setToken(" + token + ")");
        awaken();
    }

    public synchronized boolean hasPermanentError() {
        return token == badToken;
    }

    private void awaken() {
        LockSupport.unpark(worker);
    }

    public synchronized void start() {
        Log.log.info("start");
        status = null;
        // create new worker. Old one will die by itself
        worker = new Thread(this, "MarketDataReplay-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    // will be called from worker thread as a first action
    private synchronized void updateCacheInstance() {
        // reacquire cache instance if needed
        config.address = token.getServiceAddress();
        if (cache != null && !config.equals(cache.getConfig())) {
            cache.release();
            cache = null;
        }
        if (cache == null)
            cache = Cache.acquireInstance(config);
        cache.rebuildCurrentSegments(current);
    }

    private synchronized Thread releaseCacheInstance(Current current) {
        Cache cache = this.cache;
        this.cache = null;
        if (cache != null) {
            cache.releaseSegments(current);
            return cache.release();
        }
        return null;
    }

    public synchronized void stop() {
        Log.log.info("stop");
        status = null;
        // stop worker if needed
        Thread worker = this.worker;
        this.worker = null;
        if (worker != null)
            LockSupport.unpark(worker);
        stoppedThreads[0] = worker;
        stoppedThreads[1] = releaseCacheInstance(current);
    }

    public Thread[] getStoppedThreads() {
        return stoppedThreads;
    }

    private synchronized ReplayRequest prepareRequest(MarketDataToken token, boolean prefetch) throws IOException {
        long requestTime = prefetch ? current.endTime : current.time;
        IndexedSet<Key, Key> presentKeys = new IndexedSet<Key, Key>();
        IndexedSet<Key, Key> expiredKeys = new IndexedSet<Key, Key>();
        if (cache != null)
            cache.checkRequestKeys(current, requestTime, presentKeys, expiredKeys);

        boolean urgent = false;
        ArrayList<Key> requestKeys = new ArrayList<Key>();
        for (Key key : current.subscription)
            if (!presentKeys.containsKey(key)) {
                requestKeys.add(key);
                if (!expiredKeys.containsKey(key))
                    urgent = true;
            }
        if (requestKeys.isEmpty())
            return null;
        Collections.sort(requestKeys, Key.COMPARATOR);

        ReplayRequest request = new ReplayRequest();
        request.setToken(token);
        request.setAllowedDelay(urgent ? Math.min((long) ((requestTime - current.time) / current.replaySpeed), 60000) : 60000);
        request.setRequestTime(requestTime);
        request.addRequestKeys(requestKeys);
        Log.log.info("Request <" + token.getTokenUser() + "/" + token.getTokenContract() + "> [" + requestKeys.size() +
            " keys at " + TimeFormat.DEFAULT.format(requestTime) + "]" + MDREventUtil.countCategories(requestKeys) +
            ", urgent " + urgent + ", replay speed " + current.replaySpeed + ", delay " + request.getAllowedDelay() / 1000.0 + " seconds");
        return request;
    }

    private ByteArrayInput doRequest(ByteArrayOutput request, int addressHash, MarketDataToken token) {
        ArrayList<String> addresses = new ArrayList<String>(Arrays.asList(token.getServiceAddress().split(",")));
        if (!addresses.isEmpty() && badAddresses.keySet().containsAll(addresses))
            badAddresses.clear();
        while (!addresses.isEmpty()) {
            String address = addresses.remove(Math.abs(addressHash % addresses.size()));
            Long badTime = badAddresses.get(address);
            if (badTime != null && badTime > System.currentTimeMillis() - badAddressTimeout)
                continue;
            HttpURLConnection con = null;
            try {
                con = prepareConnection(address);
                OutputStream output = con.getOutputStream();
                output.write(request.getBuffer(), 0, request.getPosition());
                output.close();
                sentBytes.addAndGet(200 + request.getPosition());
                InputStream input = con.getInputStream();
                receivedBytes.addAndGet(100);
                status = null;
                return readResponse(input);
            } catch (IOException e) {
                if (e instanceof NoRouteToHostException || e instanceof ConnectException || e instanceof SocketTimeoutException) {
                    Log.log.error("Unable to connect to " + LogUtil.hideCredentials(address) + ": " + e);
                    badAddresses.put(address, System.currentTimeMillis());
                    continue;
                }
                receivedBytes.addAndGet(50);
                Log.log.error("Unexpected error", e);
                try {
                    if (con != null && con.getResponseCode() == 401) {
                        badToken = token;
                        status = "Service unavailable due to security issues.";
                        Log.log.warn("status = " + status);
                        return null;
                    }
                } catch (IOException ee) {
                    Log.log.error("Nested error: " + ee);
                }
                continue;
            }
        }
        status = "Service unavailable due to connectivity issues.";
        Log.log.warn("status = " + status);
        return null;
    }

    private HttpURLConnection prepareConnection(String address) throws IOException {
        URL url = new URL("http://" + address + "/MarketDataReplay");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(60000);
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept", "application/octet-stream");
        con.setRequestProperty("Content-Type", "application/octet-stream");
        con.setDoOutput(true);
        return con;
    }

    private ByteArrayInput readResponse(InputStream input) throws IOException {
        //todo use Content-Length as estimate of response size
        ByteArrayInput response = new ByteArrayInput(new byte[10000]);
        response.setLimit(0);
        while (true) {
            int n = input.read(response.getBuffer(), response.getLimit(), response.getBuffer().length - response.getLimit());
            if (n < 0)
                throw new IOException("Unexpected end of response");
            receivedBytes.addAndGet(n);
            response.setLimit(response.getLimit() + n);
            if (response.getLimit() - response.getPosition() < ReplayUtil.getCompactLength(response.getBuffer(), response.getPosition()))
                continue;
            int position = response.getPosition();
            int length = response.readCompactInt();
            if (length <= 0)
                throw new IOException("Unexpected end of response");
            int limit = response.getPosition() + length;
            response.setPosition(position);
            response.ensureCapacity(limit);
            if (response.getLimit() >= limit)
                break;
        }
        input.close();
        return response;
    }

    private static ArrayList<Segment> unpackResponse(ByteArrayInput response) throws IOException {
        ReplayResponse rr = new ReplayResponse();
        rr.read(response);
        response = rr.getResponseBlocksInput();
        ArrayList<Segment> newSegments = new ArrayList<Segment>();
        while (response.available() > 0) {
            int blockPosition = response.getPosition();
            try {
                Block block = new Block();
                block.readBlock(response);
                block.decompress();
                newSegments.add(new Segment(block));
            } catch (Exception e) {
                response.setPosition(blockPosition);
                int blockLength = response.readCompactInt();
                response.setPosition(response.getPosition() + blockLength);
                Log.log.error("Error reading block", e);
            }
        }
        return newSegments;
    }

    private synchronized void addData(ArrayList<Segment> newSegments) {
        if (cache != null)
            cache.addData(newSegments);
    }

    public long getSentBytes() {
        return sentBytes.longValue();
    }

    public long getReceivedBytes() {
        return receivedBytes.longValue();
    }

    public String getStatus() {
        return status;
    }

    public synchronized double getAvailableData(long time) {
        return cache == null ? 0 : cache.getAvailableData(current, time);
    }

    public synchronized RecordBuffer getSnapshot(long time) {
        setTime(time);
        return getUpdate(time);
    }

    public synchronized RecordBuffer getUpdate(long time) {
        updateReplaySpeed(time);
        RecordBuffer buffer = RecordBuffer.getInstance(RecordMode.TIMESTAMPED_DATA);
        if (cache == null || time < current.time)
            return buffer;
        cache.rebuildCurrentSegmentsIfNeeded(current);
        readCurrent(buffer, time);
        if (!current.isCurrentInterval(time)) {
            current.time = time;
            cache.rebuildCurrentSegments(current);
            readCurrent(buffer, time);
            awaken();
        } else {
            long prefetchTime = current.endTime - prefetchInterval;
            if (current.time < prefetchTime && time >= prefetchTime)
                awaken();
            current.time = time;
        }
        return buffer;
    }

    // SYNC(this), requires cache != null
    private void readCurrent(RecordBuffer buffer, long time) {
        long usage = cache.nextUsage();
        for (CurrentSegment segment : current.segments)
            segment.read(buffer, time, usage);
    }

    private void updateReplaySpeed(long time) {
        long dt = time - prevReplayTime;
        long dm = System.currentTimeMillis() - prevReplayMillis;
        if (dt <= 0 || dt > 59000 || dm <= 0 || dm > 59000) {
            prevReplayTime += dt;
            prevReplayMillis += dm;
        } else if (dt >= 1000 && dm >= 1000) {
            current.replaySpeed = Math.floor((current.replaySpeed + Math.min(Math.max(0.1, (double) dt / dm), 10)) * 50 + 0.5) / 100;
            prevReplayTime += dt;
            prevReplayMillis += dm;
        }
    }

    // Worker thread main method
    public void run() {
        updateCacheInstance();
        if (Thread.currentThread() != worker) {
            // was stopped while we were initializing the cache
            releaseCacheInstance(current);
            return;
        }
        String testMode = System.getProperty("TestThinkOnDemand");
        if (testMode != null)
            try {
                MarketDataAccess.getInstance().startConfigurationWatcher(testMode, testMode + ".cache", 10000);
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        while (Thread.currentThread() == worker)
            try {
                MarketDataToken currentToken = token; // Atomic read.
                if (testMode != null)
                    currentToken = MarketDataAccess.getInstance().createToken("test");
                if (currentToken != badToken) {
                    ReplayRequest request = prepareRequest(currentToken, false);
                    //todo prefetch in separate thread, only when current is not fetching and only if current set is defined (i.e. remember Long.MAX_VALUE)
                    if (current.time >= current.endTime - prefetchInterval) {
                        if (request == null)
                            request = prepareRequest(currentToken, true);
                        else
                            awaken();
                    }
                    if (request != null) {
                        long millis = System.currentTimeMillis();
                        long oldSent = sentBytes.longValue();
                        long oldReceived = receivedBytes.longValue();
                        ByteArrayInput response = doRequest(request.write(), (int) (request.getRequestTime() / (30 * 60 * 1000)), currentToken);
                        if (response != null) {
                            ArrayList<Segment> newSegments = unpackResponse(response);
                            long size = 0;
                            for (Segment segment : newSegments)
                                size += segment.size();
                            Log.log.info("Response: " + Log.mb(size) + " in " + newSegments.size() + " segments" +
                                ", sent " + Log.mb(sentBytes.longValue() - oldSent) +
                                " received " + Log.mb(receivedBytes.longValue() - oldReceived) +
                                " in " + (System.currentTimeMillis() - millis) / 1000.0 + " seconds" +
                                ", total sent " + Log.mb(sentBytes.longValue()) +
                                ", total received " + Log.mb(receivedBytes.longValue()));//todo compression stats, etc
                            addData(newSegments);
                        }
                        Thread.sleep(1000); // Unconditional wait to prevent DOS due to bugs, etc.
                    }
                }
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));
            } catch (Throwable t) {
                Log.log.error("Unexpected error", t);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                }
            }
    }
}
