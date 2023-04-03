/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.AbstractMessageVisitor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.BinaryQTPComposer;
import com.devexperts.qd.qtp.BinaryQTPParser;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.qd.qtp.text.TextQTPParser;
import com.devexperts.qd.stats.QDStats;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Tests to ensure that message adapters correctly compose and parse various messages.
 */
public class MessageAdaptersTest {
    private static final TestDataScheme SCHEME = new TestDataScheme(20080828, TestDataScheme.Type.HAS_TIME);

    @Test
    public void testTicker() {
        QDFactory factory = QDFactory.getDefaultFactory();
        for (Serial ser : Serial.values()) {
            check(ser, factory.createTicker(SCHEME), factory.createTicker(SCHEME));
        }
    }

    @Test
    public void testStream() {
        QDFactory factory = QDFactory.getDefaultFactory();
        for (Serial ser : Serial.values()) {
            check(ser, factory.createStream(SCHEME), factory.createStream(SCHEME));
        }
    }

    @Test
    public void testHistory() {
        QDFactory factory = QDFactory.getDefaultFactory();
        for (Serial ser : Serial.values()) {
            check(ser, factory.createHistory(SCHEME), factory.createHistory(SCHEME));
        }
    }

    // different parser/composer combinations to try
    private enum Serial {
        BINARY_NO_DESCRIBE {
            @Override
            AbstractQTPComposer createComposer() {
                return new BinaryQTPComposer(SCHEME, false);
            }

            @Override
            AbstractQTPParser createParser() {
                BinaryQTPParser parser = new BinaryQTPParser(SCHEME) {
                    @Override
                    protected boolean isSchemeKnown() {
                        return true; // work without record descriptions
                    }
                };
                return parser;
            }
        },

        BINARY_DESCRIBE {
            @Override
            AbstractQTPComposer createComposer() {
                return new BinaryQTPComposer(SCHEME, true);
            }

            @Override
            AbstractQTPParser createParser() {
                return new BinaryQTPParser(SCHEME);
            }
        },

        TEXT {
            @Override
            AbstractQTPComposer createComposer() {
                return new TextQTPComposer(SCHEME);
            }

            @Override
            AbstractQTPParser createParser() {
                return new TextQTPParser(SCHEME);
            }
        };

        abstract AbstractQTPComposer createComposer();
        abstract AbstractQTPParser createParser();

    }

    private static class Msg {
        int size;
        ChunkList last;

        Msg() {}
    }

    private void check(final Serial ser, QDCollector dcollector, QDCollector acollector) {
        QDContract contract = dcollector.getContract();
        MessageType data = MessageType.forData(contract);
        MessageType add = MessageType.forAddSubscription(contract);
        MessageType remove = MessageType.forRemoveSubscription(contract);

        MessageAdapter dadapter= new DistributorAdapter.Factory(dcollector).createAdapter(QDStats.VOID);
        MessageAdapter aadapter = new AgentAdapter.Factory(acollector).createAdapter(QDStats.VOID);
        final Msg[][] dimsgs = new Msg[2][MessageType.values().length];
        final Msg[][] agmsgs = new Msg[2][MessageType.values().length];
        for (int i = 0; i < 2; i++) {
            for (MessageType m : MessageType.values()) {
                int j = m.ordinal();
                dimsgs[i][j] = new Msg();
                agmsgs[i][j] = new Msg();
            }
        }

        dadapter.setMessageListener(provider -> provider.retrieveMessages(new AbstractMessageVisitor() {
            @Override
            public boolean visitData(DataProvider provider, MessageType message) {
                fail("Distributor adapter shall not generate data");
                return false;
            }

            @Override
            public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
                SubscriptionBuffer buf = new SubscriptionBuffer();
                provider.retrieveSubscription(buf);
                Msg msg = dimsgs[0][message.ordinal()];
                msg.size += buf.size();
                AbstractQTPComposer bac = ser.createComposer();
                ChunkedOutput output = new ChunkedOutput();
                bac.setOutput(output);
                assertFalse(bac.visitSubscription(buf, message));
                msg.last = output.getOutput(msg);
                return false;
            }
        }));

        aadapter.setMessageListener(provider -> provider.retrieveMessages(new AbstractMessageVisitor() {
            @Override
            public boolean visitData(DataProvider provider, MessageType message) {
                RecordBuffer buf = new RecordBuffer();
                provider.retrieveData(buf);
                Msg msg = agmsgs[0][message.ordinal()];
                msg.size += buf.size();
                AbstractQTPComposer bac = ser.createComposer();
                ChunkedOutput output = new ChunkedOutput();
                bac.setOutput(output);
                while (bac.visitData(buf, message))
                    /* loop to compose all messages from buf */;
                msg.last = output.getOutput(msg);
                return false;
            }

            @Override
            public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
                fail("Agent adapter shall not generate subscription");
                return false;
            }
        }));

        dadapter.start();
        aadapter.start();

        TestSubscriptionProvider subprov = new TestSubscriptionProvider(SCHEME, 20080829);
        TestDataProvider dataprov = new TestDataProvider(SCHEME, 20080829);
        for (int i = 0; i < 100 ; i++) {
            // create agent and subscription for our distributor adapter to see
            QDAgent agent = dcollector.agentBuilder().build();
            SubscriptionBuffer sub = new SubscriptionBuffer();
            subprov.retrieveSubscription(sub);
            int size = sub.size();
            agent.setSubscription(sub);

            expectNoChange(ser, agmsgs);
            expectChange(ser, dimsgs, add, size);

            // resend add subscription message to agent adapter
            resendMessage(ser, dimsgs, add, aadapter);

            expectNoChange(ser, agmsgs);
            expectNoChange(ser, dimsgs);

            // create distributor and send data to agent adater
            QDDistributor dist = acollector.distributorBuilder().build();
            RecordBuffer buf = new RecordBuffer();
            dataprov.retrieveData(buf);
            dist.processData(buf);

            expectChange(ser, agmsgs, data, size);
            expectNoChange(ser, dimsgs);

            // resend data message to distributor adapter
            resendMessage(ser, agmsgs, data, dadapter);

            // close distributor
            dist.close();

            expectNoChange(ser, agmsgs);
            expectNoChange(ser, dimsgs);

            // close agent
            agent.close();

            expectNoChange(ser, agmsgs);
            expectChange(ser, dimsgs, remove, size);

            // resend remove subscription message to agent adapter
            resendMessage(ser, dimsgs, remove, aadapter);

            expectNoChange(ser, agmsgs);
            expectNoChange(ser, dimsgs);
        }
    }

    private void resendMessage(Serial ser, Msg[][] msgs, MessageType message, MessageAdapter toAdapter) {
        Msg msg = msgs[0][message.ordinal()];
        ChunkList last = msg.last;
        ChunkedInput input = new ChunkedInput();
        input.addAllToInput(last, msg);
        AbstractQTPParser parser = ser.createParser();
        parser.setInput(input);
        parser.parse(toAdapter);
    }

    private void expectChange(Serial ser, Msg[][] msgs, MessageType message, int size) {
        msgs[1][message.ordinal()].size += size;
        expectNoChange(ser, msgs);
    }

    private void expectNoChange(Serial ser, Msg[][] msgs) {
        for (MessageType msg : MessageType.values()) {
            int i = msg.ordinal();
            assertEquals(ser + " " + msg + " count", msgs[1][i].size, msgs[0][i].size);
        }
    }
}
