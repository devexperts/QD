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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.AbstractAgentBuilder;

class VoidAgentBuilder extends AbstractAgentBuilder {
    final QDContract contract;
    final DataScheme scheme;

    VoidAgentBuilder(QDContract contract, DataScheme scheme) {
        if (contract == null || scheme == null)
            throw new NullPointerException();
        this.contract = contract;
        this.scheme = scheme;
    }

    @Override
    public QDAgent build() {
        return new VoidAgent(this);
    }
}
