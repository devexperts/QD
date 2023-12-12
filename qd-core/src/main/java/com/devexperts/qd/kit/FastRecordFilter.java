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
package com.devexperts.qd.kit;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;

import java.util.function.Predicate;

/**
 * Fast implementation for {@link RecordOnlyFilter} via boolean array.
 */
final class FastRecordFilter extends RecordOnlyFilter {

    private static final Logging log = Logging.getLogging(FastRecordFilter.class);

    private final QDFilter delegate;
    private final boolean[] accepts;
    private final SyntaxPrecedence syntaxPrecedence;

    FastRecordFilter(DataScheme scheme, boolean[] accepts) {
        super(scheme);
        this.delegate = null;
        this.accepts = accepts;
        int acceptedCount = countAccepted();
        syntaxPrecedence = acceptedCount <= 1 || acceptedCount == scheme.getRecordCount() ?
            SyntaxPrecedence.TOKEN : SyntaxPrecedence.OR;
    }

    FastRecordFilter(DataScheme scheme, String name, Predicate<DataRecord> filter) {
        super(scheme);
        delegate = null;
        accepts = new boolean[scheme.getRecordCount()];
        for (int i = 0; i < accepts.length; i++) {
            accepts[i] = filter.test(scheme.getRecord(i));
        }
        syntaxPrecedence = SyntaxPrecedence.TOKEN;
        setName(name);
    }

    FastRecordFilter(QDFilter delegate) {
        super(delegate.getScheme());
        if (delegate.isDynamic())
            throw new IllegalArgumentException("Only static filters are supported");
        this.delegate = delegate;
        int n = getScheme().getRecordCount();
        accepts = new boolean[n];
        for (int i = 0; i < n; i++) {
            accepts[i] = delegate.accept(null, getScheme().getRecord(i), 0, null);
        }
        syntaxPrecedence = delegate.getSyntaxPrecedence();
    }

    FastRecordFilter warnOnPotentialTypo(boolean doWarn) {
        if (doWarn) {
            int acceptedCount = countAccepted();
            if (acceptedCount == 0)
                log.warn("WARNING: Filter \"" + this + "\" matches no records.");
            if (acceptedCount == getScheme().getRecordCount())
                log.warn("WARNING: Filter \"" + this + "\" matches all records.");
        }
        return this;
    }

    @Override
    public QDFilter unwrap() {
        return delegate == null ? this : delegate;
    }

    @Override
    public QDFilter negate() {
        return new FastRecordFilter(delegate == null ? new NotFilter(this) : delegate.negate());
    }

    @Override
    public boolean acceptRecord(DataRecord record) {
        int id = record.getId();
        return id < accepts.length && accepts[id];
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        // we must keep name when "unwrap" is later used
        if (delegate != null)
            delegate.setName(name);
    }

    @Override
    public String getDefaultName() {
        if (delegate != null)
            return delegate.toString();
        int acceptedCount = countAccepted();
        if (acceptedCount == 0)
            return "!:*";
        if (acceptedCount == getScheme().getRecordCount())
            return ":*";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accepts.length; i++) {
            if (accepts[i]) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(':').append(PatternFilter.quote(getScheme().getRecord(i).getName()));
            }
        }
        return sb.toString();
    }

    @Override
    public SyntaxPrecedence getSyntaxPrecedence() {
        return syntaxPrecedence;
    }

    private int countAccepted() {
        int count = 0;
        for (boolean accept : accepts) {
            if (accept)
                count++;
        }
        return count;
    }
}
