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

import java.io.PrintStream;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

class ThreadDumper extends Thread {
    private static final Comparator<ThreadInfo> THREAD_ID_COMPARATOR = new Comparator<ThreadInfo>() {
        public int compare(ThreadInfo t1, ThreadInfo t2) {
            return t1.getThreadId() < t2.getThreadId() ? -1 : t1.getThreadId() > t2.getThreadId() ? 1 : 0;
        }
    };

    private final JVMSelfMonitoring selfMonitoring;
    private final String file;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.sssZ", Locale.US);
    private String header;

    ThreadDumper(JVMSelfMonitoring selfMonitoring, String file) {
        super("ThreadDumper-" + (file.isEmpty() ? "stdout" : file));
        setDaemon(true);
        this.selfMonitoring = selfMonitoring;
        this.file = file;
        this.header = "Full thread dump " + System.getProperty("java.vm.name") +
            " (" + System.getProperty("java.vm.version") + " " + System.getProperty("java.vm.info") + "):";
    }

    String getFile() {
        return file;
    }

    private static Logging log() {
        return Logging.getLogging(JVMSelfMonitoring.class);
    }

    @Override
    public void run() {
        try {
            // wait until scheduled time
            while (true) {
                long time = System.currentTimeMillis();
                long waitTill = selfMonitoring.getThreadDumpsScheduledAtTime();
                if (time >= waitTill)
                    break;
                Thread.sleep(waitTill - time);
            }
            // open file & make dumps
            PrintStream out = file.isEmpty() ? System.out : new PrintStream(file);
            try {
                while (!interrupted() && selfMonitoring.getThreadDumpsCount() > 0) {
                    makeThreadDump(out);
                    selfMonitoring.countThreadDump();
                    sleep(selfMonitoring.getThreadDumpsPeriodTime());
                }
            } finally {
                if (!file.isEmpty())
                    out.close();
            }
        } catch (InterruptedException e) {
            // interrupted from JVMSelfMonitoring -- quit
        } catch (Throwable t) {
            log().error("Failed to make thread dumps", t);
        } finally {
            selfMonitoring.threadDumperTerminated(this);
        }
    }

    private void makeThreadDump(PrintStream out) {
        // get all thread infos
        ThreadMXBean threadMXBean = selfMonitoring.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(
            threadMXBean.isObjectMonitorUsageSupported(), threadMXBean.isSynchronizerUsageSupported());
        Arrays.sort(threadInfos, THREAD_ID_COMPARATOR);
        Date now = new Date();
        out.println(dateFormat.format(now) + "T" + timeFormat.format(now) + ": ThreadDumper: Writing thread dump");
        out.println(header);
        out.println();
        for (ThreadInfo ti : threadInfos) {
            String lockOwner = ti.getLockOwnerId() > 0 ? " owned by \"" + ti.getLockOwnerName() + "\" id=" + ti.getLockOwnerId() : "";
            String lockInfo = ti.getLockName() != null ? " (lock=" + ti.getLockName() + lockOwner + ")" : "";
            out.println("\"" + ti.getThreadName() + "\"" + " id=" + ti.getThreadId() + " " + ti.getThreadState() + lockInfo);
            for (StackTraceElement ste : ti.getStackTrace())
                out.println("\tat " + ste);
            out.println();
        }
        out.flush();
    }
}
