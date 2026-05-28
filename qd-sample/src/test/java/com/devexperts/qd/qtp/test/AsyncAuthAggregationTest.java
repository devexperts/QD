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
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimePeriodInfo;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Async-auth scenarios (companion to {@link AuthAndAggregationLiveUpdateTest}).
 *
 * <p>Each test installs a {@link QDAuthRealm} (server) or {@link QDLoginHandler} (client)
 * whose {@code Promise} parks on an explicit release latch. The test waits on a paired
 * {@code started} latch — counted down inside the realm/handler — to know that auth has
 * reached the gate, then mutates {@code requestedAggregationPeriod} (client) or
 * {@code maxAggregationPeriod} (server), then releases the gate. The control flow is
 * driven by happens-before signals rather than by sleeps, so the test does not depend on
 * wall-clock estimates of how long auth takes on a particular host.
 */
public class AsyncAuthAggregationTest extends AbstractAggregationTest {

    private ExecutorService asyncAuth;

    @Before
    public void setUpAsyncExecutor() {
        asyncAuth = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "async-auth-" + testId);
            t.setDaemon(true);
            return t;
        });
    }

    @After
    public void tearDownAsyncExecutor() throws InterruptedException {
        asyncAuth.shutdownNow();
        if (!asyncAuth.awaitTermination(5, TimeUnit.SECONDS))
            fail("async-auth executor failed to terminate");
    }

    /**
     * Server realm parks on the release gate; while the realm Promise is unresolved
     * the test sets {@code requestedAggregationPeriod}. The new value must reach the
     * server in a single descriptor and the client's effective period must converge
     * to the resolved bounds once the gate releases.
     */
    @Test
    public void testPendingRealmHoldsRequestedAggregationPeriodUntilAuthCompletes() throws Exception {
        CountDownLatch authStarted = new CountDownLatch(1);
        CountDownLatch releaseAuth = new CountDownLatch(1);
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            gatedRealm(authStarted, releaseAuth));
        startClient("", validLoginHandler());

        assertTrue("server realm not reached",
            authStarted.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        releaseAuth.countDown();

        // ch@3s: 5s ∈ [3,10] → 5. ch@8s: 5<min=8 → 8.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));
        awaitFirstData("post-auth handshake stuck");

        List<ProtocolDescriptor> withRequest = filterByDataReceiveProperty(
            serverCapture.incoming(), ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY);
        assertEquals("expected single emission of requestedAggregationPeriod", 1, withRequest.size());
        MessageDescriptor data = withRequest.get(0).getReceive(MessageType.forData(QDContract.TICKER));
        assertEquals("PT5S", data.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
    }

    /**
     * Client login handler parks on the release gate; while the handler's Promise is
     * unresolved the test sets {@code requestedAggregationPeriod}. The new value must
     * reach the server in a single descriptor and the client's effective period must
     * converge to the resolved bounds once the gate releases.
     */
    @Test
    public void testPendingLoginHandlerHoldsRequestedAggregationPeriodUntilLoginCompletes() throws Exception {
        CountDownLatch loginStarted = new CountDownLatch(1);
        CountDownLatch releaseLogin = new CountDownLatch(1);
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", gatedLoginHandler(loginStarted, releaseLogin));

        assertTrue("client login handler not invoked",
            loginStarted.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        releaseLogin.countDown();

        // ch@3s: 5s ∈ [3,10] → 5. ch@8s: 5<min=8 → 8.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));
        awaitFirstData("post-login handshake stuck");

        List<ProtocolDescriptor> withRequest = filterByDataReceiveProperty(
            serverCapture.incoming(), ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY);
        assertEquals("expected single emission of requestedAggregationPeriod", 1, withRequest.size());
        MessageDescriptor data = withRequest.get(0).getReceive(MessageType.forData(QDContract.TICKER));
        assertEquals("PT5S", data.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
    }

    /**
     * Several {@code setRequestedAggregationPeriod} calls during a pending realm window
     * must converge on the final value. The number of on-wire emissions is racy with the
     * writer thread draining the DESCRIBE_PROTOCOL mask between calls — accept anywhere
     * from one up to as many emissions as edits, but every emitted value must be one of
     * the requested ones and the last emission must carry the final value.
     */
    @Test
    public void testParallelEditsDuringPendingAuthCollapseToFinalValue() throws Exception {
        CountDownLatch authStarted = new CountDownLatch(1);
        CountDownLatch releaseAuth = new CountDownLatch(1);
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            gatedRealm(authStarted, releaseAuth));
        startClient("", validLoginHandler());

        assertTrue("server realm not reached",
            authStarted.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("3s"));
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("7s"));
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        releaseAuth.countDown();

        // Final request 5s. ch@3s: 5 ∈ [3,10] → 5. ch@8s: 5<min=8 → 8.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));
        awaitFirstData("post-auth handshake stuck");

        List<ProtocolDescriptor> withRequest = filterByDataReceiveProperty(
            serverCapture.incoming(), ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY);
        assertTrue("server must receive at least one descriptor carrying the requested period",
            !withRequest.isEmpty());
        assertTrue("expected at most one emission per edit, got " + withRequest.size(),
            withRequest.size() <= 3);
        for (ProtocolDescriptor d : withRequest) {
            String v = d.getReceive(MessageType.forData(QDContract.TICKER))
                .getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY);
            assertTrue("unexpected requested period on the wire: " + v,
                "PT3S".equals(v) || "PT7S".equals(v) || "PT5S".equals(v));
        }
        MessageDescriptor last = withRequest.get(withRequest.size() - 1)
            .getReceive(MessageType.forData(QDContract.TICKER));
        assertEquals("last emission must carry the final requested value",
            "PT5S", last.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
    }

    /**
     * Server-side bound edit during a pending realm window: the new
     * {@code aggregationPeriodInfo} must reach the client only after the realm
     * Promise completes, embedded in the first post-auth descriptor.
     */
    @Test
    public void testServerMaxEditDuringPendingAuthDeferredUntilAuthCompletes() throws Exception {
        CountDownLatch authStarted = new CountDownLatch(1);
        CountDownLatch releaseAuth = new CountDownLatch(1);
        // Channels declared only via defaultAggregationPeriod (no own min) so a connector-max edit
        // can clamp them — an @Ns channel keeps its own min and would shadow the max edit.
        startServer("channels=" +
            "(AAPL&ticker[defaultAggregationPeriod=3s])" +
            "(AAPL&ticker[defaultAggregationPeriod=8s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            gatedRealm(authStarted, releaseAuth));
        startClient("", validLoginHandler());

        assertTrue("server realm not reached",
            authStarted.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // Tighten max during pending — channels (3s, 8s) clamp to (3s, 5s) on completion.
        server.getConnectors().get(0).setMaxAggregationPeriod("7s");
        server.getConnectors().get(0).setMaxAggregationPeriod("5s");
        releaseAuth.countDown();

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 5_000));
        awaitFirstData("post-auth handshake stuck");

        List<ProtocolDescriptor> withInfo = filterByDataSendProperty(
            clientCapture.incoming(), ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY);
        assertTrue("client must receive at least one descriptor carrying aggregationPeriodInfo",
            !withInfo.isEmpty());
        MessageDescriptor last = withInfo.get(withInfo.size() - 1).getSend(MessageType.forData(QDContract.TICKER));
        assertNotNull(last);
        String info = last.getProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY);
        assertEquals(TimePeriodInfo.valueOf(3_000, 5_000), TimePeriodInfo.valueOf(info));
    }

    // ---- Async realm / handler builders ----

    private QDAuthRealm gatedRealm(CountDownLatch started, CountDownLatch release) {
        return new QDAuthRealm() {
            @Override
            public Promise<AuthSession> authenticate(AuthToken token, TypedMap vars) {
                if (!PASSWORD.equals(token.getPassword()))
                    return Promise.failed(new SecurityException("Bad credentials"));
                Promise<AuthSession> p = new Promise<>();
                started.countDown();
                asyncAuth.submit(() -> awaitAndComplete(release, p, () -> sessionWithChannels(token)));
                return p;
            }
            @Override
            public String getAuthenticationInfo() {
                return testId;
            }
        };
    }

    private QDLoginHandler gatedLoginHandler(CountDownLatch started, CountDownLatch release) {
        return new QDLoginHandler() {
            @Override
            public Promise<AuthToken> login(String reason) {
                if (!reason.endsWith(testId))
                    return Promise.failed(new SecurityException("Bad credentials"));
                Promise<AuthToken> p = new Promise<>();
                started.countDown();
                asyncAuth.submit(() -> awaitAndComplete(release, p,
                    () -> AuthToken.createBasicToken(USER, PASSWORD)));
                return p;
            }
            @Override
            public AuthToken getAuthToken() {
                return null;
            }
        };
    }

    private static <T> void awaitAndComplete(CountDownLatch release, Promise<T> p, Supplier<T> value) {
        try {
            if (!release.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                p.completeExceptionally(new IllegalStateException("release latch timeout"));
            else
                p.complete(value.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.completeExceptionally(e);
        }
    }

    // ---- Local filters ----

    private static List<ProtocolDescriptor> filterByDataReceiveProperty(
        List<ProtocolDescriptor> descriptors, String property)
    {
        return descriptors.stream()
            .filter(d -> {
                MessageDescriptor md = d.getReceive(MessageType.forData(QDContract.TICKER));
                return md != null && md.getProperty(property) != null
                    && !md.getProperty(property).isEmpty();
            })
            .collect(Collectors.toList());
    }

    private static List<ProtocolDescriptor> filterByDataSendProperty(
        List<ProtocolDescriptor> descriptors, String property)
    {
        return descriptors.stream()
            .filter(d -> {
                MessageDescriptor md = d.getSend(MessageType.forData(QDContract.TICKER));
                return md != null && md.getProperty(property) != null
                    && !md.getProperty(property).isEmpty();
            })
            .collect(Collectors.toList());
    }
}
