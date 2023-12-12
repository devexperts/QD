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
package com.devexperts.mars.jvm;

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.common.MARSPlugin;
import com.devexperts.mars.common.MARSScheduler;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Monitors vital JVM statistics and publishes them into MARS.
 */
public class JVMSelfMonitoring implements MARSPlugin, Runnable, JVMSelfMonitoringMXBean {

    private static final String MBEAN_NAME = "com.devexperts.mars:type=JVMSelfMonitoring";

    private static final long FIND_DEADLOCK_PERIOD =
        SystemProperties.getLongProperty(JVMSelfMonitoring.class, "findDeadlockPeriod", 10) * TimeUtil.SECOND;

    private static final int THREAD_DUMPS_COUNT =
        SystemProperties.getIntProperty(JVMSelfMonitoring.class, "threadDumpsCount", 0);

    private static final String THREAD_DUMPS_FILE =
        SystemProperties.getProperty(JVMSelfMonitoring.class, "threadDumpsFile", "");

    private static final TimePeriod THREAD_DUMPS_PERIOD = TimePeriod.valueOf(
        SystemProperties.getProperty(JVMSelfMonitoring.class, "threadDumpsPeriod", "1s"));

    private static final long THREAD_DUMPS_SCHEDULED_AT = TimeFormat.DEFAULT.parse(
        SystemProperties.getProperty(JVMSelfMonitoring.class, "threadDumpsScheduledAt", "0")).getTime();

    private static final int STATE_NEW = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_STOPPED = 2;

    private int state = STATE_NEW;
    private Management.Registration registration;

    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    private final MARSNode root;

    private final MARSNode uptimeNode;
    private final MARSNode cpuTimeNode;
    private final MARSNode cpuUsageNode;

    private final MARSNode memHeapMaxNode;
    private final MARSNode memHeapSizeNode;
    private final MARSNode memHeapUsedNode;
    private final MARSNode memHeapUsageNode;

    private final MARSNode memNonHeapMaxNode;
    private final MARSNode memNonHeapSizeNode;
    private final MARSNode memNonHeapUsedNode;
    private final MARSNode memNonHeapUsageNode;

    private final MARSNode threadsCurrentNode;
    private final MARSNode threadsPeakNode;
    private final MARSNode threadsDeadlockedNode;

    private final MARSNode gcTimeNode;
    private final MARSNode gcRatioNode;
    private final MARSNode gcAverageNode;

    private static class GCBean {
        private final MARSNode countNode;
        private final MARSNode timeNode;
        private final MARSNode ratioNode;
        private long lastCollectionTime;

        GCBean(GarbageCollectorMXBean gc, MARSNode root) {
            countNode = root.subNode("jvm.GC." + gc.getName() + ".count", "Total number of garbage collections that have occurred.");
            timeNode = root.subNode("jvm.GC." + gc.getName() + ".time", "Approximate accumulated garbage collection elapsed time.");
            ratioNode = root.subNode("jvm.GC." + gc.getName() + ".ratio", "Ratio of time spent in garbage collection, %.");
        }

        long update(GarbageCollectorMXBean gc, long interval) {
            long delta = gc.getCollectionTime() - lastCollectionTime;
            lastCollectionTime += delta;
            countNode.setDoubleValue(gc.getCollectionCount());
            timeNode.setValue(timeToString(lastCollectionTime / 1000));
            ratioNode.setDoubleValue(interval <= 0 ? 0 : delta * 10000 / interval / 100.0);
            return delta;
        }
    }

    private final CpuCounter cpu = new CpuCounter();
    private final Map<String, GCBean> gcBeans = new HashMap<String, GCBean>();
    private final long[] lastGCReports = new long[31 * 2]; // data in pairs (collectionTime, reportTime), ascending
    private long lastCollectionTime;
    private long lastReportingTime = System.currentTimeMillis();
    private long lastFindDeadlockTime;

    private int threadDumpsCount = THREAD_DUMPS_COUNT;
    private String threadDumpsFile = THREAD_DUMPS_FILE;
    private TimePeriod threadDumpsPeriod = THREAD_DUMPS_PERIOD;
    private long threadDumpsScheduledAt = THREAD_DUMPS_SCHEDULED_AT;
    private ThreadDumper threadDumper;

