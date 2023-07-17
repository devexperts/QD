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
package com.devexperts.qd.tools;

import com.devexperts.services.ServiceProvider;
import com.devexperts.util.IndexedSet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses full thread dumps and organizes result data into performance profile.
 * The results are written to a file - by default 'profile.txt'.
 */
@ToolSummary(
    info = "Parses full thread dumps and writes performance profile.",
    argString = "<files>",
    arguments = {
        "<files> -- list of files to parse"
    }
)
@ServiceProvider
public class TDP extends AbstractTool {
    private final Option locks = new Option(
        'l', "locks", "Use full original stack traces when concurrent lock in contended state.");
    private final OptionInteger topdepth = new OptionInteger(
        't', "topdepth", "<number>", "Depth of reverse stack trace in tops section, by default 50.", 0, 1000, 50);
    private final OptionInteger methoddepth = new OptionInteger(
        'm', "methoddepth", "<number>", "Depth of stack trace in methods section, by default 50.", 0, 1000, 50);
    private final OptionDouble threshold = new OptionDouble(
        'h', "threshold", "<number>", "Threshold for contribution to expand, by default 5 (%).", 0, 100, 5);
    private final OptionString state = new OptionString(
        's', "state", "<regex>", "Limit report to a specific thread states, by default report all");
    private final OptionString name = new OptionString(
        'n', "name", "<regex>", "Limit report to a specific thread names, by default report all");
    private final OptionString output = new OptionString(
        'o', "output", "<file>", "Output file, by default 'profile.txt'.");
    private final OptionInteger count = new OptionInteger(
        'c', "count", "<number>", "Dump count used for result scaling (experimental).");

    @Override
    protected Option[] getOptions() {
        return new Option[] { locks, topdepth, methoddepth, threshold, count, state, name, output };
    }

    private static final String HEADER = "Profiling information at ";

