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
package com.devexperts.qd.monitoring;

import com.devexperts.mars.common.MARSNode;

/**
 * MARSNode wrapper that keeps long values and creates node on the first use.
 */
class VNode {
    private final MARSNode parent;
    private final String name;
    private final String description;

    private MARSNode node;

    long v; // last set value

    VNode(MARSNode parent, String name, String description) {
        this.parent = parent;
        this.name = name;
        this.description = description;
    }

    void set(long v) {
        if (v == this.v)
            return;
        if (node == null)
            node = parent.subNode(name, description);
        this.v = v;
        node.setValue(Long.toString(v));
    }
}
