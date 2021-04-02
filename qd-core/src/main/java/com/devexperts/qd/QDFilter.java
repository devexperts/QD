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
package com.devexperts.qd;

import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.NotFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordFilter;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.util.SymbolSet;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.concurrent.GuardedBy;

/**
 * Filter for QD data and subscription. This class subsumes {@link SubscriptionFilter} and should be
 * preferably used instead of it. Unlike {@link SubscriptionFilter} convention to use null when
 * there is no filter (anything is accepted), this class usage convention is to never use null.
 * The constant {@link #ANYTHING} is provided for this purpose.
 *
 * <h3>Threads</h3>
 *
 * <b>This class is thread-safe.</b> Decisions made by its {@link #accept(QDContract, DataRecord, int, String) accept}
 * method do not change over time.
 */
public abstract class QDFilter implements SubscriptionFilter, StableSubscriptionFilter, RecordFilter {
    /**
     * Filter that accepts everything.
     */
    public static final QDFilter ANYTHING = new Constant(true);

    /**
     * Filter that does not accept anything.
     */
    public static final QDFilter NOTHING = new Constant(false);

    public enum Kind {
        // these must be first two  (for CompositeFilters ordering)
        ANYTHING,
        NOTHING,
        // this must be next (for CompositeFilters ordering)
        RECORD_ONLY,
        // "isSymbolOnly" filters must be next (for CompositeFilters ordering)
        SYMBOL_SET,
        SYMBOL_SET_WITH_ATTRIBUTES,
        PATTERN,
        OTHER_SYMBOL_ONLY,
        // the order of the rest is not important
        OTHER;

        /**
         * Returns {@code true} for filters that depend only on the the record.
         * The filter of this kind must work even when contract and symbol are {@code null}
         * in {@link QDFilter#accept(QDContract, DataRecord, int, String) QDFilter.accept(...)} invocation.
         */
        public boolean isRecordOnly() {
            return this == RECORD_ONLY;
        }

        /**
         * Returns {@code true} for filters that depend only on the the symbol.
         * The filter of this kind must work even when contract and record are {@code null}
         * in {@link QDFilter#accept(QDContract, DataRecord, int, String) QDFilter.accept(...)} invocation.
         */
        public boolean isSymbolOnly() {
            return this == SYMBOL_SET || this == SYMBOL_SET_WITH_ATTRIBUTES ||
                    this == PATTERN || this == OTHER_SYMBOL_ONLY;
        }
    }

    public enum SyntaxPrecedence { OR, AND, TOKEN }

    private final DataScheme scheme;
    private String name;

    @GuardedBy("updated")
    private ArrayList<UpdateListener> listeners; // always null for non-dynamic filters, init on demand, clear on fire

    // Updated is shared across all descendants of this filter
    private final Updated updated;

    protected QDFilter(DataScheme scheme) {
        this(scheme, null);
    }

    /**
     * Use this constructor to create an updated instance of dynamic filter.
     * It works just like the ordinary constructor when source is null.
     * Scheme must match with the source scheme (if specified).
     */
    protected QDFilter(DataScheme scheme, QDFilter source) {
        if (source != null && source.scheme != scheme)
            throw new IllegalArgumentException("Scheme must match in an updated instance of dynamic filter");

        this.scheme = scheme;
        this.updated = (source != null) ? source.updated : new Updated(this);
    }

    /**
     * Returns scheme that this filter works for.
     * Returns null if this filter works for any scheme.
     */
    public final DataScheme getScheme() {
        return scheme;
    }

    /**
     * Returns a kind of this filter that constrains and defines its overall behaviour.
     */
    public Kind getKind() {
        return Kind.OTHER;
    }

    /**
     * Returns a set of symbols that corresponds to this filter.
     * It is {@code null} when this filter does not have a symbol set.
     */
    public SymbolSet getSymbolSet() {
        return null;
    }

    /**
     * Returns true if this filter accepts a given record and symbol on the specified contract.
     * @param contract The specified contract. Can be null when contract is unknown/unspecified/any.
     * @param record The record.
     * @param cipher The cipher.
     * @param symbol The symbol.
     */
    public abstract boolean accept(QDContract contract, DataRecord record, int cipher, String symbol);