    public JVMSelfMonitoring(MARSNode root) {
        this.root = root;

        root.subNode("jvm").setValue(System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        root.subNode("jvm.props.OS").setValue(SystemProperties.getProperty("os.name", "<unknown>") + " " + SystemProperties.getProperty("os.version", "<unknown>") + " " + SystemProperties.getProperty("os.arch", "<unknown>"));
        root.subNode("jvm.props.dir").setValue(SystemProperties.getProperty("user.dir", ""));
        root.subNode("jvm.props.java").setValue(SystemProperties.getProperty("java.vm.name", "<unknown>") + " " + SystemProperties.getProperty("java.version", "<unknown>") + ", home " + SystemProperties.getProperty("java.home", "<unknown>"));
        root.subNode("jvm.props.startTime").setTimeValue(runtimeMXBean.getStartTime());
        root.subNode("jvm.props.timezone").setValue(TimeZone.getDefault().getID());
        try {
            root.subNode("jvm.props.host").setValue(InetAddress.getLocalHost().toString());
        } catch (Exception e) {
            log().warn("Cannot get host address: " + e);
        }

        uptimeNode = root.subNode("jvm.uptime", "Uptime in days, hours, mins, secs.");
        cpuTimeNode = root.subNode("jvm.cpu.time", "The amount of consumed CPU time.");
        cpuUsageNode = root.subNode("jvm.cpu.usage", "CPU consumption as percent of total CPU capacity, %.");
        memHeapMaxNode = root.subNode("jvm.mem.heap_max", "Configured maximum heap size, Kb.");
        memHeapSizeNode = root.subNode("jvm.mem.heap_size", "The amount of allocated heap memory, Kb.");
        memHeapUsedNode = root.subNode("jvm.mem.heap_used", "The amount of used heap memory, Kb.");
        memHeapUsageNode = root.subNode("jvm.mem.heap_usage", "The amount of used heap memory as percent of maximum heap size, %.");
        memNonHeapMaxNode = root.subNode("jvm.mem.non-heap_max", "Configured maximum non-heap size, Kb.");
        memNonHeapSizeNode = root.subNode("jvm.mem.non-heap_size", "The amount of allocated non-heap memory, Kb.");
        memNonHeapUsedNode = root.subNode("jvm.mem.non-heap_used", "The amount of used non-heap memory, Kb.");
        memNonHeapUsageNode = root.subNode("jvm.mem.non-heap_usage", "The amount of used non-heap memory as percent of maximum non-heap size, %.");
        threadsCurrentNode = root.subNode("jvm.threads.current", "The current number of live threads.");
        threadsPeakNode = root.subNode("jvm.threads.peak", "The peak live thread count since the JVM started or peak was reset.");
        threadsDeadlockedNode = root.subNode("jvm.threads.deadlocked", "The current number of deadlocked threads.");
        gcTimeNode = root.subNode("jvm.GC.time", "Net accumulated garbage collection elapsed time.");
        gcRatioNode = root.subNode("jvm.GC.ratio", "Net ratio of time spent in garbage collections of all types, %.");
        gcAverageNode = root.subNode("jvm.GC.average_5min", "Net ratio of time spent in garbage collections of all types during last 5 minutes, %.");
    }

    public synchronized void start() {
        if (state == STATE_STARTED)
            return;
        state = STATE_STARTED;
        registration = Management.registerMBean(this, JVMSelfMonitoringMXBean.class, MBEAN_NAME);
        MARSScheduler.schedule(this);
        run(); // report once
        checkThreadDumper();
    }

    public synchronized void stop() {
        if (state != STATE_STARTED)
            return;
        state = STATE_STOPPED;
        registration.unregister();
        MARSScheduler.cancel(this);
        cpu.close();
        checkThreadDumper();
    }

    // REQUIRES SYNC(this)
    private void checkThreadDumper() {
        // kill old thread dumper if not longer needed or file name changed
        if (threadDumper != null &&
            (threadDumpsCount <= 0 || state != STATE_STARTED || !threadDumper.getFile().equals(threadDumpsFile)))
        {
            threadDumper.interrupt();
            threadDumper = null;
        }
        // start new thread dumper if needed
        if (threadDumper == null && threadDumpsCount > 0 && state == STATE_STARTED) {
            threadDumper = new ThreadDumper(this, threadDumpsFile);
            threadDumper.start();
        }
    }

    synchronized long getThreadDumpsPeriodTime() {
        return threadDumpsPeriod.getTime();
    }

    synchronized long getThreadDumpsScheduledAtTime() {
        return threadDumpsScheduledAt;
    }

    synchronized void threadDumperTerminated(ThreadDumper threadDumper) {
        // handle unexpected termination of an active thread-dumper by restarting it as needed
        if (threadDumper == this.threadDumper) {
            this.threadDumper = null;
            checkThreadDumper();
        }
    }

    synchronized void countThreadDump() {
        if (threadDumpsCount > 0)
            threadDumpsCount--;
    }

    ThreadMXBean getThreadMXBean() {
        return threadMXBean;
    }

    public void run() {
        long currentTime = System.currentTimeMillis();

        uptimeNode.setValue(timeToString(runtimeMXBean.getUptime() / 1000));
        cpuTimeNode.setValue(timeToString(cpu.getCpuTime() / 1000000000));
        cpuUsageNode.setDoubleValue((long) (cpu.getCpuUsage() * 10000) / 100.0);

        setMemoryUsage(memoryMXBean.getHeapMemoryUsage(), memHeapMaxNode, memHeapSizeNode, memHeapUsedNode, memHeapUsageNode);
        setMemoryUsage(memoryMXBean.getNonHeapMemoryUsage(), memNonHeapMaxNode, memNonHeapSizeNode, memNonHeapUsedNode, memNonHeapUsageNode);

        threadsCurrentNode.setIntValue(threadMXBean.getThreadCount());
        threadsPeakNode.setIntValue(threadMXBean.getPeakThreadCount());
        if (FIND_DEADLOCK_PERIOD > 0 && currentTime >= lastFindDeadlockTime + FIND_DEADLOCK_PERIOD) {
            threadsDeadlockedNode.setIntValue(getThreadDeadlockedCount());
            lastFindDeadlockTime = currentTime;
        }

        long delta = 0;
        long interval = currentTime - lastReportingTime;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            GCBean bean = gcBeans.get(gc.getName());
            if (bean == null)
                gcBeans.put(gc.getName(), bean = new GCBean(gc, root));
            delta += bean.update(gc, interval);
        }
        lastCollectionTime += delta;
        lastReportingTime += interval;
        gcTimeNode.setValue(timeToString(lastCollectionTime / 1000));
        gcRatioNode.setDoubleValue(interval <= 0 ? 0 : delta * 10000 / interval / 100.0);
        if (interval < 0) {
            // Backward time change detected - update all stored times to avoid algorithmic errors.
            for (int i = 0; i < lastGCReports.length; i += 2)
                if (lastGCReports[i + 1] != 0)
                    lastGCReports[i + 1] += interval;
        } else if (lastReportingTime >= lastGCReports[lastGCReports.length - 1] + 9900) {
            System.arraycopy(lastGCReports, 2, lastGCReports, 0, lastGCReports.length - 2);
            lastGCReports[lastGCReports.length - 2] = lastCollectionTime;
            lastGCReports[lastGCReports.length - 1] = lastReportingTime;
            for (int i = 0; i < lastGCReports.length - 2; i += 2)
                if (lastGCReports[i + 1] != 0 && lastGCReports[i + 3] > lastReportingTime - 5 * 60 * 1000) {
                    gcAverageNode.setDoubleValue((lastCollectionTime - lastGCReports[i]) * 10000 / (lastReportingTime - lastGCReports[i + 1]) / 100.0);
                    break;
                }
        }
    }

