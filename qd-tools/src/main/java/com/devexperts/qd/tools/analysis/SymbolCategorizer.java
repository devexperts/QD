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
package com.devexperts.qd.tools.analysis;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;

import java.util.ArrayList;
import java.util.List;

class SymbolCategorizer {
    private final List<QDFilter> filters = new ArrayList<QDFilter>();
    private final List<SymbolCategory> categories = new ArrayList<SymbolCategory>();

    SymbolCategorizer(DataScheme scheme) {
        for (SymbolCategory category : SymbolCategory.values()) {
            if (category.filterSpec == null)
                continue;
            QDFilter filter = CompositeFilters.valueOf(category.filterSpec, scheme);
            filters.add(filter);
            categories.add(category);
        }
    }

    SymbolCategory getSymbolCategory(int cipher, String symbol) {
        for (int i = 0; i < filters.size(); i++) {
            QDFilter filter = filters.get(i);
            if (filter.accept(null, null, cipher, symbol))
                return categories.get(i);
        }
        return SymbolCategory.OTHER;
    }
}