    /**
     * Returns a string representation of this filter.
     * This string representation should be parseable into this filter via {@link QDFilterFactory}.
     * To parse this string representation back into the instance of this class use
     * {@link CompositeFilters#valueOf(String, DataScheme)}.
     *
     * <p><b>Do not override this method.</b> Override {@link #getDefaultName()} instead.
     */
    public String toString() {
        if (name != null)
            return name;
        String defaultName = getDefaultName();
        if (defaultName == null)
            throw new UnsupportedOperationException("Filter name is not defined");
        setName(defaultName);
        return name;
    }

    public SyntaxPrecedence getSyntaxPrecedence() {
        return SyntaxPrecedence.TOKEN;
    }

    /**
     * Sets a custom string representation for this filter.
     * This method takes effect at most once.
     * The name is returned by {@link #toString()} method.
     * It can be an arbitrarily complex expression, but this string representation should be parseable into this filter via {@link QDFilterFactory}.
     * To parse this string representation back into the instance of this class use
     * {@link CompositeFilters#valueOf(String, DataScheme)}.
     *
     * @param name a custom string representation for this filter.
     */
    public synchronized void setName(String name) {
        if (this.name == null)
            this.name = name;
    }

    /**
     * Sets a short-name string representation for this filter.
     * This method {@link #checkShortName(String) checks}
     * validity of the short name (must contain only lower-case English letters) and sets the name
     * just like {@link #setName(String)}, but with the difference that {@link #hasShortName()} will return {@code true}.
     * Complex filter transformations (logical operations) try to retain this name.
     *
     * @param name a short-name string representation for this filter.
     */
    public void setShortName(String name) {
        checkShortName(name);
        setName(name);
    }

    /**
     * Returns {@code true} if this filter has project-specified short name of lower-case English letters.
     * Complex filter transformations (logical operations) try to retain this name.
     */
    public boolean hasShortName() {
        return isShortName(name);
    }

    /**
     * Sets a custom string representation for this filter if its no longer than a default one.
     * This method takes effect at most once.
     * This string representation should be parseable into this filter via {@link QDFilterFactory}.
     * To parse this string representation back into the instance of this class use
     * {@link CompositeFilters#valueOf(String, DataScheme)}.
     *
     * @param name a custom string representation for this filter.
     */
    public void setNameOrDefault(String name) {
        String defaultName = getDefaultName();
        // Note: when name.length == defaultName.length, then name is preferred
        // this way, the list of symbols specified on command like "A,B,C,D" does not flip due to random hash
        setName(defaultName != null && defaultName.length() < name.length() ? defaultName : name);
    }

    /**
     * Composes default string representation of the filter.
     * This string representation should be parseable into this filter via {@link QDFilterFactory}.
     * To parse this string representation back into the instance of this class use
     * {@link CompositeFilters#valueOf(String, DataScheme)}.
     *
     * <p><b>This method must be overriden in custom {@link QDFilter} implementations</b>.
     * This implementation returns {@code null}.
     *
     * @return  default string representation of the filter.
     */
    public String getDefaultName() {
        return null;
    }

    /**
     * This method is needed only to implement legacy {@link SubscriptionFilter} interface.
     * @deprecated Use {@link #accept(QDContract, DataRecord, int, String)}
     * to filter taking contract into account.
     */
    @Override
    public final boolean acceptRecord(DataRecord record, int cipher, String symbol) {
        return accept(null, record, cipher, symbol);
    }

    @Override
    public final boolean accept(RecordCursor cur) {
        return acceptRecord(cur.getRecord(), cur.getCipher(), cur.getSymbol());
    }

    /**
     * Returns negation of this filter. This implementation returns
     * <code>new {@link NotFilter NotFilter}(this)</code>.
     */
    public QDFilter negate() {
        return new NotFilter(this);
    }

    /**
     * Returns a stable filter that is the same or more encompassing as this filter.
     * It is always safe to return {@link #ANYTHING}, which means that this filter is not stable
     * (it is <b>dynamic</b>) and the only stable extension of it constitutes everything.
     * Stable filters must return {@code this} as a result of this method. The result of this method
     * satisfies the following constrains:
     * <ol>
     * <li>Result must be stable filter, that {@code result.toStableFilter() == result}
     * <li>Result must be more encompassing, that is {@code this.accept(...)}
     *     implies {@code result.accept(...)}.
     * <li>Result must be reconstructible from string, that is
     *     {@code result.toString()} must parse back to the same filter via data scheme's
     *     {@link SubscriptionFilterFactory}.
     * </ol>
     *
     * <p>This method shall never return null.</p>
     */
    @Override
    public QDFilter toStableFilter() {
        return ANYTHING;
    }

