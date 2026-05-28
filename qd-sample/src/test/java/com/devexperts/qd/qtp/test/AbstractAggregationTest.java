/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.test;

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.ChannelDescription;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.auth.BasicChannelShaperFactory;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.qtp.test.util.CapturingAgentFactory;
import com.devexperts.qd.qtp.test.util.CapturingDistributorFactory;
import com.devexperts.qd.qtp.test.util.TestAuthRealmFactory;
import com.devexperts.qd.qtp.test.util.TestLoginHandlerFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.util.TimePeriodInfo;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devexperts.qd.qtp.auth.BasicChannelShaperFactory.ALL_DATA;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.assertTrue;

/**
 * Shared infrastructure for socket-based server + client integration tests at the
 * QDEndpoint level with QTP authentication and aggregation-period management.
 *
 * <p>Subclasses contain only {@code @Test} methods and any test-specific helpers.
 * State, lifecycle, realm/handler builders, and connector wiring live here.
 *
 * <p>Both endpoints are wired through capturing factories
 * ({@link CapturingAgentFactory} on the server, {@link CapturingDistributorFactory}
 * on the client). Tests that don't inspect descriptors simply ignore the capture fields.
 */
public abstract class AbstractAggregationTest {

    protected static final long WAIT_TIMEOUT_MS = 40_000;
    protected static final String USER = "test-user";
    protected static final String PASSWORD = "right-password";
    protected static final String SYMBOL = "AAPL";

    protected static final TestDataScheme SCHEME = new TestDataScheme();
    protected static final DataRecord RECORD = SCHEME.getRecord(0);

    protected String testId = UUID.randomUUID().toString();
    protected QDEndpoint server;
    protected QDEndpoint client;
    protected int port;
    protected CountDownLatch firstData;

    protected CapturingAgentFactory serverCapture;
    protected CapturingDistributorFactory clientCapture;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        testId = UUID.randomUUID().toString();
    }

    @After
    public void tearDown() {
        TestAuthRealmFactory.unregister(testId);
        TestLoginHandlerFactory.unregister(testId);
        if (client != null)
            client.close();
        if (server != null)
            server.close();
        ThreadCleanCheck.after();
    }

    // ---- Realm / handler builders ----

    protected QDAuthRealm tokenChannelsRealm(String... channels) {
        return new QDAuthRealm() {
            @Override
            public Promise<AuthSession> authenticate(AuthToken token, TypedMap vars) {
                if (!PASSWORD.equals(token.getPassword()))
                    return Promise.failed(new SecurityException("Bad credentials"));
                return Promise.completed(sessionWithChannels(token, channels));
            }
            @Override
            public String getAuthenticationInfo() {
                return testId;
            }
        };
    }

    protected QDAuthRealm unrestrictedRealm() {
        return tokenChannelsRealm();
    }

    protected static class CountingRealm implements QDAuthRealm {
        private final QDAuthRealm delegate;
        final AtomicInteger calls = new AtomicInteger();

        CountingRealm(QDAuthRealm delegate) {
            this.delegate = delegate;
        }

        @Override
        public Promise<AuthSession> authenticate(AuthToken token, TypedMap vars) {
            calls.incrementAndGet();
            return delegate.authenticate(token, vars);
        }

        @Override
        public String getAuthenticationInfo() {
            return delegate.getAuthenticationInfo();
        }
    }

    protected CountingRealm counting(QDAuthRealm delegate) {
        return new CountingRealm(delegate);
    }

    protected QDLoginHandler validLoginHandler() {
        return new QDLoginHandler() {
            @Override
            public Promise<AuthToken> login(String reason) {
                if (!reason.endsWith(testId))
                    return Promise.failed(new SecurityException("Bad credentials"));
                return Promise.completed(AuthToken.createBasicToken(USER, PASSWORD));
            }
            @Override
            public AuthToken getAuthToken() {
                return null;
            }
        };
    }

    // ---- Connector setup ----

    protected void startServer(String options, QDAuthRealm realm) throws InterruptedException {
        TestAuthRealmFactory.register(testId, realm);
        server = QDEndpoint.newBuilder()
            .withName("server")
            .withScheme(SCHEME)
            .withCollectors(Collections.singletonList(QDContract.TICKER))
            .build();

        String connectorName = "server-" + testId;
        Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(connectorName);
        serverCapture = new CapturingAgentFactory(server, null);
        server.addConnectors(MessageConnectors.createMessageConnectors(
            serverCapture,
            ":0[name=" + connectorName + ",bindAddr=127.0.0.1,auth=" + TestAuthRealmFactory.PREFIX + ":" +
                testId + (options.isEmpty() ? "" : "," + options) + "]", QDStats.VOID));

        QDDistributor distributor = server.getTicker().distributorBuilder().build();
        distributor.getAddedRecordProvider().setRecordListener(provider -> {
            RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
            provider.retrieve(sub);
            sub.rewind();
            RecordBuffer data = RecordBuffer.getInstance(RecordMode.DATA);
            for (RecordCursor cur; (cur = sub.next()) != null; ) {
                data.add(cur.getRecord(), cur.getCipher(), cur.getDecodedSymbol());
            }
            sub.release();
            if (!data.isEmpty())
                distributor.process(data);
            data.release();
        });

        server.startConnectors();
        port = portPromise.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    protected void startClient(String options, QDLoginHandler handler) {
        TestLoginHandlerFactory.register(testId, handler);
        client = QDEndpoint.newBuilder()
            .withName("client")
            .withScheme(SCHEME)
            .withCollectors(Collections.singletonList(QDContract.TICKER))
            .build();

        String address = "127.0.0.1:" + port + "[login=" + TestLoginHandlerFactory.PREFIX + ":" + testId
            + (options.isEmpty() ? "" : "," + options) + "]";
        clientCapture = new CapturingDistributorFactory(client, null);
        client.addConnectors(MessageConnectors.createMessageConnectors(
            clientCapture, address, QDStats.VOID));

        firstData = new CountDownLatch(1);
        QDAgent agent = client.getTicker().agentBuilder().build();
        agent.setRecordListener(provider -> {
            RecordBuffer buf = RecordBuffer.getInstance(RecordMode.DATA);
            provider.retrieve(buf);
            buf.rewind();
            for (RecordCursor cur; (cur = buf.next()) != null; ) {
                firstData.countDown();
            }
            buf.release();
        });
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, SCHEME.getCodec().encode(SYMBOL), SYMBOL);
        agent.setSubscription(sub);
        sub.release();

        client.startConnectors();
    }

    // ---- Awaits ----

    protected void awaitFirstData(String failureSuffix) throws InterruptedException {
        assertTrue("no data — " + failureSuffix, firstData.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    protected void awaitClientPeriod(TimePeriodInfo expected) {
        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(() -> expected.equals(
            client.getAggregationPeriodInfo()));
    }

    // ---- Helpers ----

    protected static AuthSession sessionWithChannels(AuthToken token, String... channels) {
        AuthSession session = new AuthSession(token);
        if (channels.length == 0) {
            session.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY, ALL_DATA);
        } else {
            ChannelDescription[] descs = Arrays.stream(channels)
                .map(ChannelDescription::new)
                .toArray(ChannelDescription[]::new);
            session.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY, descs);
        }
        return session;
    }
}
