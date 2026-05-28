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
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Wire-level coverage for the authentication failure recovery path documented in
 * {@code com.devexperts.qd.qtp.auth} package-info.
 *
 * <p>Companion to {@link DescriptorDeltaEmissionTest} (post-login re-send) and
 * {@link AuthAndAggregationLiveUpdateTest} (happy path + implicit re-challenge dedup via
 * {@code realm.calls == 1}). The scenarios covered here:
 * <ul>
 *   <li>Server's realm rejects the first token → server emits the failure message on
 *       {@code authentication} → client re-runs its login handler with the error text →
 *       fresh token is accepted and data flows.</li>
 *   <li>The non-empty failure value reaches the client on the wire and the failure-window
 *       descriptor carries no {@code authorization} (server-side branch).</li>
 *   <li>Token expiry: server-side {@code AuthSession.close()} drops the connection (via the
 *       {@code SessionCloseListener} installed in {@code AuthManager.successSync}); the
 *       client's connector reconnects on its default {@code reconnectDelay} and the handler
 *       supplies a fresh token on the new handshake. In-connection re-auth on a still-open
 *       session is NOT supported — {@code AuthManager.successSync} guards with
 *       {@code if (this.session != null) return false;}, so a second {@code realm.authenticate}
 *       success cannot promote the manager from {@code AUTHENTICATE} back to {@code AUTH_OK}.
 *       This test exercises the close-and-reconnect path that IS supported.</li>
 * </ul>
 */
public class AuthFailureRecoveryTest extends AbstractAggregationTest {

    private static final String FAIL_REASON = "Bad credentials";
    private static final String WRONG_PASSWORD = "wrong-password";

    /**
     * Full failure-then-success loop. The realm rejects the first token with
     * {@code SecurityException("Bad credentials")}; the client's handler is re-invoked with
     * the error text as {@code reason} and supplies the correct password on retry.
     */
    @Test
    public void testServerRejectsBadCredentialsThenClientRetriesAndSucceeds() throws Exception {
        CountingRealm realm = counting(tokenChannelsRealm());
        RetryingLoginHandler handler = new RetryingLoginHandler();

        startServer("", realm);
        startClient("", handler);
        awaitFirstData("failure recovery never completed");

        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .until(() -> realm.calls.get() == 2);
        assertEquals("login handler should be invoked exactly twice (initial + retry-on-error)",
            2, handler.attempts.get());
        assertTrue("first login reason must be the server's challenge",
            handler.reasons.get(0).startsWith(MessageAdapter.AUTHENTICATION_LOGIN_REQUIRED));
        assertEquals("retry reason must be the realm's failure message",
            FAIL_REASON, handler.reasons.get(1));
    }

    /**
     * Wire-level: the failure descriptor crossing back to the client must carry
     * {@code authentication=<reason>} (non-empty, not a {@code LOGIN ...} re-prompt).
     */
    @Test
    public void testFailureErrorReachesClientOnWire() throws Exception {
        CountingRealm realm = counting(tokenChannelsRealm());
        RetryingLoginHandler handler = new RetryingLoginHandler();

        startServer("", realm);
        startClient("", handler);
        awaitFirstData("failure recovery never completed");

        boolean sawFailureOnWire = clientCapture.incoming().stream()
            .map(d -> d.getProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY))
            .anyMatch(v -> FAIL_REASON.equals(v));
        assertTrue("client must receive an incoming descriptor carrying authentication=\""
            + FAIL_REASON + "\"", sawFailureOnWire);
    }

    /**
     * Token expiry → close-and-reconnect → fresh auth. The realm captures the live session
     * and the test closes it explicitly to simulate token expiry. The client's connector
     * reconnects on its default {@code reconnectDelay}; the handler is invoked again on the
     * new connection's {@code DESCRIBE_PROTOCOL} challenge and returns a fresh token. The
     * realm sees a second {@code authenticate} call on the new {@code MessageAdapter}.
     */
    @Test
    public void testSessionExpiryTriggersReconnectAndFreshTokenAuth() throws Exception {
        AtomicReference<AuthSession> liveSession = new AtomicReference<>();
        QDAuthRealm capturing = new QDAuthRealm() {
            @Override
            public Promise<AuthSession> authenticate(AuthToken token, TypedMap vars) {
                if (!PASSWORD.equals(token.getPassword()))
                    return Promise.failed(new SecurityException(FAIL_REASON));
                AuthSession s = sessionWithChannels(token);
                liveSession.set(s);
                return Promise.completed(s);
            }
            @Override
            public String getAuthenticationInfo() {
                return testId;
            }
        };
        CountingRealm realm = counting(capturing);
        AtomicInteger logins = new AtomicInteger();
        QDLoginHandler refreshingHandler = new QDLoginHandler() {
            @Override
            public Promise<AuthToken> login(String reason) {
                logins.incrementAndGet();
                return Promise.completed(AuthToken.createBasicToken(USER, PASSWORD));
            }
            @Override
            public AuthToken getAuthToken() {
                return null;
            }
        };

        startServer("", realm);
        startClient("reconnectDelay=100", refreshingHandler);
        awaitFirstData("initial handshake stuck");

        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(() -> liveSession.get() != null);
        AuthSession expired = liveSession.getAndSet(null);
        assertNotNull("realm must have captured the live session", expired);
        expired.close("Token expired");

        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(() -> realm.calls.get() >= 2);
        assertTrue("login handler must be re-invoked on reconnect, got " + logins.get(),
            logins.get() >= 2);
        assertTrue("realm must accept the fresh token on the new connection, calls=" + realm.calls.get(),
            realm.calls.get() >= 2);
    }

    private class RetryingLoginHandler implements QDLoginHandler {
        final AtomicInteger attempts = new AtomicInteger();
        final List<String> reasons = new CopyOnWriteArrayList<>();

        @Override
        public Promise<AuthToken> login(String reason) {
            reasons.add(reason);
            int n = attempts.incrementAndGet();
            String password = (n == 1) ? WRONG_PASSWORD : PASSWORD;
            return Promise.completed(AuthToken.createBasicToken(USER, password));
        }

        @Override
        public AuthToken getAuthToken() {
            return null;
        }
    }
}
