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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;

/**
 * Default implementation for {@link QDFilter#negate()} operation.
 */
public class NotFilter extends QDFilter implements QDFilter.UpdateListener {
    QDFilter delegate; // effectively final, but need to change it after clone for dynamic filters

    public NotFilter(QDFilter delegate) {
        this(delegate, null);
    }

    private NotFilter(QDFilter delegate, NotFilter source) {
        super(delegate.getScheme(), source);
        this.delegate = delegate;
    }

    @Override
    public Kind getKind() {
        Kind kind= delegate.getKind();
        if (kind.isRecordOnly())
            return Kind.RECORD_ONLY;
        else if (kind.isSymbolOnly())
            return Kind.OTHER_SYMBOL_ONLY;
        else
            return Kind.OTHER;
    }

    @Override
    public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        DataScheme scheme = getScheme();
        if (scheme == null)
            scheme = record.getScheme(); // for backwards compatibility
        // TODO the below code incorrectly passes wildcard disregarding other criteria like contract or record
        // TODO refactor with explicit split of all filters into individual single criteria filters plus expressions
        if (cipher == scheme.getCodec().getWildcardCipher())
            return true;
        return !delegate.accept(contract, record, cipher, symbol);
    }

    @Override
    public QDFilter negate() {
        return delegate;
    }

    @Override
    public boolean isStable() {
        return delegate.isStable();
    }

    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }

    @Override
    public boolean isFast() {
        return delegate.isFast();
    }

    @Override
    public QDFilter toStableFilter() {
        return delegate.isStable() ? this : null;
    }

    @Override
    protected void dynamicTrackingStart() {
        delegate.addUpdateListener(this);
    }

    @Override
    protected void dynamicTrackingStop() {
        delegate.removeUpdateListener(this);
    }

    @Override
    protected QDFilter produceUpdatedFilter() {
        return new NotFilter(delegate.getUpdatedFilter(), this);
    }

    @Override
    public void filterUpdated(QDFilter filter) {
        fireFilterUpdated(null); // will synchronously use produceUpdateFilter
        // NOTE: it uses dynamicTrackingStart mechanics to add new listener (new instance) to the delegate
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return delegate.equals(((NotFilter) o).delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String getDefaultName() {
        StringBuilder sb = new StringBuilder();
        SyntaxPrecedence precedence = delegate.getSyntaxPrecedence();
        boolean parenthesis = precedence == SyntaxPrecedence.AND || precedence == SyntaxPrecedence.OR;
        sb.append('!');
        if (parenthesis)
            sb.append('(');
        sb.append(delegate);
        if (parenthesis)
            sb.append(')');
        return sb.toString();
    }
}