    /**
     * Returns {@code true} if this filter is <b>stable</b>, that is the decision of its
     * {@link #accept(QDContract, DataRecord, int, String) accept} method never changes for a given set of
     * arguments even from restart to restart and from one JVM to another.
     * Returns false if filter is <b>dynamic</b>.
     *
     * <p>This implementation returns {@code true} when {@code toStableFilter() == this}.
     * All overriding implementations have equivalent behavior.
     * @see #toStableFilter()
     */
    public boolean isStable() {
        return toStableFilter() == this;
    }

    /**
     * Returns {@code true} if this filter is <b>fast</b>, that is it can be quickly checked without blocking.
     * There filters are checked under the collector's global lock when processing subscription messages.
     * On the contrast, slow filters are checked outside of global lock when processing subscription messages.
     *
     * <p>This implementation returns {@code false} and is designed for override.
     */
    public boolean isFast() {
        return false;
    }

    /**
     * Unwraps filter that wraps another filter for performance reasons only (functionally identical to its delegate).
     *
     * <p>This implementation returns {@code this} and is designed for override in wrapping filter implelentations.
     *
     * @return Delegate of this filter.
     */
    public QDFilter unwrap() {
        return this;
    }

    /**
     * Returns {@code true} if this filter is <b>dynamic</b>, that is it can update and send the corresponding
     * notification. Use {@link #getUpdatedFilter()} to get the most recent instance of the dynamic filter.
     *
     * <p>This implementation returns {@code false} and is designed for override.
     */
    public boolean isDynamic() {
        return false;
    }

    /**
     * Returns an object that tracks the most-recent version of the {@link #isDynamic() dynamic} filter.
     * @return an object that tracks the most-recent version of the {@link #isDynamic() dynamic} filter.
     */
    public Updated getUpdated() {
        return updated;
    }

    /**
     * Returns new value of this filter if this is a {@link #isDynamic() dynamic} filter that has updated.
     * Returns {@code this} if this filter has not updated. This method always returns {@code this} when this filter
     * is not dynamic. This method returns the most recent new filter in series of updates that has happened so far.
     * Use {@link #addUpdateListener(UpdateListener) addUpdateListener} method to get notification on updates.
     */
    public QDFilter getUpdatedFilter() {
        return updated.filter;
    }

    /**
     * Adds listener for {@link #isDynamic() dynamic} filter. When this filter
     * is not dynamic this method does nothing. The listener's
     * {@link UpdateListener#filterUpdated(QDFilter) filterUpdated} method is invoked
     * at most once during lifetime of a filter on it update. If filter
     * was already updated when this method was invoked, then listener
     * is invoked immediately. You don't have to {@link #removeUpdateListener remove}
     * invoked listeners, as they are removed automatically when fired.
     *
     * <p>Use <code>{@link #getUpdated() getUpdated}().{@link Updated#addUpdateListener(UpdateListener) addUpdateListener}(listener)</code>
     * to get notification every time when listener updates (as opposed to just once).
     */
    public final void addUpdateListener(UpdateListener listener) {
        if (addUpdateListenerNoFireImpl(listener))
            listener.filterUpdated(this);
    }

    private boolean addUpdateListenerNoFireImpl(UpdateListener listener) {
        if (!isDynamic())
            return false;
        synchronized (updated) {
            boolean fireImmediately = getUpdatedFilter() != this;
            // don't need add the filter that we'll immediately fire
            if (!fireImmediately) {
                if (listeners == null)
                    listeners = new ArrayList<>();
                boolean wasEmpty = listeners.isEmpty();
                listeners.add(listener);
                if (wasEmpty)
                    dynamicTrackingStart();
            }
            return fireImmediately;
        }
    }

    /**
     * Removes listener for {@link #isDynamic() dynamic} filter. When this filter
     * is not dynamic this method does nothing.
     */
    public void removeUpdateListener(UpdateListener listener) {
        synchronized (updated) {
            if (listeners != null) {
                boolean wasEmpty = listeners.isEmpty();
                listeners.remove(listener);
                if (!wasEmpty && listeners.isEmpty())
                    dynamicTrackingStop();
            }
        }
    }

