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
 * traversing four lists simultaneously.
 */
class AgentProcessor4 extends AgentProcessor3 {
    int nagent3;
    int nindex3;
    int timeMark3;
    int rid3;

    AgentProcessor4(Distribution dist) {
        super(dist);
    }

    @Override
    void processAgentsList(int nagent4, int nindex4, int timeMark4, int rid4) {
        while (nagent4 > 0) {
            if (nagent1 <= 0) {
                nagent1 = nagent4;
                nindex1 = nindex4;
                timeMark1 = timeMark4;
                rid1 = rid4;
                break;
            }
            if (nagent2 <= 0) {
                nagent2 = nagent4;
                nindex2 = nindex4;
                timeMark2 = timeMark4;
                rid2 = rid4;
                break;
            }
            if (nagent3 <= 0) {
                nagent3 = nagent4;
                nindex3 = nindex4;
                timeMark3 = timeMark4;
                rid3 = rid4;
                break;
            }
            SubMatrix nsub1 = dist.add(nagent1, nindex1, timeMark1, 0, rid1).sub;
            SubMatrix nsub2 = dist.add(nagent2, nindex2, timeMark2, 0, rid2).sub;
            SubMatrix nsub3 = dist.add(nagent3, nindex3, timeMark3, 0, rid3).sub;
            SubMatrix nsub4 = dist.add(nagent4, nindex4, timeMark4, 0, rid4).sub;
            nagent1 = nsub1.getInt(nindex1 + NEXT_AGENT);
            nagent2 = nsub2.getInt(nindex2 + NEXT_AGENT);
            nagent3 = nsub3.getInt(nindex3 + NEXT_AGENT);
            nagent4 = nsub4.getInt(nindex4 + NEXT_AGENT);
            nindex1 = nsub1.getInt(nindex1 + NEXT_INDEX);
            nindex2 = nsub2.getInt(nindex2 + NEXT_INDEX);
            nindex3 = nsub3.getInt(nindex3 + NEXT_INDEX);
            nindex4 = nsub4.getInt(nindex4 + NEXT_INDEX);
        }
    }

    @Override
    void flush() {
        super.processAgentsList(nagent3, nindex3, timeMark3, rid3);
        nagent3 = 0;
        super.flush();
    }
}
