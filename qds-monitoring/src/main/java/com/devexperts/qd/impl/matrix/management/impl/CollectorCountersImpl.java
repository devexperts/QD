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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.impl.matrix.management.RecordCounters;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Performance counters for collectors.
 * These counters are implementation-dependent and are subject to change in any future version.
 */
public class CollectorCountersImpl extends CollectorCounters {
    private static final CollectorOperation[] OPS = CollectorOperation.values();
    private static final int N_OPS = OPS.length;
    private static final long UNKNOWN = -1;
    static final int TOP_N = 5;

    private final DataScheme scheme;
    private long milliseconds;
    private int numberAdded;

    private final AtomicLong distributions = new AtomicLong();
    private final AtomicLongArray distributionIncomingRecords;
    private final AtomicLongArray distributionOutgoingRecords;
    private final AtomicLong distributionSpins = new AtomicLong();
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong retrievals = new AtomicLong();
    private final AtomicLong retrievedRecords = new AtomicLong();

    private volatile long distributionIncomingRecordsSum = UNKNOWN;
    private volatile long distributionOutgoingRecordsSum = UNKNOWN;

    private final AtomicReferenceArray<LockCounters> lockCountersGlobal =
        new AtomicReferenceArray<LockCounters>(N_OPS);

    public static String getAllLockOperations() {
        StringBuilder sb = new StringBuilder();
        for (CollectorOperation op : OPS) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(op);
        }
        return sb.toString();
    }

    public CollectorCountersImpl(DataScheme scheme, long milliseconds, int numberAdded) {
        this.scheme = scheme;
        this.milliseconds = milliseconds;
        this.numberAdded = numberAdded;
        int n = scheme.getRecordCount();
        distributionIncomingRecords = new AtomicLongArray(n);
        distributionOutgoingRecords = new AtomicLongArray(n);
    }

    public CollectorCountersImpl(DataScheme scheme) {
        this(scheme, System.currentTimeMillis(), 0);
    }

    public CollectorCountersImpl(CollectorCountersImpl other) {
        this(other.scheme, 0, 0);
        add(other);
    }

    public CollectorCountersImpl(CollectorCountersImpl cur, CollectorCountersImpl old) {
        this(cur.scheme, cur.getMilliseconds() - old.getMilliseconds(), 1);
        distributions.set(cur.distributions.get() - old.distributions.get());
        setDiff(distributionIncomingRecords, cur.distributionIncomingRecords, old.distributionIncomingRecords);
        setDiff(distributionOutgoingRecords, cur.distributionOutgoingRecords, old.distributionOutgoingRecords);
        distributionSpins.set(cur.distributionSpins.get() - old.distributionSpins.get());
        droppedRecords.set(cur.droppedRecords.get() - old.droppedRecords.get());
        retrievals.set(cur.retrievals.get() - old.retrievals.get());
        retrievedRecords.set(cur.retrievedRecords.get() - old.retrievedRecords.get());
        for (int i = 0; i < N_OPS; i++) {
            LockCounters curCounters = cur.lockCountersGlobal.get(i);
            if (curCounters != null) {
                LockCounters oldCounters = old.lockCountersGlobal.get(i);
                if (oldCounters != null)
                    lockCountersGlobal.set(i, new LockCounters(curCounters, oldCounters));
                else
                    lockCountersGlobal.set(i, new LockCounters(curCounters));
            }
        }
    }

    private static void setDiff(AtomicLongArray a, AtomicLongArray cur, AtomicLongArray old) {
        int n = a.length();
        for (int i = 0; i < n; i++)
            a.set(i, cur.get(i) - old.get(i));
    }

    @Override
    public CollectorCounters snapshot() {
        return new CollectorCountersImpl(this);
    }

    @Override
    public CollectorCounters since(CollectorCounters snapshot) {
        return snapshot == null ? this : new CollectorCountersImpl(this, (CollectorCountersImpl) snapshot);
    }

    public long getMilliseconds() {
        return numberAdded > 0 ? milliseconds / numberAdded : System.currentTimeMillis() - milliseconds;
    }

    public long getDistributions() {
        return distributions.get();
    }

    public long getDistributionIncomingRecords() {
        long sum = distributionIncomingRecordsSum;
        if (sum != UNKNOWN)
            return sum;
        return distributionIncomingRecordsSum = sum(distributionIncomingRecords);
    }

    public long getDistributionOutgoingRecords() {
        long sum = distributionOutgoingRecordsSum;
        if (sum != UNKNOWN)
            return sum;
        return distributionOutgoingRecordsSum = sum(distributionOutgoingRecords);
    }

    public long getDistributionSpins() {
        return distributionSpins.get();
    }

    public long getDroppedRecords() {
        return droppedRecords.get();
    }

    public long getRetrievals() {
        return retrievals.get();
    }

    public long getRetrievedRecords() {
        return retrievedRecords.get();
    }

    public double getAverageDistributionIncomingRecords() {
        return (double) getDistributionIncomingRecords() / getDistributions();
    }

    public double getAverageDistributionFanoutRecords() {
        return (double) getDistributionOutgoingRecords() / getDistributionIncomingRecords();
    }

    public double getAverageDistributionSpins() {
        return (double) getDistributionSpins() / getDistributions();
    }

    public double getAverageDistributionIncomingRecordsPerSecond() {
        return getDistributionIncomingRecords() * 1000.0 / getMilliseconds();
    }

    public double getAverageDistributionOutgoingRecordsPerSecond() {
        return getDistributionOutgoingRecords() * 1000.0 / getMilliseconds();
    }

    public double getAverageRetrievedRecords() {
        return (double) getRetrievedRecords() / getRetrievals();
    }

    public double getAverageRetrievedRecordsFraction() {
        return (double) getRetrievedRecords() / getDistributionOutgoingRecords();
    }

    public double getAverageRetrievedRecordsPerSecond() {
        return getRetrievedRecords() * 1000.0 / getMilliseconds();
    }

    public LockCounters lockCountersGlobal(CollectorOperation op) {
        LockCounters result = lockCountersGlobal.get(op.ordinal());
        if (result == null)
            synchronized (lockCountersGlobal) {
                result = lockCountersGlobal.get(op.ordinal());
                if (result == null)
                    lockCountersGlobal.set(op.ordinal(), result = new LockCounters());
            }
        return result;
    }

    @Override
    public void countLock(CollectorOperation op, long waitNanos, long lockNanos) {
        lockCountersGlobal(op).countLock(waitNanos, lockNanos);
    }

    @Override
    public void countDistributionAndClear(RecordCounters incoming, RecordCounters outgoing, int spins) {
        distributions.incrementAndGet();
        distributionSpins.addAndGet(spins);
        if (incoming.flushAndClear(distributionIncomingRecords))
            distributionIncomingRecordsSum = UNKNOWN;
        if (outgoing.flushAndClear(distributionOutgoingRecords))
            distributionOutgoingRecordsSum = UNKNOWN;
    }

    @Override
    public void countRetrieval(int records) {
        retrievals.incrementAndGet();
        retrievedRecords.addAndGet(records);
    }

    @Override
    public void countDropped(int records) {
        droppedRecords.addAndGet(records);
    }

    public void add(CollectorCountersImpl other) {
        milliseconds += other.getMilliseconds();
        numberAdded++;
        distributions.getAndAdd(other.distributions.get());
        add(distributionIncomingRecords, other.distributionIncomingRecords);
        add(distributionOutgoingRecords, other.distributionOutgoingRecords);
        distributionSpins.getAndAdd(other.distributionSpins.get());
        droppedRecords.getAndAdd(other.droppedRecords.get());
        retrievals.getAndAdd(other.retrievals.get());
        retrievedRecords.getAndAdd(other.retrievedRecords.get());
        for (int i = 0; i < N_OPS; i++) {
            LockCounters otherCounters = other.lockCountersGlobal.get(i);
            if (otherCounters != null) {
                LockCounters thisCounters = lockCountersGlobal(OPS[i]);
                thisCounters.add(otherCounters);
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        long millis = getMilliseconds();
        sb.append(fmtHeader(millis)).append(": ");
        sb.append(fmtDist()).append("; ");
        sb.append(fmtRetrieve());
        String glockStr = fmtLockCountersGlobal(millis);
        if (glockStr.length() > 0)
            sb.append("; GLOCK[").append(glockStr).append("]");
        fmtTopRpsTo(sb, "IN", distributionIncomingRecords, millis, TOP_N);
        fmtTopRpsTo(sb, "OUT", distributionOutgoingRecords, millis, TOP_N);
        return sb.toString();
    }

    public ReportBuilder reportTo(ReportBuilder rb, String name, int topSize) {
        long millis = getMilliseconds();
        rb.header(fmtHeader(millis) + (name == null ? "" : " on " + name), ReportBuilder.HEADER_LEVEL_COLLECTOR);
        rb.message(fmtDist());
        rb.message(fmtRetrieve());
        rb.beginTable();
        rb.newRow().td("Op");
        LockCounters.reportHeaderTo(rb);
        for (int i = 0; i < N_OPS; i++) {
            LockCounters counters = lockCountersGlobal.get(i);
            if (counters == null || counters.getLockTimes().getCount() == 0)
                continue;
            rb.newRow().td(OPS[i]);
            counters.reportDataTo(rb, millis);
        }
        rb.endTable();
        reportTopRpsTo(rb, "IN", distributionIncomingRecords, millis, topSize);
        reportTopRpsTo(rb, "OUT", distributionOutgoingRecords, millis, topSize);
        return rb;
    }

    @Override
    public String textReport() {
        return reportTo(new ReportBuilder(ReportBuilder.TEXT), null, TOP_N).toString();
    }

    private String fmtHeader(long millis) {
        return "COUNTERS for " + fmt(millis / 1000.0) + " sec";
    }

    private String fmtDist() {
        return "DIST[" + fmt(getDistributions()) + "; " +
            "AVG: " +
            "in=" + fmt(getAverageDistributionIncomingRecords()) + " " +
            "fanout=" + fmt(getAverageDistributionFanoutRecords()) + " " +
            "spin=" + fmt(getAverageDistributionSpins()) + "; " +
            "RPS: " +
            "in=" + fmt(getAverageDistributionIncomingRecordsPerSecond()) + " " +
            "out=" + fmt(getAverageDistributionOutgoingRecordsPerSecond()) + "; " +
            "DROPPED: " + fmt(getDroppedRecords()) + "]";
    }

    private String fmtRetrieve() {
        return "RETRIEVE[" + fmt(getRetrievals()) + "; " +
            "AVG: " +
            "ret=" + fmt(getAverageRetrievedRecords()) + " " +
            "frac=" + fmt(getAverageRetrievedRecordsFraction() * 100) + "%; " +
            "RPS: " +
            "ret=" + fmt(getAverageRetrievedRecordsPerSecond()) + "]";
    }

    private String fmtLockCountersGlobal(long millis) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N_OPS; i++) {
            LockCounters counters = lockCountersGlobal.get(i);
            if (counters == null || counters.getLockTimes().getCount() == 0)
                continue;
            if (sb.length() > 0)
                sb.append(' ');
            sb.append(OPS[i]).append("=").append(counters.fmtString(millis));
        }
        return sb.toString();
    }

    private void fmtTopRpsTo(StringBuilder sb, String header, AtomicLongArray count, long millis, int topSize) {
        List<CountItem> list = buildTopList(scheme, count, topSize);
        if (list.isEmpty())
            return;
        sb.append("; TOP_RPS_").append(header).append('[');
        for (CountItem item : list) {
            sb.append(item.record).append('=').append(fmt(item.count * 1000.0 / millis)).append(' ');
        }
        sb.setCharAt(sb.length() - 1, ']');
    }

    private void reportTopRpsTo(ReportBuilder rb, String header, AtomicLongArray count, long millis, int topSize) {
        List<CountItem> list = buildTopList(scheme, count, topSize);
        if (list.isEmpty())
            return;
        rb.header("TOP RPS " + header, ReportBuilder.HEADER_LEVEL_SECTION);
        rb.beginTable().newRow().td("Record").td("RPS").endTR();
        for (CountItem item : list) {
            rb.newRow().td(item.record).td(fmt(item.count * 1000.0 / millis)).endTR();
        }
        rb.endTable();
    }

    private static List<CountItem> buildTopList(DataScheme scheme, AtomicLongArray count, int topSize) {
        List<CountItem> list = new ArrayList<>();
        for (int i = 0; i < count.length(); i++)
            if (count.get(i) != 0)
                list.add(new CountItem(scheme.getRecord(i), count.get(i)));
        Collections.sort(list);
        while (list.size() > topSize && !list.isEmpty())
            list.remove(list.size() - 1);
        return list;
    }

    // ---------------------- static helpers ----------------------

    /**
     * A dirty hack to reuse package-private implementation from outside.
     */
    public static String reportCounters(DataScheme scheme, Map<String, AtomicLongArray> counters, String format, int topSize) {
        ReportBuilder rb = new ReportBuilder(format);
        for (String header : counters.keySet()) {
            AtomicLongArray count = counters.get(header);
            List<CountItem> list = buildTopList(scheme, count, topSize);
            if (list.isEmpty())
                continue;
            rb.header(header, ReportBuilder.HEADER_LEVEL_SECTION);
            rb.beginTable().newRow().td("Record").td("Count").endTR();
            for (CountItem item : list) {
                rb.newRow().td(item.record).td(item.count).endTR();
            }
            rb.endTable();
        }
        return rb.toString();
    }

    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT = new ThreadLocal<NumberFormat>();

    private static String fmt(double d) {
        if (Double.isNaN(d))
            return "0";
        if (d >= 99.95)
            d = Math.floor(d + 0.5);
        else if (d >= 9.995)
            d = Math.floor(d * 10 + 0.5) / 10;
        else
            d = Math.floor(d * 100 + 0.5) / 100;
        NumberFormat nf = NUMBER_FORMAT.get();
        if (nf == null) {
            nf = NumberFormat.getInstance(Locale.US);
            nf.setMaximumFractionDigits(2);
            NUMBER_FORMAT.set(nf);
        }
        return nf.format(d);
    }

    private static long sum(AtomicLongArray arr) {
        long sum = 0;
        int n = arr.length();
        for (int i = 0; i < n; i++)
            sum += arr.get(i);
        return sum;
    }

    private static void add(AtomicLongArray cur, AtomicLongArray old) {
        int n = cur.length();
        for (int i = 0; i < n; i++)
            cur.getAndAdd(i, old.get(i));
    }

    private static class CountItem implements Comparable<CountItem> {
        final String record;
        final long count;

        CountItem(DataRecord record, long count) {
            this.record = record.getName();
            this.count = count;
        }

        public int compareTo(CountItem o) {
            return count > o.count ? -1 : count < o.count ? 1 : record.compareTo(o.record);
        }
    }
}