    /**
     * Fires notification about filter update and clears the list of listeners.
     * This method does nothing when invoked with {@code this} argument or when filter was already updated.
     * Note, that the value of {@code updatedFilter} must be produced by invoking a special
     * constructor {@link #QDFilter(DataScheme, QDFilter)}.
     *
     * @param updatedFilter the new instance of this filter. When updateFilter is null this method uses
     *                      {@link #produceUpdatedFilter()} under {@code synchronized(this)} section
     *                      to synchronously get the actual value of updated filter.
     */
    protected void fireFilterUpdated(QDFilter updatedFilter) {
        ArrayList<UpdateListener> fireListeners;
        synchronized (updated) {
            if (updated.getFilter() != this)
                return; // ignore subsequent calls when we've already updated
            if (updatedFilter == null)
                updatedFilter = produceUpdatedFilter();
            if (updatedFilter == this)
                return; // nothing to do
            updated.setFilter(updatedFilter);
            fireListeners = listeners;
            listeners = null;
            if (fireListeners != null && !fireListeners.isEmpty())
                dynamicTrackingStop();
        }
        // fire listeners installed on this specific QDFilter instance, will fire listeners installed on "updated", too
        // because "updated" itself is always in the list of listeners.
        if (fireListeners != null)
            for (UpdateListener listener : fireListeners)
                listener.filterUpdated(this);
    }

    /**
     * This method is called under {@code synchronized(getUpdated())} each time listeners list becomes non-empty.
     * This implementation does nothing and is designed for override to allocate resources for tracking
     * changes of this dynamic filter. This method is never invoked for non-dynamic filters.
     * This method is invoked only when this instance is the most up-to-date version of dynamic filter.
     */
    @GuardedBy("updated")
    protected void dynamicTrackingStart() {}

    /**
     * This method is called under {@code synchronized(getUpdated())} each time listeners list becomes empty.
     * This implementation does nothing and is designed for override to free resources for tracking
     * changes of this dynamic filter. This method is never invoked for non-dynamic filters.
     * This method is invoked only after {@link #dynamicTrackingStart()} was invoked.
     */
    @GuardedBy("updated")
    protected void dynamicTrackingStop() {}

    /**
     * This method is called under {@code synchronized(this)} when {@link #fireFilterUpdated(QDFilter) fireFilterUpdated}
     * is called with {@code null} argument to synchronously produce updated instance of this filter.
     * Note, that the result of this method must be produced by invoking a special
     * constructor {@link #QDFilter(DataScheme, QDFilter)}.
     */
    protected QDFilter produceUpdatedFilter() {
        return this;
    }

    /**
     * Converts a legacy {@link SubscriptionFilter} implementation into {@code QDFilter} instance.
     * This method never returns null. When {@code filter} is null, the result is {@link #ANYTHING}.
     * {@link StableSubscriptionFilter} implementations are also supported properly by this method.
     * @param filter The legacy filter.
     * @param scheme The data scheme that this filter works for.
     *               It may be null if scheme is unknown/any.
     * @throws IllegalArgumentException when filter is a subclass of {@link QDFilter} with a different scheme.
     */
    public static QDFilter fromFilter(SubscriptionFilter filter, DataScheme scheme) {
        if (filter instanceof QDFilter) {
            QDFilter result = (QDFilter) filter;
            if (scheme != null && result.getScheme() != null && scheme != result.getScheme())
                throw new IllegalArgumentException("the supplied filter has different scheme");
            return result;
        }
        if (filter == null)
            return ANYTHING;
        StableSubscriptionFilter stable = (filter instanceof StableSubscriptionFilter) ?
            ((StableSubscriptionFilter) filter).toStableFilter() : null;
        return new Legacy(scheme, filter, stable);
    }

    /**
     * Checks validity of the short-name for the filter.
     * @throws IllegalArgumentException if not valid.
     */
    public static void checkShortName(String name) {
        if (!isShortName(name))
            throw new IllegalArgumentException("Custom filter names can contain only lower-case English letters optionally starting with negation '!' sign");
    }

