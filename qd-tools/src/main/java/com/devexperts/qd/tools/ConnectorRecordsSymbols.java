/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.*;

class ConnectorRecordsSymbols {
    static interface Listener {
        public void recordsAvailable(RecordProvider provider, MessageType message);
    }

    private final DataRecord[] records;
    private final String[] symbols;
    private final long millis;

    ConnectorRecordsSymbols(DataRecord[] records, String[] symbols, long millis) {
        this.records = records;
        this.symbols = symbols;
        this.millis = millis;
    }

    void subscribe(QDEndpoint endpoint, Listener listener) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        long time = (millis / 1000) << 32;
        for (String symbol : symbols) {
            int cipher = endpoint.getScheme().getCodec().encode(symbol);
            for (DataRecord record : records)
                sub.add(record, cipher, symbol).setTime(time);
        }
        for (QDCollector c : endpoint.getCollectors()) {
            // create agent with all supported options
            QDAgent agent = c.agentBuilder().withOptSet(ProtocolOption.SUPPORTED_SET).build();
            agent.setSubscription(sub);
            sub.rewind();
            MessageType message = MessageType.forData(c.getContract());
            agent.setRecordListener(provider -> listener.recordsAvailable(provider, message));
        }
        sub.release();
    }
}
