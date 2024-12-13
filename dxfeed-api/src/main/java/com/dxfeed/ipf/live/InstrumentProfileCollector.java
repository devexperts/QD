/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.live;

import com.devexperts.logging.Logging;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileType;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Collects instrument profile updates and provides the live list of instrument profiles.
 * This class contains a map that keeps a unique instrument profile per symbol.
 * This class is intended to be used with {@link InstrumentProfileConnection} as a repository that keeps
 * profiles of all known instruments. See {@link InstrumentProfileConnection} for a usage example.
 *
 * <p>As set of instrument profiles stored in this collector can be accessed with {@link #view() view} method.
 * A snapshot plus a live stream of updates can be accessed with
 * {@link #addUpdateListener(InstrumentProfileUpdateListener) addUpdateListener} method.
 *
 * <p>Removal of instrument profile is represented by an {@link InstrumentProfile} instance with a
 * {@link InstrumentProfile#getType() type} equal to
 * <code>{@link InstrumentProfileType InstrumentProfileType}.{@link InstrumentProfileType#REMOVED REMOVED}.{@link InstrumentProfileType#name() name}()</code>.
 *
 * <p><b>This class is thread-safe.</b>
 */
public class InstrumentProfileCollector {
    private static final Logging log = Logging.getLogging(InstrumentProfileCollector.class);

    // =====================  private instance fields =====================

    // Invariant: For all entries in the set entry.old == false
    private final IndexedSet<String, Entry> entriesBySymbol =
        IndexedSet.create((IndexerFunction<String, Entry>) entry -> entry.symbol); // sync
    private final CopyOnWriteArrayList<Agent> agents = new CopyOnWriteArrayList<>(); // sync
    private volatile Executor executor = createAgentExecutor();
    private volatile Node tail = new Node(); // not-null, write sync

    private long lastUpdateTime = System.currentTimeMillis();
    private long lastReportedTime;

    // =====================  public instance methods =====================

    /**
     * Returns last modification time (in milliseconds) of instrument profiles or zero if it is unknown.
     * Note, that while the time is represented in milliseconds, the actual granularity of time here is a second.
     * @return last modification time (in milliseconds) of instrument profiles or zero if it is unknown.
     */
    public final synchronized long getLastUpdateTime() {
        return lastReportedTime = lastUpdateTime;
    }

    /**
     * Convenience method to update one instrument profile in this collector. This is a shortcut for:
     * <pre><tt>
     *    {@link #updateInstrumentProfiles updateInstrumentProfiles}(Collections.singletonList(ip), null);
     * </tt></pre>
     *
     * <p>Note, that this method stores reference to an instance of a given {@link InstrumentProfile} object
     * inside this collector, unless a protected method
     * {@link #copyInstrumentProfile(InstrumentProfile) copyInstrumentProfile}
     * is overridden to create a copy.
     *
     * @param ip instrument profile.
     */
    public final void updateInstrumentProfile(InstrumentProfile ip) {
        boolean updated = false;
        synchronized (this) {
            if (updateInstrumentProfileImpl(ip, null)) {
                updateTimeImpl();
                updated = true;
            }
        }
        if (updated)
            notifyAgents();
    }

    /**
     * Updates a list of instrument profile and assign them a generation tag.
     * A generation tag can be later use with {@link #removeGenerations(Set) removeGenerations} method.
     *
     * <p>Removal of instrument profile is represented by an {@link InstrumentProfile} instance with a
     * {@link InstrumentProfile#getType() type} equal to
     * <code>{@link InstrumentProfileType InstrumentProfileType}.{@link InstrumentProfileType#REMOVED REMOVED}.{@link InstrumentProfileType#name() name}()</code>.
     *
     * <p>Note, that this method stores references to instances of {@link InstrumentProfile} objects from
     * a given list inside this collector, unless a protected method
     * {@link #copyInstrumentProfile(InstrumentProfile) copyInstrumentProfile}
     * is overridden to create a copy.
     *
     * @param ips a list of instrument profiles.
     * @param generation a generation tag, may be {@code null}.
     */
    public final void updateInstrumentProfiles(List<InstrumentProfile> ips, Object generation) {
        if (ips.isEmpty())
            return;
        boolean updated = false;
        synchronized (this) {
            for (InstrumentProfile ip : ips)
                if (updateInstrumentProfileImpl(ip, generation))
                    updated = true;
            if (updated)
                updateTimeImpl();
        }
        if (updated)
            notifyAgents();
    }

    /**
     * Removes instrument profiles with non-null generation tags from a given set.
     * Note, that this method takes O(N) time, where N is the number of stored instrument profiles in this collector.
     * It shall be used sparingly.
     *
     * @param generations a set of generation tags.
     */
    public final void removeGenerations(Set<Object> generations) {
        if (Objects.requireNonNull(generations, "generations").isEmpty())
            return; // nothing to do
        boolean removed = false;
        synchronized (this) {
            for (Iterator<Entry> it = entriesBySymbol.iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                if (entry.generation != null && generations.contains(entry.generation)) {
                    // [0] remove from snapshot
                    it.remove();
                    // the rest of removal
                    removeEntryImpl(entry);
                    removed = true;
                }
            }
            if (removed)
                updateTimeImpl();
        }
        if (removed)
            notifyAgents();
    }

    /**
     * Returns a concurrent view of the set of instrument profiles.
     * Note, that removal of instrument profile is represented by an {@link InstrumentProfile} instance with a
     * {@link InstrumentProfile#getType() type} equal to
     * <code>{@link InstrumentProfileType InstrumentProfileType}.{@link InstrumentProfileType#REMOVED REMOVED}.{@link InstrumentProfileType#name() name}()</code>.
     * Normally, this view exposes only non-removed profiles. However, if iteration is concurrent with removal,
     * then a removed instrument profile (with a removed type) can be exposed by this view.
     *
     * @return a concurrent view of the set of instrument profiles.
     */
    public final Iterable<InstrumentProfile> view() {
        return () -> new Iterator<InstrumentProfile>() {
            private final Iterator<Entry> entryIterator = entriesBySymbol.concurrentIterator();

            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public InstrumentProfile next() {
                return entryIterator.next().ip;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns executor for processing instrument profile update notifications.
     * By default, single-threaded executor is used to process updates asynchronously.
     *
     * @return executor for processing instrument profile update notifications.
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Changes executor for processing instrument profile update notifications.
     *
     * @param executor non-null executor for processing instrument profile update notifications.
     */
    public void setExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Adds listener that is notified about any updates in the set of instrument profiles.
     * If a set of instrument profiles is not empty, then this listener will be immediately
     * {@link InstrumentProfileUpdateListener#instrumentProfilesUpdated(Iterator) notified}.
     *
     * @param listener profile update listener.
     */
    public final void addUpdateListener(InstrumentProfileUpdateListener listener) {
        Agent agent = new Agent(Objects.requireNonNull(listener, "listener"));
        agents.add(agent);
        executor.execute(agent::update);
    }

    /**
     * Removes listener that is notified about any updates in the set of instrument profiles.
     *
     * @param listener profile update listener.
     */
    public final void removeUpdateListener(InstrumentProfileUpdateListener listener) {
        for (Agent agent : agents)
            if (agent.listener == listener) {
                agents.remove(agent);
                return;
            }
    }

    // =====================  protected instance methods =====================

    /**
     * A hook to create a defensive copy of the instrument profile that is saved into
     * this collector. This method is invoked from inside of
     * {@link #updateInstrumentProfile(InstrumentProfile) updateInstrumentProfile} and
     * {@link #updateInstrumentProfiles(List, Object) updateInstrumentProfiles} methods
     * when a decision is made to save an instrument profile into this data
     * structure.
     *
     * <p>This implementation returns the original instrument reference.
     * Overriding methods may choose to create and return a copy of the instrument
     * profile object instead.
     *
     * <p>Override this method to safely reuse {@link InstrumentProfile} instances that are
     * passed into this collector. This lazily creates their defensive copies,
     * just when the corresponding instrument profile is detected to have been changed.</p>
     *
     * @param ip the original instrument profile.
     * @return the original or copied instrument profile.
     */
    protected InstrumentProfile copyInstrumentProfile(InstrumentProfile ip) {
        return ip;
    }

    // =====================  private instance methods =====================

    // SYNC(this) required
    private void updateTimeImpl() {
        lastUpdateTime = Math.max(System.currentTimeMillis(), lastReportedTime + 1000);
    }

    private void notifyAgents() {
        Executor executor = this.executor; // Atomic read
        for (Agent agent : agents) {
            executor.execute(agent::update);
        }
    }

    // SYNC(this) required
    private boolean updateInstrumentProfileImpl(InstrumentProfile ip, Object generation) {
        String symbol = Objects.requireNonNull(ip, "ip").getSymbol();
        Entry oldEntry = entriesBySymbol.getByKey(symbol);
        if (oldEntry != null && oldEntry.ip != ip && oldEntry.ip.equals(ip)) {
            // different instance of the same instrument -- just update the generation (no need to notify)
            oldEntry.generation = generation;
            return false;
        }
        // CONCURRENCY NOTE: The order of numbered operations is important
        // [0] Create a copy of the instrument (if copyInstrumentProfile is overridden in a subclass)
        //     This captures this instrument profile state in case of its concurrent modifications
        ip = copyInstrumentProfile(ip);
        Entry newEntry = new Entry(symbol, ip);
        newEntry.generation = generation;
        if (ip.getType().equals(InstrumentProfileType.REMOVED.name())) {
            // removing instrument
            if (oldEntry == null) {
                return false; // it was not present before, so there's nothing else to do
            } else {
                // [1a] Remove entry from snapshot
                entriesBySymbol.remove(oldEntry);
            }
        } else {
            // [1b] Add/update in snapshot (keep only non-removed ones in a snapshot)
            entriesBySymbol.put(newEntry);
        }
        // [2] link to update list (linearization for publishing of new instrument)
        linkUpdatedEntryImpl(newEntry);
        if (oldEntry != null) {
            // [3] mark old entry (linearization for removal of old instrument profile instance)
            oldEntry.old = true;
        }
        return true;
    }

    private void removeEntryImpl(Entry entry) {
        // CONCURRENCY NOTE: The order of numbered operations is important
        // create new removed instrument
        InstrumentProfile removed = new InstrumentProfile();
        removed.setSymbol(entry.symbol);
        removed.setType(InstrumentProfileType.REMOVED.name());
        // [1] link new entry to update list with this removed instrument
        linkUpdatedEntryImpl(new Entry(entry.symbol, removed));
        // [2] mark old entry
        entry.old = true;
    }

    private void linkUpdatedEntryImpl(Entry entry) {
        Node node = new Node(entry);
        Node oldTail = tail;
        oldTail.next = node; // new entry linearized in the list here
        tail = node;
    }

    private String debugString(InstrumentProfile ip) {
        return ip + " @" + Integer.toHexString(System.identityHashCode(ip));
    }

    // =====================  private inner classes and methods =====================

    private static class Entry {
        final String symbol;
        final InstrumentProfile ip;
        Object generation; // sync
        volatile boolean old; // true if this entry was replaced with a fresh, new one

        Entry(String symbol, InstrumentProfile ip) {
            this.symbol = symbol;
            this.ip = ip;
        }
    }

    private static class Node {
        final Entry entry;
        volatile Node next;

        // Dummy initial tail node constructor
        Node() {
            this.entry = new Entry(null, null);
            this.entry.old = true;
        }

        Node(Entry entry) {
            this.entry = entry;
        }
    }

    private class Agent implements Iterator<InstrumentProfile> {
        final InstrumentProfileUpdateListener listener;
        Entry nextEntry;
        Node tailNode;
        Iterator<Entry> snapshotIterator;

        Agent(InstrumentProfileUpdateListener listener) {
            this.listener = listener;
            // CONCURRENCY NOTE: The order of numbered operations is important
            // [1] capture current tail
            tailNode = tail;
            // [2] capture current snapshot
            snapshotIterator = entriesBySymbol.concurrentIterator();
        }

        // SYNC(this)
        private void ensureNext() {
            if (nextEntry != null)
                return;
            if (snapshotIterator != null) {
                if (snapshotIterator.hasNext()) {
                    nextEntry = snapshotIterator.next();
                    return;
                } else {
                    snapshotIterator = null;
                }
            }
            while (true) {
                Node node = tailNode.next;
                if (node == null)
                    return; // nothing new -- return
                tailNode = node; // move to next
                if (!node.entry.old) {
                    nextEntry = node.entry;
                    return;
                }
                // loop to skip removed
            }
        }

        private void update() {
            try {
                if (hasNext()) {
                    listener.instrumentProfilesUpdated(this);
                }
            } catch (Throwable t) {
                log.error("Exception in InstrumentProfileUpdateListener", t);
            }
        }

        @Override
        public synchronized boolean hasNext() {
            ensureNext();
            return nextEntry != null;
        }

        @Override
        public synchronized InstrumentProfile next() {
            ensureNext();
            if (nextEntry == null)
                throw new NoSuchElementException();
            InstrumentProfile result = nextEntry.ip;
            nextEntry = null;
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    protected static Executor createAgentExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "IPC-Executor");
            t.setDaemon(true);
            return t;
        });
    }
}
