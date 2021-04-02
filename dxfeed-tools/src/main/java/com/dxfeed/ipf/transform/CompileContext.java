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

import java.util.HashMap;
import java.util.Map;

class CompileContext {
    final CompileContext parent;

    final Map<String, FieldReference> fields = new HashMap<>();

    CompileContext(CompileContext parent) {
        this.parent = parent;
    }
}