    static String timeToString(long seconds) {
        StringBuilder sb = new StringBuilder(32);
        append(sb, seconds / 86400, " days ");
        append(sb, seconds % 86400 / 3600, " hours ");
        append(sb, seconds % 3600 / 60, " min ");
        sb.append(seconds % 60).append(" sec");
        return sb.toString();
    }

    private static void append(StringBuilder sb, long v, String s) {
        if (v > 0)
            sb.append(v).append(s);
    }

    private static void setMemoryUsage(MemoryUsage memory, MARSNode maxNode, MARSNode sizeNode, MARSNode usedNode, MARSNode usageNode) {
        maxNode.setDoubleValue(memory.getMax() >> 10);
        sizeNode.setDoubleValue(memory.getCommitted() >> 10);
        usedNode.setDoubleValue(memory.getUsed() >> 10);
        usageNode.setDoubleValue(getUsage(memory));
    }

    private static double getUsage(MemoryUsage memory) {
        long maxMemory = Math.max(memory.getMax(), memory.getCommitted());
        if (maxMemory <= 0) {
            // Under Substrate VM in non-heap memory (see java.lang.management.MemoryMXBean#getNonHeapMemoryUsage),
            // committed memory will be 0
            return 100.0;
        }
        return memory.getUsed() * 10000 / maxMemory / 100.0;
    }

