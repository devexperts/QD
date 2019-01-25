/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.ng.*;

class AgentSnapshotProvider extends AbstractRecordProvider {
    private final Agent agent;

    AgentSnapshotProvider(Agent agent) {
        this.agent = agent;
    }

    @Override
    public RecordMode getMode() {
        return agent.getMode();
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        return agent.collector.retrieveData(agent, sink, true);
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        agent.setSnapshotListener(listener);
    }
}
