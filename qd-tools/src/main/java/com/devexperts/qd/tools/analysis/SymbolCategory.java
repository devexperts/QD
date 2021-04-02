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

enum SymbolCategory {
    REPEAT_LAST("", null),
    FX("", "fxsymbol"),
    FUT("/", "futsymbol"),
    IND("$", "indsymbol"),
    SPREAD("=", "spreadsymbol"),
    FUT_OPT("./", "futoptsymbol"),
    BS_OPT(".", "bsoptsymbol"),
    OTHER("", null);

    final String prefix;
    final String filterSpec;

    SymbolCategory(String prefix, String filterSpec) {
        this.prefix = prefix;
        this.filterSpec = filterSpec;
    }
}