    private static String getUsageString(MemoryUsage memory) {
        return getUsage(memory) + "%: " + (memory.getUsed() >> 20) + "M of " + (Math.max(memory.getMax(), memory.getCommitted()) >> 20) + "M";
    }

    private static Logging log() {
        return Logging.getLogging(JVMSelfMonitoring.class);
    }

    // ========== MXBean Implementation ==========

    public String getUptime() {
        return uptimeNode.getValue() + " (cpuTime = " + cpuTimeNode.getValue() + ")";
    }

    public String getCpuUsage() {
        return cpuUsageNode.getValue() + "%";
    }

    public String getHeapSize() {
        return (memoryMXBean.getHeapMemoryUsage().getCommitted() >> 20) + "M";
    }

    public String getHeapUsage() {
        return getUsageString(memoryMXBean.getHeapMemoryUsage());
    }

    public String getNonHeapSize() {
        return (memoryMXBean.getNonHeapMemoryUsage().getCommitted() >> 20) + "M";
    }

    public String getNonHeapUsage() {
        return getUsageString(memoryMXBean.getNonHeapMemoryUsage());
    }

    public String getThreadCount() {
        return threadMXBean.getThreadCount() + " (peak = " + threadMXBean.getPeakThreadCount() + ")";
    }

    public int getThreadDeadlockedCount() {
        try {
            long[] t = threadMXBean.findMonitorDeadlockedThreads();
            return t == null ? 0 : t.length;
        } catch (Throwable t) {
            // An error will be thrown for unsupported operations,
            // e.g. Substrate VM does not support calling threadMXBean.findMonitorDeadlockedThreads()
            return 0;
        }
    }

    public String getTimeZone() {
        TimeZone tz = TimeZone.getDefault();
        return tz.getID() + " (" + tz.getDisplayName() + ")";
    }

    public synchronized int getThreadDumpsCount() {
        return threadDumpsCount;
    }

    public synchronized String getThreadDumpsFile() {
        return threadDumpsFile;
    }

    public synchronized String getThreadDumpsPeriod() {
        return threadDumpsPeriod.toString();
    }

    public synchronized String getThreadDumpsScheduledAt() {
        return threadDumpsScheduledAt == 0 ? "" : TimeFormat.DEFAULT.format(threadDumpsScheduledAt);
    }

    public synchronized void makeThreadDumps(int count, String file, String period, String scheduledAt) {
        if (period != null && period.length() > 0)
            threadDumpsPeriod = TimePeriod.valueOf(period);
        threadDumpsCount = count;
        threadDumpsFile = file == null ? "" : file;
        threadDumpsScheduledAt = scheduledAt == null || scheduledAt.isEmpty() ? 0 :
            TimeFormat.DEFAULT.parse(scheduledAt).getTime();
        checkThreadDumper();
    }

    public void forceGarbageCollection() {
        //noinspection CallToSystemGC
        System.gc();
    }

    @Override
    public String toString() {
        return "JVM self-monitoring";
    }

    @ServiceProvider
    public static class PluginFactory extends Factory {
        @Override
        public MARSPlugin createPlugin(MARSEndpoint marsEndpoint) {
            return new JVMSelfMonitoring(marsEndpoint.getRoot());
        }
    }
}
