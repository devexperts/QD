/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.live;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.devexperts.logging.Logging;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileType;

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

    static {
        log.configureDebugEnabled(false);
    }

    // =====================  private instance fields =====================

    private final IndexedSet<String, Entry> entriesBySymbol = IndexedSet.create((IndexerFunction<String, Entry>) entry -> entry.symbol); // sync
    private final CopyOnWriteArrayList<Agent> agents = new CopyOnWriteArrayList<>(); // sync
    private Entry tail = new Entry(); // not-null, sync

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
     * is overriden to create a copy.
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
     * is overriden to create a copy.
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
        if (generations == null)
            throw new NullPointerException();
        if (generations.isEmpty())
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
                    if (log.debugEnabled())
                        log.debug("Removing " + debugString(entry.ip));
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
        return new Iterable<InstrumentProfile>() {
            @Override
            public Iterator<InstrumentProfile> iterator() {
                return new Iterator<InstrumentProfile>() {
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
        };
    }

    /**
     * Adds listener that is notified about any updates in the set of instrument profiles.
     * If a set of instrument profiles is not empty, then this listener is immediately
     * {@link InstrumentProfileUpdateListener#instrumentProfilesUpdated(Iterator) notified}
     * right from inside this add method.
     *
     * @param listener profile update listener.
     */
    public final void addUpdateListener(InstrumentProfileUpdateListener listener) {
        if (listener == null)
            throw new NullPointerException("null listener");
        Agent agent = new Agent(listener);
        agents.add(agent);
        agent.update();
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
        for (Agent agent : agents)
            agent.update();
    }

    // SYNC(this) required
    private boolean updateInstrumentProfileImpl(InstrumentProfile ip, Object generation) {
        if (ip == null)
            throw new NullPointerException("null instrument");
        String symbol = ip.getSymbol();
        Entry oldEntry = entriesBySymbol.getByKey(symbol);
        if (oldEntry != null && oldEntry.ip != ip && oldEntry.ip.equals(ip)) {
            // different instance of the same instrument -- just update the generation (no need to notify)
            oldEntry.generation = generation;
            return false;
        }
        if (log.debugEnabled())
            log.debug((oldEntry == null ? "Adding " : "Updating ") + debugString(ip) +
                (oldEntry != null && oldEntry.ip == ip ? " (same instance)" : ""));
        // CONCURRENCY NOTE: The order of numbered operations is important
        // [0] Create a copy of the instrument (if copyInstrumentProfile is overriden is a subclass)
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
            unlinkRemovedEntryImpl(oldEntry);
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
        unlinkRemovedEntryImpl(entry);
    }

    private void linkUpdatedEntryImpl(Entry entry) {
        Entry oldTail = tail;
        oldTail.next = entry; // new entry linearized in the list here
        entry.prev = oldTail;
        tail = entry;
        // if old tail was removed, it needs to be unlinked
        if (oldTail.old)
            unlinkRemovedEntryImpl(oldTail);
    }

    private void unlinkRemovedEntryImpl(Entry entry) {
        Entry next = entry.next;
        if (next == null)
            return; // never unlink tail -- need to preserve an update chain
        Entry prev = entry.prev;
        if (prev == null)
            return; // already unlinked (also true for dummy head)
        prev.next = next;
        next.prev = prev;
        entry.prev = null; // this marks entry as unlinked, just in case
    }

    private String debugString(InstrumentProfile ip) {
        return ip + " @" + Integer.toHexString(System.identityHashCode(ip));
    }

    // =====================  private inner classes methods =====================

    private static class Entry {
        final String symbol;
        final InstrumentProfile ip;
        Object generation; // sync
        Entry prev; // sync
        volatile Entry next;
        volatile boolean old; // true if this entry was replaced with a fresh, new one

        // dummy initial tail entry constructor
        Entry() {
            this(null, null);
            old = true;
        }

        Entry(String symbol, InstrumentProfile ip) {
            this.symbol = symbol;
            this.ip = ip;
        }
    }

    private class Agent implements Iterator<InstrumentProfile> {
        final InstrumentProfileUpdateListener listener;
        Entry nextEntry;
        Entry tailEntry;
        Iterator<Entry> snapshotIterator;

        Agent(InstrumentProfileUpdateListener listener) {
            this.listener = listener;
            // CONCURRENCY NOTE: The order of numbered operations is important
            // [1] capture current tail
            tailEntry = tail;
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
                } else
                    snapshotIterator = null;
            }
            while (true) {
                Entry entry = tailEntry.next;
                if (entry == null)
                    return; // nothing new -- return
                tailEntry = entry; // move to next
                if (!entry.old) {
                    nextEntry = entry;
                    return;
                }
                // loop to skip removed
            }
        }

        void update() {
            if (hasNext())
                listener.instrumentProfilesUpdated(this);
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
}


