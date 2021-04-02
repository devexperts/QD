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
package com.dxfeed.ipf.transform;

class SymbolCategoryExpression extends Expression<Boolean> {
    static final Expression<Boolean> BS = new SymbolCategoryExpression();
    static final Expression<Boolean> BSOPT = new SymbolCategoryExpression();
    static final Expression<Boolean> FUT = new SymbolCategoryExpression();
    static final Expression<Boolean> FUTOPT = new SymbolCategoryExpression();
    static final Expression<Boolean> SPREAD = new SymbolCategoryExpression();

    private SymbolCategoryExpression() {
        super(Boolean.class);
    }

    @Override
    Boolean evaluate(TransformContext ctx) {
        String symbol = ctx.currentProfile().getSymbol();
        if (symbol == null || symbol.isEmpty())
            return false;
        char c0 = symbol.charAt(0);
        if (c0 == '/')
            return this == FUT;
        if (c0 == '=')
            return this == SPREAD;
        if (c0 == '.')
            return symbol.length() > 1 && symbol.charAt(1) == '/' ? this == FUTOPT : this == BSOPT;
        return this == BS;
    }
}
