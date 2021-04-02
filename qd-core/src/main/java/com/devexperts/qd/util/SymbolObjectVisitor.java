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
package com.devexperts.qd.util;

/**
 * Visitor interface for {@link SymbolObjectMap} entries.
 */
public interface SymbolObjectVisitor<T> {
    /**
     * Returns whether visitor has capacity to efficiently visit next record.
     */
    public boolean hasCapacity();

    /**
     * Visits {@link SymbolObjectMap} entry.
     */
    public void visitEntry(int cipher, String symbol, T value);
}
