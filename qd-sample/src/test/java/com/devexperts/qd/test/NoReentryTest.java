/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.RecordBuffer;
import junit.framework.TestCase;

public class NoReentryTest extends TestCase {
    private static final DataScheme SCHEME = new TestDataScheme(20091005);
    private static final int SYM_A = SCHEME.getCodec().encode("A");
    private static final DataRecord RECORD = SCHEME.getRecord(0);

    public void testPotentialDeadLock() {
        QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        final boolean[] seen_exception = new boolean[1];
        ticker.setErrorHandler(new QDErrorHandler() {
            public void handleDataError(DataProvider provider, Throwable t) {
                if (t instanceof IllegalStateException)
                    seen_exception[0] = true;
                else
                    fail(t.toString());
            }

            public void handleSubscriptionError(SubscriptionProvider provider, Throwable t) {
                fail(t.toString());
            }
        });
        final QDAgent agent = ticker.agentBuilder().build();
        QDDistributor dist = ticker.distributorBuilder().build();

        final SubscriptionBuffer sub = new SubscriptionBuffer();
        sub.visitRecord(RECORD, SYM_A, null);
        agent.setSubscription(sub.examiningIterator());
        agent.setDataListener(new DataListener() {
            public void dataAvailable(DataProvider provider) {
                provider.retrieveData(new DataVisitor() {
                    public boolean hasCapacity() {
                        return true;
                    }

                    public void visitRecord(DataRecord record, int cipher, String symbol) {
                        // try to rerenter QD from inside of here
                        agent.addSubscription(sub.examiningIterator());
                    }

                    public void visitIntField(DataIntField field, int value) {
                    }

                    public void visitObjField(DataObjField field, Object value) {
                    }
                });
            }
        });

        RecordBuffer buf = new RecordBuffer();
        buf.add(RECORD, SYM_A, null);
        dist.processData(buf);

        assertTrue(seen_exception[0]);
    }
}
