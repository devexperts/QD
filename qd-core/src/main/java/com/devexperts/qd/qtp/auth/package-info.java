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

/**
 * QTP authentication and authorization, modeled after HTTP Basic Auth (RFC 7617).
 *
 * <h2>Wire protocol</h2>
 *
 * Two {@link com.devexperts.qd.qtp.ProtocolDescriptor} properties carry auth state:
 * <ul>
 *   <li>{@code authentication} — sent by server: challenge, error, or success signal</li>
 *   <li>{@code authorization}  — sent by client: credentials token</li>
 * </ul>
 *
 * <h3>Property value semantics</h3>
 * <table>
 *   <tr><th>Property</th><th>Value</th><th>Meaning</th></tr>
 *   <tr><td>{@code authentication}</td><td>{@code "LOGIN <realm>"}</td>
 *       <td>Server challenges client (like HTTP {@code WWW-Authenticate})</td></tr>
 *   <tr><td>{@code authentication}</td><td>non-empty error string</td>
 *       <td>Server reports auth failure</td></tr>
 *   <tr><td>{@code authentication}</td><td>{@code ""} (empty)</td>
 *       <td>Server confirms success (like HTTP {@code 200 OK})</td></tr>
 *   <tr><td>{@code authentication}</td><td>absent</td>
 *       <td>No change; receiver retains last value</td></tr>
 *   <tr><td>{@code authorization}</td><td>token string</td>
 *       <td>Client sends credentials (like HTTP {@code Authorization})</td></tr>
 * </table>
 *
 * <p>Properties <b>accumulate</b> across {@code DESCRIBE_PROTOCOL} messages within a connection.
 * A property not present in a new message survives from the previous descriptor.
 * The only way to clear a property on the remote side is to send an explicit replacement value.
 *
 * <h2>Protocol exchange</h2>
 *
 * <h3>Happy path</h3>
 * <pre>
 * Server                                        Client
 *   |                                             |
 *   |--- authentication="LOGIN MyRealm" ---------&gt;|   (challenge)
 *   |                                             |
 *   |      [QDLoginHandler.login() -&gt; Promise]    |
 *   |                                             |
 *   |&lt;-- authorization="Basic dXNlcjpwYXNz" ------|   (credentials)
 *   |                                             |
 *   |    [QDAuthRealm.authenticate() -&gt; Promise]  |
 *   |                                             |
 *   |--- authentication="" ----------------------&gt;|   (success + data flow begins)
 *   |                                             |
 * </pre>
 *
 * <h3>Authentication failure</h3>
 * Server's {@code authenticate()} promise rejects: AuthManager moves to {@code AUTH_FAILED},
 * the failure message is sent as a non-empty {@code authentication} value. The client treats
 * any non-empty {@code authentication} value as a fresh login prompt and re-runs
 * {@link com.devexperts.qd.qtp.auth.QDLoginHandler#login login(reason)} with the error text.
 * <pre>
 * Server                                        Client
 *   |&lt;-- authorization="Basic dXNlcjpwYXNz" ------|
 *   |    [QDAuthRealm.authenticate() rejects]     |
 *   |--- authentication="bad credentials" -------&gt;|   (failure, NOT empty)
 *   |                                             |
 *   |      [QDLoginHandler.login("bad ...")]      |
 *   |&lt;-- authorization="Basic dXNlcjpwYXNzMg==" --|   (retry with new token)
 *   |                                             |
 * </pre>
 *
 * <h3>Idempotent re-challenge / re-send</h3>
 * The receiver accumulates properties across {@code DESCRIBE_PROTOCOL} messages, so a peer that
 * re-emits a property the other side has already processed must not re-invoke the
 * {@link com.devexperts.qd.qtp.auth.QDAuthRealm} / {@link com.devexperts.qd.qtp.auth.QDLoginHandler}
 * implementation.
 * On the server side {@code MessageAdapter} guards by {@code lastProcessedAuthToken}: an
 * {@code authorization} with the same token as the previous call is dropped before reaching
 * {@code authManager.authenticate()}. On the client side {@code LoginManager} guards by
 * {@code lastSendAccessToken} and ignores a repeated {@code "LOGIN ..."} challenge while
 * already in {@code LOGIN} / {@code WAITING_OTHER_SIDE} / {@code COMPLETED}.
 *
 * <h3>Post-login DESCRIBE_PROTOCOL re-send</h3>
 * After authentication completes, either side may re-emit {@code DESCRIBE_PROTOCOL} carrying
 * an unrelated delta (e.g. client-side {@code setRequestedAggregationPeriod}). The auth
 * properties are NOT re-attached: {@code LoginManager} skips writing {@code authorization}
 * while in {@code WAITING_OTHER_SIDE} / {@code COMPLETED}, and {@code MessageAdapter} writes
 * {@code authentication=""} on every outgoing descriptor only because the receiver already
 * processed it once and discards the redundant repeat.
 * <pre>
 * Server                                        Client
 *   |--- authentication="" ----------------------&gt;|   (auth complete)
 *   |&lt;-- requestedAggregationPeriod="1s" ---------|   (delta only, NO authorization re-sent)
 *   |                                             |
 * </pre>
 *
 * <h2>Server side</h2>
 *
 * <p>Implement {@link com.devexperts.qd.qtp.auth.QDAuthRealm} and register via
 * {@link com.devexperts.qd.qtp.auth.QDAuthRealmFactory} (SPI).
 * The realm's {@link com.devexperts.qd.qtp.auth.QDAuthRealm#authenticate authenticate} method
 * receives the client's token and returns a {@link com.dxfeed.promise.Promise} that resolves
 * to an {@link com.devexperts.auth.AuthSession} on success or an exception on failure.
 *
 * <p>Internally, {@code AuthManager} (package-private, in {@code com.devexperts.qd.qtp})
 * drives the server-side state machine. The on-wire {@code authentication} value is split
 * across two write sites: {@code AuthManager.prepareAuthenticate} writes the challenge
 * ({@code "LOGIN <realm>"}) and the failure string from its {@code reason} field, while
 * {@code MessageAdapter.prepareProtocolDescriptor} writes the empty success value directly
 * based on {@code AuthManager.isAuthComplete()}. Both writes are a pure function of
 * AuthManager's current state and are never consumed by the act of sending — the value
 * changes only via state transitions.
 *
 * <h2>Client side</h2>
 *
 * <p>Implement {@link com.devexperts.qd.qtp.auth.QDLoginHandler} and register via
 * {@link com.devexperts.qd.qtp.auth.QDLoginHandlerFactory} (SPI).
 * The handler's {@link com.devexperts.qd.qtp.auth.QDLoginHandler#login login} method
 * receives the server's challenge or error and returns a {@link com.dxfeed.promise.Promise}
 * that resolves to an {@link com.devexperts.auth.AuthToken}.
 *
 * <p>Internally, {@code LoginManager} (package-private, in {@code com.devexperts.qd.qtp})
 * drives the client-side state machine. The {@code authorization} value is written by
 * {@code LoginManager.prepareProtocolDescriptor} and, symmetric to the server side, is a
 * pure function of LoginManager's current state — once the token has been emitted, the
 * manager moves to {@code WAITING_OTHER_SIDE} / {@code COMPLETED} and stops re-attaching
 * the token to subsequent descriptors.
 *
 * @see com.devexperts.qd.qtp.auth.QDAuthRealm
 * @see com.devexperts.qd.qtp.auth.QDLoginHandler
 * @see com.devexperts.qd.qtp.ProtocolDescriptor
 */
package com.devexperts.qd.qtp.auth;
