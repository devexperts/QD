/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.qd.*;

/**
 * Fast implementation for {@link RecordOnlyFilter} via boolean array.
 */
final class FastRecordFilter extends RecordOnlyFilter {
    private final QDFilter delegate;
    private final boolean[] accepts;

    FastRecordFilter(DataScheme scheme, boolean[] accepts) {
        super(scheme);
        this.delegate = null;
        this.accepts = accepts;
    }

    FastRecordFilter(QDFilter delegate, boolean warnOnPotentialTypo) {
        super(delegate.getScheme());
        if (delegate.isDynamic())
            throw new IllegalArgumentException("Only static filters are supported");
        this.delegate = delegate;
        int n = getScheme().getRecordCount();
        accepts = new boolean[n];
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            accepts[i] = delegate.accept(null, getScheme().getRecord(i), 0, null);
            if (accepts[i])
                cnt++;
        }
        if (warnOnPotentialTypo && cnt == 0)
            QDLog.log.info("WARNING: Filter \"" + delegate + "\" matches no records.");
        if (warnOnPotentialTypo && cnt == n)
            QDLog.log.info("WARNING: Filter \"" + delegate + "\" matches all records.");
    }

    @Override
    public QDFilter unwrap() {
        return delegate == null ? this : delegate;
    }

    @Override
    public QDFilter negate() {
        return new FastRecordFilter(delegate == null ? super.negate() : delegate.negate(), false);
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accepts.length; i++)
            if (accepts[i]) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(':').append(PatternFilter.quote(getScheme().getRecord(i).getName()));
            }
        if (sb.length() == 0)
            sb.append("!:*");
        return sb.toString();
    }

    @Override
    public SyntaxPrecedence getSyntaxPrecedence() {
        if (delegate != null)
            return delegate.getSyntaxPrecedence();
        int count = 0;
        for (boolean accept : accepts)
            if (accept)
                count++;
        return count <= 1 ? SyntaxPrecedence.TOKEN : SyntaxPrecedence.OR;
    }
}
