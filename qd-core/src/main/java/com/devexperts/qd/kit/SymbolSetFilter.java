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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.util.SymbolSet;

/**
 * Fast filter based on {@link SymbolSet}.
 * Delegate's kind should be {@link Kind#isSymbolOnly() symbol-only} filter.
 * Is {@link SymbolSetFilter#isDynamic() dynamic} only iff its delegate is dynamic.
 */
public final class SymbolSetFilter extends QDFilter implements QDFilter.UpdateListener {
    // ------- static factory -------

    /**
     * Parses a given specification as symbol set filter for a given scheme.
     * Here, any pattern with an ".ipf" substring is recognized as an IPF file url
     * that gives a set of symbols.
     *
     * @param spec the filter specification.
     * @param scheme the scheme.
     * @return filter.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static SymbolSetFilter valueOf(String spec, DataScheme scheme) {
        if (spec.equals("*") || spec.equals("all"))
            return new SymbolSetFilter(scheme);
        QDFilter filter = CompositeFilters.getFactory(scheme).createFilter(spec, QDFilterContext.SYMBOL_SET);
        if (filter instanceof SymbolSetFilter)
            return (SymbolSetFilter) filter;
        if (filter.getKind().isSymbolOnly() && filter.getSymbolSet() != null)
            return new SymbolSetFilter(filter); // return wrapped filter
        throw new FilterSyntaxException("\"" + spec + "\" does not specify a list of symbols");
    }

    // ------- instance -------

    private final int wildcard;
    private final SymbolSet set;
    private final QDFilter delegate;
    private final boolean negated;

    public SymbolSetFilter(DataScheme scheme, SymbolSet set) {
        this(scheme, set, null);
    }

    public SymbolSetFilter(QDFilter delegate) {
        this(delegate.getScheme(), delegate.getSymbolSet(), delegate);
    }

    SymbolSetFilter(DataScheme scheme, SymbolSet set, QDFilter delegate) {
        this(scheme, set, delegate, false);
    }

    // use this constructor to create updated filter, works only with delegate
    private SymbolSetFilter(QDFilter delegate, SymbolSetFilter source) {
        super(delegate.getScheme(), source);
        if (!delegate.getKind().isSymbolOnly())
            throw new IllegalArgumentException("Delegate '" + delegate + "' should be symbol set only");
        this.wildcard = delegate.getScheme().getCodec().getWildcardCipher();
        this.set = delegate.getSymbolSet().unmodifiable();
        this.delegate = delegate;
        this.negated = source.negated;
    }

    private SymbolSetFilter(DataScheme scheme, SymbolSet set, QDFilter delegate, boolean negated) {
        super(scheme);
        wildcard = scheme.getCodec().getWildcardCipher();
        this.set = set.unmodifiable();
        this.delegate = delegate;
        this.negated = negated;
    }

    // symbol set with a single wildcard symbol
    private SymbolSetFilter(DataScheme scheme) {
        super(scheme);
        wildcard = scheme.getCodec().getWildcardCipher();
        SymbolSet set = SymbolSet.createInstance();
        set.add(wildcard, null);
        this.set = set.unmodifiable();
        this.delegate = null;
        this.negated = false;
    }

    @Override
    public Kind getKind() {
        return negated ? Kind.OTHER_SYMBOL_ONLY : Kind.SYMBOL_SET;
    }

    @Override
    public boolean isFast() {
        return true;
    }

    @Override
    public QDFilter unwrap() {
        return delegate == null ? this : delegate;
    }

    @Override
    public QDFilter negate() {
        return new SymbolSetFilter(getScheme(), set, delegate == null ? null : delegate.negate(), !negated);
    }

    @Override
    public SymbolSet getSymbolSet() {
        return negated ? null : set;
    }

    @Override
    public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
        if (cipher == wildcard)
            return true;
        return negated ^ set.contains(cipher, symbol);
    }

    @Override
    public boolean isDynamic() {
        return delegate != null ? delegate.isDynamic() : false;
    }

    @Override
    protected QDFilter produceUpdatedFilter() {
        return new SymbolSetFilter(delegate.getUpdatedFilter(), this);
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
    public void filterUpdated(QDFilter filter) {
        fireFilterUpdated(null);
    }

    @Override
    public QDFilter toStableFilter() {
        // is stable without delegate
        return delegate == null || delegate.isStable() ? this : delegate.toStableFilter();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SymbolSetFilter other = (SymbolSetFilter) o;
        return set.equals(other.set) && negated == other.negated;
    }

    @Override
    public int hashCode() {
        return set.hashCode();
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
        if (set.isEmpty())
            return negated ? "*" : "!*";
        StringBuilder sb = new StringBuilder();
        boolean parenthesis = negated && set.size() > 1;
        if (negated)
            sb.append('!');
        if (parenthesis)
            sb.append('(');
        int startLen = sb.length();
        set.examine((cipher, symbol) -> {
            if (sb.length() > startLen)
                sb.append(",");
            sb.append(PatternFilter.quote(getScheme().getCodec().decode(cipher, symbol)));
        });
        if (parenthesis)
            sb.append(')');
        return sb.toString();
    }

    @Override
    public SyntaxPrecedence getSyntaxPrecedence() {
        if (delegate != null)
            return delegate.getSyntaxPrecedence();
        return set.size() <= 1 || negated ? SyntaxPrecedence.TOKEN : SyntaxPrecedence.OR;
    }
}
