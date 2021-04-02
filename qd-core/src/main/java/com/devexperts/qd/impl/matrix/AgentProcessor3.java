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
 * traversing three lists simultaneously.
 */
class AgentProcessor3 extends AgentProcessor2 {
    int nagent2;
    int nindex2;
    int timeMark2;
    int rid2;

    AgentProcessor3(Distribution dist) {
        super(dist);
    }

    @Override
    void processAgentsList(int nagent3, int nindex3, int timeMark3, int rid3) {
        while (nagent3 > 0) {
            if (nagent1 <= 0) {
                nagent1 = nagent3;
                nindex1 = nindex3;
                timeMark1 = timeMark3;
                rid1 = rid3;
                break;
            }
            if (nagent2 <= 0) {
                nagent2 = nagent3;
                nindex2 = nindex3;
                timeMark2 = timeMark3;
                rid2 = rid3;
                break;
            }
            SubMatrix nsub1 = dist.add(nagent1, nindex1, timeMark1, 0, rid1).sub;
            SubMatrix nsub2 = dist.add(nagent2, nindex2, timeMark2, 0, rid2).sub;
            SubMatrix nsub3 = dist.add(nagent3, nindex3, timeMark3, 0, rid3).sub;
            nagent1 = nsub1.getInt(nindex1 + NEXT_AGENT);
            nagent2 = nsub2.getInt(nindex2 + NEXT_AGENT);
            nagent3 = nsub3.getInt(nindex3 + NEXT_AGENT);
            nindex1 = nsub1.getInt(nindex1 + NEXT_INDEX);
            nindex2 = nsub2.getInt(nindex2 + NEXT_INDEX);
            nindex3 = nsub3.getInt(nindex3 + NEXT_INDEX);
        }
    }

    @Override
    void flush() {
        super.processAgentsList(nagent2, nindex2, timeMark2, rid2);
        nagent2 = 0;
        super.flush();
    }
}
