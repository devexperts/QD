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

import static com.devexperts.qd.impl.matrix.Collector.NEXT_AGENT;
import static com.devexperts.qd.impl.matrix.Collector.NEXT_INDEX;

/**
 * Processes subscription list starting from a given agent and index,
 * traversing two lists simultaneously.
 */
class AgentProcessor2 extends AgentProcessor {
    int nagent1;
    int nindex1;
    int timeMark1;
    int rid1;

    AgentProcessor2(Distribution dist) {
        super(dist);
    }

    @Override
    void processAgentsList(int nagent2, int nindex2, int timeMark2, int rid2) {
        while (nagent2 > 0) {
            if (nagent1 <= 0) {
                nagent1 = nagent2;
                nindex1 = nindex2;
                timeMark1 = timeMark2;
                rid1 = rid2;
                break;
            }
            SubMatrix nsub1 = dist.add(nagent1, nindex1, timeMark1, 0, rid1).sub;
            SubMatrix nsub2 = dist.add(nagent2, nindex2, timeMark2, 0, rid2).sub;
            nagent1 = nsub1.getInt(nindex1 + NEXT_AGENT);
            nagent2 = nsub2.getInt(nindex2 + NEXT_AGENT);
            nindex1 = nsub1.getInt(nindex1 + NEXT_INDEX);
            nindex2 = nsub2.getInt(nindex2 + NEXT_INDEX);
        }
    }

    @Override
    void flush() {
        super.processAgentsList(nagent1, nindex1, timeMark1, rid1);
        nagent1 = 0;
    }
}