    private static boolean isShortName(String name) {
        if (name == null || name.isEmpty())
            return false;
        int start = 0;
        if (name.startsWith("!"))
            start++;
        for (int i = start; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 'a' || c > 'z')
                return false;
        }
        return true;
    }

    private static class Legacy extends QDFilter {
        private final SubscriptionFilter filter;
        private final StableSubscriptionFilter stable;

        Legacy(DataScheme scheme, SubscriptionFilter filter, StableSubscriptionFilter stable) {
            super(scheme);
            this.filter = filter;
            this.stable = stable;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return filter.acceptRecord(record, cipher, symbol);
        }

        @Override
        public boolean isStable() {
            return stable == filter;
        }

        @Override
        public QDFilter toStableFilter() {
            return stable == filter ? this : // This filter is stable
                QDFilter.fromFilter(stable, getScheme()); // this filter is not stable, so upgrade its stable part to QDFilter
        }

        @Override
        public String getDefaultName() {
            return filter.toString();
        }
    }

    private static class Constant extends QDFilter {
        private final boolean accepts;

        Constant(boolean accepts) {
            super(null);
            this.accepts = accepts;
            setName(getDefaultName()); // fix name on construction
        }

        @Override
        public Kind getKind() {
            return accepts ? Kind.ANYTHING : Kind.NOTHING;
        }

        @Override
        public boolean isFast() {
            return true;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return accepts;
        }

        @Override
        public String getDefaultName() {
            return accepts ? "*" : "!*";
        }

        @Override
        public QDFilter toStableFilter() {
            return this;
        }

        @Override
        public QDFilter negate() {
            return accepts ? NOTHING : ANYTHING;
        }
    }

    /**
     * Filter update notification interface.
     */
    public interface UpdateListener {
        /**
         * This method is fired at most once is a lifetime of a {@link QDFilter#isDynamic() dynamic} filter when
         * it updates. The new value of the filter shall be retrieved with {@link QDFilter#getUpdatedFilter} method.
         * This method is never called under synchronization on any filter to avoid deadlocks.
         * @param filter The filter that was updated. Use {@code filter.getUpdateFilter()} to get the new instance of
         *               the updated filter.
         */
        public void filterUpdated(QDFilter filter);
    }

    /**
     * Represents the most recent (updated) version of the dynamic filter.
     */
    public static class Updated implements UpdateListener {
        volatile QDFilter filter;
        volatile CopyOnWriteArrayList<UpdateListener> listeners;

        Updated(QDFilter filter) {
            this.filter = filter;
        }

        synchronized void setFilter(QDFilter filter) {
            this.filter = filter;
            if (listeners != null && !listeners.isEmpty())
                filter.addUpdateListenerNoFireImpl(this);
        }

        /**
         * Adds listener for {@link #isDynamic() dynamic} filter. When this filter
         * is not dynamic this method does nothing. The listener's
         * {@link UpdateListener#filterUpdated(QDFilter) filterUpdated} method is invoked
         * on every update of a filter that happens <b>afterwards</b>.
         * You must {@link #removeUpdateListener remove} listeners when they are no longer needed.
         */
        public void addUpdateListener(UpdateListener listener) {
            if (!filter.isDynamic())
                return;
            synchronized (this) {
                if (listeners == null)
                    listeners = new CopyOnWriteArrayList<>();
                boolean wasEmpty = listeners.isEmpty();
                listeners.add(listener);
                if (wasEmpty)
                    filter.addUpdateListenerNoFireImpl(this);
            }
        }

        public void removeUpdateListener(UpdateListener listener) {
            if (!filter.isDynamic())
                return;
            synchronized (this) {
                if (listeners == null || listeners.isEmpty())
                    return;
                listeners.remove(listener);
                if (listeners.isEmpty())
                    filter.removeUpdateListener(this);
            }
        }

        /**
         * Returns the most recent version of {@link #isDynamic() dynamic} filter.
         * This method returns the most recent new filter in series of updates that has happened so far.
         * Use {@link #addUpdateListener(UpdateListener) addUpdateListener} method to get notification on updates.
         * For non-dynamic filter the result of this method is always the same.
         * @return the most recent version of {@link #isDynamic() dynamic} filter.
         */
        public QDFilter getFilter() {
            return filter;
        }

        /**
         * Invokes {@link UpdateListener#filterUpdated(QDFilter)} on all added listeners.
         * @param filter the filter that was updated.
         */
        @Override
        public void filterUpdated(QDFilter filter) {
            CopyOnWriteArrayList<UpdateListener> listeners = this.listeners; // volatile read
            if (listeners != null)
                for (UpdateListener listener : listeners)
                    listener.filterUpdated(filter);
        }
    }
}