    private Matcher stateMatcher;
    private Matcher nameMatcher;

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();
        stateMatcher = state.isSet() ? Pattern.compile(state.getValue()).matcher("") : null;
        nameMatcher = name.isSet() ?
            Pattern.compile(name.getValue(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher("") : null;
        if (stateMatcher != null && Arrays.stream(THREAD_STATES).noneMatch(s -> stateMatcher.reset(s.abbreviation).matches()))
            throw new OptionParseException("No matching thread state abbreviation is found for regex " + state.getValue());
        try {
            for (String file : args) {
                processFile(file);
            }
            if (dumpsCount > 0) {
                if (count.isSet())
                    dumpsCount = count.getValue();
                dumpToFile(output.isSet() ? output.getValue() : "profile.txt");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processFile(String file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file), 100000)) {
            line = in.readLine();
            if (line != null && line.startsWith(HEADER)) {
                try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file + "~"), 100000))) {
                    boolean top = false;
                    while (line != null) {
                        if (line.startsWith("-----")) {
                            top = false;
                        } else if (line.startsWith("Top ")) {
                            top = true;
                        }
                        int indent = 0;
                        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '|'))
                            indent++;
                        if (indent <= (top ? topdepth : methoddepth).getValue() * 2)
                            out.println(line);
                        line = in.readLine();
                    }
                }
            } else {
                parse(in);
            }
        }
    }

    // ========== Full Thread Dump metadata ==========

    private static final String DUMP_START = "Full thread dump";
    private static final String SMR_INFO_START = "Threads class SMR info:";
    private static final String LOCKED_SYNCHRONIZERS_START = "Locked ownable synchronizers";
    private static final String NO_COMPILE_TASK = "No compile task";
    private static final String JAVA_LANG_THREAD_STATE = "java.lang.Thread.State:";
    private static final String STACK_LOCATION = "\tat ";
    private static final String STACK_ANNOTATION = "\t- "; // annotation for lock status (locked, waiting, etc)

    private static final String METHOD_ROOT = "<root>"; // method that is considered to call every thread
    private static final String METHOD_THIS = "<this>"; // method name to profile CPU consumed by the method itself

    private static class ThreadState {
        public final String abbreviation;
        public final String state;
        public final String[] methods;

        ThreadState(String abbreviation, String state, String... methods) {
            this.abbreviation = abbreviation;
            this.state = state;
            this.methods = methods;
        }
    }

    private static final ThreadState[] THREAD_STATES = {
        // Actual thread states. Use 1 character names.
        new ThreadState("U", "unknown"),
        new ThreadState("R", "runnable"),
        new ThreadState("R", Thread.State.RUNNABLE.name()),
        new ThreadState("E", "waiting for monitor entry"),
        new ThreadState("E", Thread.State.BLOCKED.name()),
        new ThreadState("e", "waiting for lock entry"),
        new ThreadState("W", "in Object.wait()",
            "java.lang.Object.wait"), // native code for Object.wait() in Java 1.4
        new ThreadState("W", Thread.State.WAITING.name()),
        new ThreadState("w", "waiting on monitor"),
        new ThreadState("P", "parking",
            "sun.misc.Unsafe.park",
            "jdk.internal.misc.Unsafe.park"),
        new ThreadState("P", "waiting on VM lock",
            "java.util.concurrent.locks.LockSupport.park",
            "java.util.concurrent.locks.LockSupport.parkNanos",
            "java.util.concurrent.locks.LockSupport.parkUntil"), // Zing VM, also non-Unsafe parking
        new ThreadState("S", "sleeping", "java.lang.Thread.sleep"), // going to sleeping or awakening in Java 1.5
        new ThreadState("S", Thread.State.TIMED_WAITING.name()),
        new ThreadState("s", "waiting on condition"), // sleeping in Java 1.4
        new ThreadState("d", "suspended"),
        new ThreadState("N", Thread.State.NEW.name()),
        new ThreadState("T", Thread.State.TERMINATED.name()),
        // Terminating IO methods which block as 'runnable'. Use additional states with 2+ character names.
        new ThreadState("FR", "File read",
            "java.io.FileInputStream.read", "java.io.FileInputStream.readBytes",
            "java.io.RandomAccessFile.read", "java.io.RandomAccessFile.readBytes"),
        new ThreadState("FW", "File write",
            "java.io.FileOutputStream.write", "java.io.FileOutputStream.writeBytes",
            "java.io.RandomAccessFile.write", "java.io.RandomAccessFile.writeBytes"),
        new ThreadState("SA", "Socket accept", "java.net.PlainSocketImpl.socketAccept"),
        new ThreadState("SC", "Socket connect", "java.net.PlainSocketImpl.socketConnect"),
        new ThreadState("SR", "Socket read",
            "java.net.SocketInputStream.socketRead", "java.net.SocketInputStream.socketRead0"),
        new ThreadState("SW", "Socket write",
            "java.net.SocketOutputStream.socketWrite", "java.net.SocketOutputStream.socketWrite0"),
        new ThreadState("NA", "NIO accept",
            "sun.nio.ch.ServerSocketChannelImpl.accept0",
            "sun.nio.ch.Net.accept"),
        new ThreadState("NS", "NIO select",
            "sun.nio.ch.PollArrayWrapper.poll0",
            "sun.nio.ch.DevPollArrayWrapper.poll0",
            "sun.nio.ch.EPollArrayWrapper.epollWait",
            "sun.nio.ch.Net.poll"),
        new ThreadState("DP", "Datagram peek",
            "java.net.PlainDatagramSocketImpl.peek", "java.net.PlainDatagramSocketImpl.peekData"),
        new ThreadState("DR", "Datagram receive",
            "java.net.PlainDatagramSocketImpl.receive", "java.net.PlainDatagramSocketImpl.receive0",
            "java.net.TwoStacksPlainDatagramSocketImpl.receive0",
            "java.net.DualStackPlainDatagramSocketImpl.socketReceiveOrPeekData",
            "sun.nio.ch.DatagramChannelImpl.receive", "sun.nio.ch.DatagramChannelImpl.receive0"),
        new ThreadState("DS", "Datagram send",
            "java.net.PlainDatagramSocketImpl.send",
            "java.net.TwoStacksPlainDatagramSocketImpl.send",
            "java.net.DualStackPlainDatagramSocketImpl.socketSend",
            "sun.nio.ch.DatagramChannelImpl.send", "sun.nio.ch.DatagramChannelImpl.send0"),
    };
    private static final int WAITING_FOR_LOCK_ENTRY = findThreadState("waiting for lock entry", "", "");

    private static int findThreadState(String threadLine, String stateLine, String method) {
        for (int threadState = 0; threadState < THREAD_STATES.length; threadState++) {
            for (String m : THREAD_STATES[threadState].methods) {
                if (method.equals(m))
                    return threadState;
            }
        }
        int k = threadLine.indexOf('"', 1);
        for (int threadState = 0; threadState < THREAD_STATES.length; threadState++) {
            if (threadLine.indexOf(THREAD_STATES[threadState].state, k + 1) >= 0)
                return threadState;
        }
        if (stateLine.length() != 0) {
            k = stateLine.indexOf(JAVA_LANG_THREAD_STATE) + JAVA_LANG_THREAD_STATE.length();
            for (int threadState = 0; threadState < THREAD_STATES.length; threadState++) {
                if (stateLine.indexOf(THREAD_STATES[threadState].state, k + 1) >= 0)
                    return threadState;
            }
        }
        return 0;
    }

    private static class MethodStats {
        final String name;
        int totalCount;
        final int[] counts = new int[THREAD_STATES.length];
        IndexedSet<String, MethodStats> children; // created on first need

        MethodStats(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        IndexedSet<String, MethodStats> getChildren() {
            if (children == null)
                children = IndexedSet.create(MethodStats::getName);
            return children;
        }

        boolean contributes(int threadState, MethodStats outer, double thresholdPercent) {
            return counts[threadState] * 100.0 > outer.counts[threadState] * thresholdPercent;
        }

        boolean contributes(MethodStats outer, double thresholdPercent) {
            for (int i = 0; i < counts.length; i++) {
                if (contributes(i, outer, thresholdPercent))
                    return true;
            }
            return false;
        }
    }

    private static final Comparator<MethodStats> METHOD_STATS_BY_TOTAL_COUNT =
        (o1, o2) -> Long.compare(o2.totalCount, o1.totalCount);

    private static class Top implements Comparable<Top> {
        final MethodStats ms;
        final int count;

        Top(MethodStats ms, int count) {
            this.ms = ms;
            this.count = count;
        }

        @Override
        public int compareTo(Top other) {
            return other.count != count ? other.count - count : ms.name.compareTo(other.ms.name);
        }
    }

    private static final int TOP_METHODS = 30;

    // ========== Stats ==========

    private final IndexedSet<String, MethodStats> forwardStats = IndexedSet.create(MethodStats::getName);
    private final IndexedSet<String, MethodStats> reverseStats = IndexedSet.create(MethodStats::getName);
    private int dumpsCount;

    // ========== Parser internal state ==========

    private String line; // current line
    private final List<String> methods = new ArrayList<>(); // current stack track
    private final IndexedSet<String, String> cache = new IndexedSet<>(); // method names cache
    private final IndexedSet<String, String> seen = new IndexedSet<>(); // seen method (rec) in current stack trace
    private int threadState; // current thread state

    private void parse(BufferedReader in) throws IOException {
        while (line != null) {
            if (!line.startsWith(DUMP_START)) {
                line = in.readLine();
                continue;
            }
            // Full thread dump has started.
            dumpsCount++;
            line = in.readLine();
            while (line != null) { // Loop for all threads.
                while (line != null && line.isEmpty())
                    line = in.readLine();

                // Skip Safe Memory Reclamation (SMR) info.
                if (startsWith(line, SMR_INFO_START)) {
                    while (line != null && !line.trim().startsWith("}"))
                        line = in.readLine();
                    if (line != null)
                        line = in.readLine();
                }
                while (line != null && line.isEmpty())
                    line = in.readLine();

                // Skip synchronizers
                if (startsWith(line, LOCKED_SYNCHRONIZERS_START)) {
                    while (line != null && !line.trim().isEmpty())
                        line = in.readLine();
                }
                while (line != null && line.isEmpty())
                    line = in.readLine();

                // Break if line is not describing the next thread.
                if (line == null || line.charAt(0) != '"')
                    break;
                String threadLine = line;
                line = in.readLine();
                String stateLine = startsWith(line, JAVA_LANG_THREAD_STATE) ? line : "";
                if (stateLine.length() != 0) // Java 1.6 thread state info.
                    line = in.readLine();

                readStackTrace(in);
                // Skip methods with no stack trace
                if (startsWith(line, NO_COMPILE_TASK))
                    line = in.readLine();

                threadState = findThreadState(threadLine, stateLine, methods.get(0));
                // Collapse new "concurrent locks" stacktrace when lock in contended state (i.e. blocking wait).
                // The code below tries to remove classes from "java.util.concurrent.locks" and their ancestors
                // from the analysis, so that real application methods where the locks are acquired show up
                // in the result statistics
                if (isUnsafePark(methods.get(0)) && !locks.isSet()) {
                    int i = 1;
                    while (i < methods.size() && methods.get(i).startsWith("java.util.concurrent.locks."))
                        i++;
                    String m = methods.get(i - 1);
                    if (isWaitingForLockMethod1(m)) {
                        while (i < methods.size() && isWaitingForLockMethod2(methods.get(i))) {
                            i++;
                        }
                        threadState = WAITING_FOR_LOCK_ENTRY;
                        methods.subList(0, i).clear();
                    }
                }
                if (!matches(stateMatcher, THREAD_STATES[threadState].abbreviation))
                    continue;
                if (!matches(nameMatcher, threadLine.substring(1, threadLine.indexOf('"', 1))))
                    continue;
                updateStats();
            }
        }
    }

    private void readStackTrace(BufferedReader in) throws IOException {
        methods.clear();
        while (true) {
            while (line != null && (line.startsWith(STACK_ANNOTATION)))
                line = in.readLine();
            if (line == null || !line.startsWith(STACK_LOCATION) || line.indexOf('(') < 0)
                break;
            String methodName = line.substring(STACK_LOCATION.length(), line.indexOf('('));
            String cachedName = cache.getByKey(methodName);
            if (cachedName == null) {
                cache.add(methodName);
                cachedName = methodName;
            }
            methods.add(cachedName);
            line = in.readLine();
        }
        methods.add(METHOD_ROOT);
    }

    private void updateStats() {
        // collect reverse stack-trace stats from <this> (method at the top)
        updateStatsAt(reverseStats, 0);
        // reverse list to get forward stats
        Collections.reverse(methods);
        methods.add(METHOD_THIS); // add this to the end of forward stats
        // collect forward stats from each method, but this, keep track of recursion
        seen.clear();
        for (int i = methods.size() - 2; i >= 0; i--) {
            if (seen.add(methods.get(i)))
                updateStatsAt(forwardStats, i);
        }
    }

    private void updateStatsAt(IndexedSet<String, MethodStats> set, int i) {
        while (true) {
            MethodStats ms = inc(set, methods.get(i));
            if (++i >= methods.size())
                break;
            set = ms.getChildren();
        }
    }

    private MethodStats inc(IndexedSet<String, MethodStats> set, String name) {
        MethodStats ms = set.getByKey(name);
        if (ms == null)
            set.add(ms = new MethodStats(name));
        ms.totalCount++;
        ms.counts[threadState]++;
        return ms;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean matches(Matcher matcher, String value) {
        return (matcher == null || matcher.reset(value).find());
    }

    private static boolean startsWith(String line, String pattern) {
        return (line != null && line.trim().startsWith(pattern));
    }

    private static boolean isUnsafePark(String method) {
        return (method.equals("sun.misc.Unsafe.park") || method.equals("jdk.internal.misc.Unsafe.park"));
    }

    private static boolean isWaitingForLockMethod1(String method) {
        return method.startsWith("java.util.concurrent.locks.") &&
            (method.endsWith(".lock") || method.endsWith(".lockInterruptibly") || method.endsWith(".tryLock")) ||
            method.startsWith("java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire") ||
            method.startsWith("java.util.concurrent.locks.AbstractQueuedSynchronizer.tryAcquire");
    }

    private static boolean isWaitingForLockMethod2(String method) {
        return method.endsWith(".lock") || method.endsWith(".lockInterruptibly") || method.endsWith(".tryLock") ||
            method.endsWith(".acquire") || method.endsWith(".tryAcquire");
    }

    private void dumpToFile(String file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file), 100000))) {
            out.println(HEADER + new Date());
            out.println(forwardStats.size() + " method(s) have been collected in " + dumpsCount + " dump(s)");
            dumpReverseTops(out);
            dumpForwardMethods(out);
        }
    }

    private void dumpReverseTops(PrintWriter out) {
        for (int threadState = 0; threadState < THREAD_STATES.length; threadState++) {
            dumpReverseTopsForState(out, threadState);
        }
    }

    private void dumpReverseTopsForState(PrintWriter out, int threadState) {
        PriorityQueue<Top> tops = new PriorityQueue<>();
        for (MethodStats ms : reverseStats) {
            int count = ms.counts[threadState];
            if (count != 0)
                tops.add(new Top(ms, count));
        }
        if (tops.isEmpty())
            return;
        out.println();
        out.println("----------------------------------------------------------------");
        out.println("Top " + TOP_METHODS + " methods with " + METHOD_THIS + " in '" +
            THREAD_STATES[threadState].state + "' state and reverse call stacks");
        int n = Math.min(tops.size(), TOP_METHODS);
        for (int i = 0; i < n; i++) {
            Top top = tops.poll();
            dumpTop(out, "", top, threadState);
            dumpTopDepth(out, "  ", top.ms, threadState, top.ms);
        }
    }

    private void dumpTopDepth(PrintWriter out, String indent, MethodStats method, int threadState,
        MethodStats outer)
    {
        if ((indent.length() >> 1) > topdepth.getValue())
            return;
        if (method.children == null)
            return;
        PriorityQueue<Top> tops = new PriorityQueue<>();
        int skipped = 0;
        for (MethodStats ms : method.children) {
            if (ms.counts[threadState] != 0) {
                // Filter by contribution
                if (ms.contributes(threadState, outer, threshold.getValue())) {
                    tops.add(new Top(ms, ms.counts[threadState]));
                } else {
                    skipped++;
                }
            }
        }
        int n = tops.size();
        for (int i = 0; i < n; i++) {
            Top top = tops.poll();
            dumpTop(out, indent, top, threadState);
            MethodStats ms = method.children.getByKey(top.ms.name);
            dumpTopDepth(out, indent + (i < n - 1 || skipped > 0 ? "| " : "  "), ms, threadState, outer);
        }
        if (skipped > 0)
            dumpSkipped(out, indent, skipped);
    }

    private void dumpTop(PrintWriter out, String indent, Top top, int threadState) {
        out.print(indent);
        out.print(THREAD_STATES[threadState].abbreviation);
        out.print(":");
        dumpCount(out, top.count);
        out.print(" - ");
        out.print(top.ms.name);
        out.println();
    }

    private void dumpForwardMethods(PrintWriter out) {
        out.println();
        out.println("================================================================");
        out.println("All methods with submethods and forward call stacks");
        MethodStats[] sorted = sort(forwardStats, METHOD_STATS_BY_TOTAL_COUNT);
        for (MethodStats ms : sorted) {
            out.println();
            dumpMethod(out, "", "+ ", ms);
            dumpMethodDepth(out, "  ", ms, ms);
        }
    }

    private void dumpMethodDepth(PrintWriter out, String indent, MethodStats method, MethodStats outer) {
        if ((indent.length() >> 1) > methoddepth.getValue())
            return;
        if (method.children == null)
            return;
        MethodStats[] sorted = sort(method.children, METHOD_STATS_BY_TOTAL_COUNT);
        // Filter by contribution
        int skipped = 0;
        int n = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (sorted[i].contributes(outer, threshold.getValue())) {
                sorted[n++] = sorted[i];
            } else {
                skipped++;
            }
        }
        for (int i = 0; i < n; i++) {
            MethodStats ms = sorted[i];
            dumpMethod(out, indent, "- ", ms);
            dumpMethodDepth(out, indent + (i < n - 1 || skipped > 0 ? "| " : "  "), ms, outer);
        }
        if (skipped > 0)
            dumpSkipped(out, indent, skipped);
    }

    private void dumpMethod(PrintWriter out, String indent, String prefix, MethodStats method) {
        out.print(indent);
        out.print(prefix);
        out.print(method.name);
        int states = 0;
        for (int count : method.counts) {
            if (count != 0)
                states++;
        }
        if (states > 1) {
            out.print(" - ");
            dumpCount(out, method.totalCount);
        }
        out.print(" =");
        for (int threadState = 0; threadState < THREAD_STATES.length; threadState++) {
            if (method.counts[threadState] != 0) {
                out.print(" ");
                out.print(THREAD_STATES[threadState].abbreviation);
                out.print(":");
                dumpCount(out, method.counts[threadState]);
            }
        }
        out.println();
    }

    private void dumpSkipped(PrintWriter out, String indent, int skipped) {
        out.print(indent);
        out.print("... ");
        out.print(skipped);
        out.print(" more below threshold");
        out.println();
    }

    private void dumpCount(PrintWriter out, int count) {
        out.printf(Locale.US, "%d (%.2f%%)", count, 100.0 * count / dumpsCount);
    }

    private static MethodStats[] sort(Collection<MethodStats> c, Comparator<MethodStats> comparator) {
        MethodStats[] a = c.toArray(new MethodStats[0]);
        Arrays.sort(a, comparator);
        return a;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(TDP.class, args);
    }
}
