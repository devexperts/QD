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
package com.devexperts.rmi.test.auth;

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.qtp.auth.BasicChannelShaperFactory;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDAuthRealmFactory;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.auth.QDLoginHandlerFactory;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.test.NTU;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.devexperts.util.Base64;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TypedMap;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class AuthorizationTest {
    private static final Logging log = Logging.getLogging(AuthorizationTest.class);

    private static final AuthToken BAD_USER = AuthToken.createBasicToken("Petr", "123456");
    private static final AuthToken GOOD_USER = AuthToken.createBasicToken("Ivan", "ivan");

    private DXPublisher publisher;

    private static final int ERROR_LOGIN_COUNT = 3;
    private static final int COUNT = 10;
    private static final String CONFIG_NAME = "AuthRealm.config";

    private static String serverSubject;

    private volatile Thread waitingThread;
    private volatile boolean readyToChange = true;
    private DXEndpoint dxClientEndpoint;
    private DXEndpoint dxServerEndpoint;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    static {
        char[] b = new char[10];
        Random rnd = new Random(35621622366674225L);
        for (int i = 0; i < 10; i++)
            b[i] = (char) ('A' + rnd.nextInt('z' - 'A'));
        serverSubject = new String(b);
    }

    private RMIEndpoint server;
    private RMIEndpoint client;

    private SimpleAuthServer serverAuth;

    private CountDownLatch recordsLatch = new CountDownLatch(COUNT);
    private CountDownLatch receiveQuote = new CountDownLatch(1);

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        client = RMIEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.FEED)
            .withSide(RMIEndpoint.Side.CLIENT)
            .withName("Client")
            .build();
        server = RMIEndpoint.newBuilder()
            .withName("Server")
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
    }

    @After
    public void tearDown() {
        System.out.println(" -- 1 -- ");
        client.close();
        System.out.println(" -- 2 -- ");
        server.close();
        System.out.println(" -- 3 -- ");
        dxClientEndpoint.close();
        System.out.println(" -- 4 -- ");
        dxServerEndpoint.close();
        System.out.println(" -- 5 -- ");
        if (serverAuth != null)
            serverAuth.close();
        System.out.println(" -- 6 -- ");
        ThreadCleanCheck.after();
    }

    @Test
    public void testAuth() throws Exception {
        String serverAddress = initServerSimple("auth=" + GOOD_USER.getUser() + ":" + GOOD_USER.getPassword());
        String clientAddress =
            serverAddress + "[user=" + GOOD_USER.getUser() + ",password=" + GOOD_USER.getPassword() + "]";
        initClient(clientAddress, "IBM");
        startUpdateQuotes(true, true, COUNT, new Quote("IBM"));
        if (!recordsLatch.await(10, TimeUnit.SECONDS)) {
            log.info("" + recordsLatch.getCount());
            fail();
        }
    }

    @Test
    public void testBadAuth() throws Exception {
        String serverAddress = initServerSimple("auth=" + GOOD_USER.getUser() + ":" + GOOD_USER.getPassword());
        String clientAddress =
            serverAddress + "[user=" + BAD_USER.getUser() + ",password=" + BAD_USER.getPassword() + "]";
        initClient(clientAddress, "IBM");
        startUpdateQuotes(false, false, COUNT, new Quote("IBM"));
        Thread.sleep(2000);
        assertEquals(recordsLatch.getCount(), COUNT);
    }

    @Test
    public void testCustomAuth() throws InterruptedException {
        serverAuth = new SimpleAuthServer(serverSubject, BAD_USER, GOOD_USER);
        String serverAddress = initServerSimple(
            "auth=" + AuthFactory.PREFIX + ":" + serverAuth.getAddress() + ";" + serverSubject);
        String clientAddress = "(" + serverAddress +
            "[login=" + LoginFactory.PREFIX + ":" + BAD_USER + ";" + GOOD_USER + ";" + ERROR_LOGIN_COUNT + "])" +
            "(" + serverAuth.getAddress() + ")";
        initClient(clientAddress, "IBM");
        startUpdateQuotes(true, true, COUNT, new Quote("IBM"));
        if (!recordsLatch.await(10, TimeUnit.SECONDS)) {
            log.info("" + recordsLatch.getCount());
            fail();
        }
        assertEquals(ERROR_LOGIN_COUNT, serverAuth.getErrorLoginCount());
    }

    @Test
    public void testBasicAuthRealmFromFile() throws Exception {
        File file = new File(tempFolder.getRoot(), CONFIG_NAME);

        receiveQuote = new CountDownLatch(2);
        recordsLatch = new CountDownLatch(20);
        Files.write(file.toPath(), Arrays.asList("user2:qwerty:*", "user3:vfr3we:TICKER"));
        String name1 = UUID.randomUUID().toString();
        String name2 = UUID.randomUUID().toString();
        //noinspection deprecation
        Promise<Integer> p1 = ServerSocketTestHelper.createPortPromise(name1);
        //noinspection deprecation
        Promise<Integer> p2 = ServerSocketTestHelper.createPortPromise(name2);
        String serverAddress = "(:0[name=" + name1 + ",auth=" + file.getPath() + "])" +
            "(:0[name=" + name2 + ",auth=" + file.getPath() + "])";
        initServer(serverAddress);
        int port1 = p1.await(10, TimeUnit.SECONDS);
        int port2 = p2.await(10, TimeUnit.SECONDS);

        String clientAddress = "(" + NTU.localHost(port1) + "[user=user1,password=123456])" +
            "(" + NTU.localHost(port2) + "[login=user3:vfr3we])";
        initClient(clientAddress, "IBM", "YAHOO");
        startUpdateQuotes(true, true, 2 * COUNT, new Quote("IBM"), new Quote("YAHOO"));
        if (recordsLatch.await(20, TimeUnit.SECONDS)) {
            log.info("" + recordsLatch.getCount());
        } else {
            fail();
        }
    }

    @Test
    public void testAll() throws Exception {
        File file = new File(tempFolder.getRoot(), CONFIG_NAME);

        globalUsersCount = 4;
        RMIEndpoint serverAuth = RMIEndpoint.newBuilder()
            .withName("AUTH SERVER")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        RMIEndpoint firstBasicClient = RMIEndpoint.newBuilder()
            .withName("BASIC CLIENT OWN")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        RMIEndpoint secondBasicClient = RMIEndpoint.newBuilder()
            .withName("BASIC CLIENT TWO")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        serverAuth.getServer().export(new FixedUsersAuthServiceImpl(globalUsersCount), FixedUsersAuthService.class);

        String authAddress = NTU.localHost(NTU.connectServer(serverAuth));
        log.info("first part");
        Files.write(file.toPath(), Arrays.asList("user1:123456", "user2:qwerty:*", "user3:vfr3we:TICKER"));

        String[] names = Stream.generate(() -> UUID.randomUUID().toString()).limit(6).toArray(String[]::new);

        List<Promise<Integer>> promises =
            Arrays.stream(names).map(ServerSocketTestHelper::createPortPromise).collect(Collectors.toList());

        String serverAddress =
            "(:0[name=" + names[0] + ",auth=" + FixedUsersAuthRealmFactory.PREFIX + ":" + authAddress + "])" +
            "(:0[name=" + names[1] + ",auth=" + FixedUsersAuthRealmFactory.PREFIX + ":" + authAddress + "])" +
            "(:0[name=" + names[2] + ",auth=" + FixedUsersAuthRealmFactory.PREFIX + ":" + authAddress + "])" +
            "(:0[name=" + names[3] + ",auth=" + FixedUsersAuthRealmFactory.PREFIX + ":" + authAddress + "])" +
            "(:0[name=" + names[4] + ",auth=" + file.getPath() + "])" +
            "(:0[name=" + names[5] + ",auth=" + file.getPath() + "])";
        initServer(serverAddress);

        int[] ports = promises.stream().mapToInt((p) -> p.await(10, TimeUnit.SECONDS)).toArray();

        String clientAddress =
            "(" + NTU.localHost(ports[0]) + "[login=" + FixedUsersLoginHandlerFactory.PREFIX + "])" +
            "(" + NTU.localHost(ports[1]) + "[login=" + FixedUsersLoginHandlerFactory.PREFIX + "])" +
            "(" + NTU.localHost(ports[2]) + "[login=" + FixedUsersLoginHandlerFactory.PREFIX + "])" +
            "(" + NTU.localHost(ports[3]) + "[login=" + FixedUsersLoginHandlerFactory.PREFIX + "])" +
            "(" + authAddress + ")";

        NTU.connect(firstBasicClient, NTU.localHost(ports[4]) + "[user=user1,password=123456]");
        initClient(clientAddress);
        NTU.connect(secondBasicClient, NTU.localHost(ports[5]) + "[login=user2:qwerty]");

        assertTrue(FixedUsersLoginHandler.usersLoginOk.await(10, TimeUnit.SECONDS));
        assertTrue(FixedUsersAuthRealm.usersLoginOk.await(10, TimeUnit.SECONDS));
        serverAuth.close();
        firstBasicClient.close();
        secondBasicClient.close();
        assertEquals(globalUsersCount, FixedUsersLoginHandler.usersCount.get());
        assertEquals(globalUsersCount, FixedUsersAuthRealm.usersCount.get());
    }

    public static class FixedUsersLoginHandlerFactory implements QDLoginHandlerFactory {
        static final String PREFIX = "FixesUsersLogin";

        @Override
        public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
        {
            if (!login.startsWith(PREFIX))
                return null;
            return new FixedUsersLoginHandler(factory.getEndpoint(RMIEndpoint.class));
        }
    }

    static class FixedUsersLoginHandler implements QDLoginHandler {
        private static final Logging innerLog = Logging.getLogging(FixedUsersLoginHandler.class);

        private final RMIEndpoint client;
        static final AtomicInteger usersCount = new AtomicInteger();
        static final CountDownLatch usersLoginOk = new CountDownLatch(1);

        FixedUsersLoginHandler(RMIEndpoint client) {
            this.client = client;
        }

        @Override
        public Promise<AuthToken> login(String reason) {
            RMIRequest<AuthToken> request = client.getClient().getPort(null).createRequest(
                FixedUsersAuthServiceImpl.REGISTERED_USER);
            Promise<AuthToken> tokenPromise = request.getPromise();
            tokenPromise.whenDone(promise -> {
                if (promise.isCancelled())
                    innerLog.info("PROMISE CLOSE");
                else
                    innerLog.info("PROMISE DONE");
            });
            request.setListener(req -> {
                if (request.getState() == RMIRequestState.SUCCEEDED) {
                    innerLog.info("REQUEST DONE (" + req.getNonBlocking()+ ")");
                    if (req.getNonBlocking() != null)
                        if (usersCount.incrementAndGet() == globalUsersCount)
                            usersLoginOk.countDown();
                } else {
                    innerLog.info("REQUEST CLOSE");
                }
            });
            request.send();
            return tokenPromise;
        }

        @Override
        public AuthToken getAuthToken() {
            return null;
        }
    }

    public static class FixedUsersAuthRealmFactory implements QDAuthRealmFactory {
        static final String PREFIX = "FixesUsersAuth";

        @Override
        public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
        {
            if (!auth.startsWith(PREFIX))
                return null;
            return new FixedUsersAuthRealm(auth.substring(auth.indexOf(':') + 1));
        }
    }

    private static int globalUsersCount;

    static class FixedUsersAuthRealm implements QDAuthRealm {
        private static final Logging innerLog = Logging.getLogging(FixedUsersAuthRealm.class);

        static final AtomicInteger usersCount = new AtomicInteger();
        static final CountDownLatch usersLoginOk = new CountDownLatch(1);

        private int refCount;
        private RMIEndpoint endpoint;

        private final String address;

        FixedUsersAuthRealm(String address) {
            this.address = address;
        }

        private synchronized RMIEndpoint referenceEndpoint() {
            refCount++;
            if (endpoint != null)
                return endpoint;
            innerLog.info("Creating connection to authentication endpoint");
            endpoint = RMIEndpoint.newBuilder()
                .withName("RealmClient ")
                .withSide(RMIEndpoint.Side.CLIENT)
                .build();
            NTU.connect(endpoint,address);
            return endpoint;
        }

        private synchronized void dereferenceEndpoint() {
            if (--refCount == 0) {
                innerLog.info("Closing connection to authentication endpoint");
                endpoint.close();
                endpoint = null;
            }
        }

        @Override
        public Promise<AuthSession> authenticate(AuthToken authToken, TypedMap connectionVariables) {
            RMIRequest<Boolean> request = referenceEndpoint().getClient().getPort(null).createRequest(
                FixedUsersAuthServiceImpl.CHECK_USER, authToken);
            Promise<AuthSession> sessionPromise = new Promise<>();
            sessionPromise.whenDone(promise -> {
                if (promise.isCancelled()) {
                    innerLog.info("PROMISE CLOSE");
                    request.cancelOrAbort();
                } else {
                    innerLog.info("PROMISE DONE");
                }
                dereferenceEndpoint();
            });
            request.setListener(req -> {
                if (request.getState() == RMIRequestState.SUCCEEDED) {
                    innerLog.info("REQUEST DONE");
                    if (req.getNonBlocking() != null || (Boolean) req.getNonBlocking()) {
                        int temp = usersCount.incrementAndGet();
                        AuthSession session = new AuthSession(authToken);
                        session.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY,
                            BasicChannelShaperFactory.ALL_DATA);
                        sessionPromise.complete(session);
                        if (temp == globalUsersCount)
                            usersLoginOk.countDown();
                    }
                } else {
                    sessionPromise.completeExceptionally(req.getException());
                    innerLog.info("REQUEST CLOSE");
                }
            });
            request.send();
            return sessionPromise;
        }

        @Override
        public String getAuthenticationInfo() {
            return "You must login";
        }
    }

    static interface FixedUsersAuthService {
        public AuthToken registeredUser();
        public boolean checkUserRegistration(AuthToken token);
    }

    static class FixedUsersAuthServiceImpl implements FixedUsersAuthService {
        private static final Logging innerLog = Logging.getLogging(FixedUsersAuthServiceImpl.class);

        static final RMIOperation<AuthToken> REGISTERED_USER =
            RMIOperation.valueOf(FixedUsersAuthService.class, AuthToken.class, "registeredUser");
        static final RMIOperation<Boolean> CHECK_USER =
            RMIOperation.valueOf(FixedUsersAuthService.class, boolean.class, "checkUserRegistration", AuthToken.class);

        static final int TOKEN_LENGTH = 16;
        private final int maxCount;
        private int currentCount = 0;
        private final Set<AuthToken> tokens = new ConcurrentHashSet<>();
        private final Random rnd = new Random(System.currentTimeMillis());

        FixedUsersAuthServiceImpl(int maxCount) {
            this.maxCount = maxCount;
        }

        //only for LoginHandler
        @Override
        public AuthToken registeredUser() {
            synchronized (this) {
                if (currentCount == maxCount)
                    return null;
                currentCount++;
                innerLog.info("Registered user №" + currentCount);
            }
            AuthToken token = generateToken();
            innerLog.info("user token: " + token);
            tokens.add(token);
            return token;
        }

        //only for AuthRealm
        @Override
        public synchronized boolean checkUserRegistration(AuthToken token) {
            boolean check = tokens.contains(token);
            innerLog.info("check " + token + " user:" + check);
            return check;
        }

        private AuthToken generateToken() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                int number = rnd.nextInt(Base64.DEFAULT.getAlphabet().length());
                char ch = Base64.DEFAULT.getAlphabet().charAt(number);
                sb.append(ch);
            }
            return AuthToken.createBearerToken(sb.toString());
        }
    }
    //-------------------------------------------------------------

    @Test
    public void testCloseAuthContextOnRealm() throws InterruptedException {
        String serverAddress = initServerSimple("auth=" + CloseCountAuthRealmFactory.FACTORY_NAME);
        String clientAddress = serverAddress + "[user=Ivan,password=ivan]";
        initClient(clientAddress, "IBM");
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.FEED);
        NTU.connect(endpoint, serverAddress + "[user=Ivan,password=ivan]");

        assertTrue(CloseCountAuthRealm.firstStart.await(10, TimeUnit.SECONDS));
        assertTrue(CloseCountAuthRealm.secondStart.await(10, TimeUnit.SECONDS));
        endpoint.disconnect();
        assertTrue(((DXEndpointImpl) endpoint).getQDEndpoint().getConnectors().isEmpty());
        assertTrue(CloseCountAuthRealm.firstClose.await(10, TimeUnit.SECONDS));
        assertEquals(1, CloseCountAuthRealm.secondClose.getCount());
        client.disconnect();
        assertTrue(((RMIEndpointImpl) client).getQdEndpoint().getConnectors().isEmpty());
        assertTrue(CloseCountAuthRealm.secondClose.await(10, TimeUnit.SECONDS));
        endpoint.close();
    }

    @Test
    public void testCloseAuthContextOnLoginHandler() throws InterruptedException {
        String name60 = UUID.randomUUID().toString();
        String name65 = UUID.randomUUID().toString();
        Promise<Integer> p60 = ServerSocketTestHelper.createPortPromise(name60);
        Promise<Integer> p65 = ServerSocketTestHelper.createPortPromise(name65);

        String clientAddress =
            "(:0[name=" + name60 + ",login=" + CloseLoginHandlerFactory.FACTORY_NAME + "good])" +
            "(:0[name=" + name65 + ",login=" + CloseLoginHandlerFactory.FACTORY_NAME + "bad])";
        initClient(clientAddress, "IBM");

        int port60 = p60.await(10, TimeUnit.SECONDS);
        int port65 = p65.await(10, TimeUnit.SECONDS);

        String serverAddress = NTU.localHost(port60) + "[auth=user:password]";
        initServer(serverAddress);
        assertTrue(CloseLoginHandler.firstStart.await(10, TimeUnit.SECONDS));
        assertEquals(1, CloseLoginHandler.start.get());
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
        NTU.connect(endpoint, NTU.localHost(port65) + "[auth=user:password]");
        assertTrue(CloseLoginHandler.secondStart.await(10, TimeUnit.SECONDS));
        assertEquals(2, CloseLoginHandler.start.get());
        endpoint.close();
        assertTrue(CloseLoginHandler.firstClose.await(10, TimeUnit.SECONDS));
        assertEquals(1, CloseLoginHandler.secondClose.getCount());
        assertTrue(((DXEndpointImpl) endpoint).getQDEndpoint().getConnectors().isEmpty());
        client.disconnect();
        assertTrue(((RMIEndpointImpl) client).getQdEndpoint().getConnectors().isEmpty());
        assertTrue(CloseLoginHandler.secondClose.await(10, TimeUnit.SECONDS));
        assertEquals(2, CloseLoginHandler.start.get());
    }

    public static class CloseLoginHandlerFactory implements QDLoginHandlerFactory {
        private static final String FACTORY_NAME = "updater_basic";

        @Override
        public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
        {
            return login.startsWith(FACTORY_NAME) ? new CloseLoginHandler() : null;
        }
    }

    private static class CloseLoginHandler implements QDLoginHandler {

        private static final Logging innerLog = Logging.getLogging(CloseLoginHandler.class);


        private static AtomicInteger start = new AtomicInteger();
        private static CountDownLatch firstStart = new CountDownLatch(1);
        private static CountDownLatch secondStart = new CountDownLatch(1);
        private static CountDownLatch firstClose = new CountDownLatch(1);
        private static CountDownLatch secondClose = new CountDownLatch(1);


        @Override
        public Promise<AuthToken> login(String reason) {
            innerLog.info("START LOGIN");
            synchronized (this) {
                start.getAndIncrement();
                if (firstStart.getCount() != 0)
                    firstStart.countDown();
                else
                    secondStart.countDown();
            }
            Promise<AuthToken> tokenPromise = new Promise<>();
            tokenPromise.whenDone(promise -> {
                if (promise.isCancelled()) {
                    innerLog.info("PROMISE CLOSE");
                    if (firstClose.getCount() != 0)
                        firstClose.countDown();
                    else
                        secondClose.countDown();
                } else {
                    innerLog.info("PROMISE COMPLETE");
                }
            });
            return tokenPromise;
        }

        @Override
        public AuthToken getAuthToken() {
            return null;
        }
    }

    public static class CloseCountAuthRealmFactory implements QDAuthRealmFactory {

        private static final String FACTORY_NAME = "close_factory";

        @Override
        public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
        {
            return auth.equals(FACTORY_NAME) ? new CloseCountAuthRealm() : null;
        }
    }

    private static class CloseCountAuthRealm implements QDAuthRealm {

        private static final Logging innerLog = Logging.getLogging(CloseCountAuthRealm.class);

        private static CountDownLatch firstStart = new CountDownLatch(1);
        private static CountDownLatch secondStart = new CountDownLatch(1);
        private static CountDownLatch firstClose = new CountDownLatch(1);
        private static CountDownLatch secondClose = new CountDownLatch(1);

        @Override
        public Promise<AuthSession> authenticate(AuthToken authToken, TypedMap connectionVariables) {
            innerLog.info("START AUTH");
            synchronized (this) {
                if (firstStart.getCount() != 0)
                    firstStart.countDown();
                else
                    secondStart.countDown();
            }
            Promise<AuthSession> sessionPromise = new Promise<>();
            sessionPromise.whenDone(promise -> {
                if (promise.isCancelled()) {
                    innerLog.info("PROMISE CLOSE");
                    if (firstClose.getCount() != 0)
                        firstClose.countDown();
                    else
                        secondClose.countDown();
                } else {
                    innerLog.info("PROMISE COMPLETE");
                }
            });
            return sessionPromise;
        }

        @Override
        public String getAuthenticationInfo() {
            return "Required";
        }
    }

    public static class RepeatAuthRealmFactory implements QDAuthRealmFactory {
        private static final String FACTORY_NAME = "repeat_factory";

        @Override
        public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
        {
            return auth.equals(FACTORY_NAME) ? new RepeatAuthRealm() : null;
        }
    }

    private static class RepeatAuthRealm implements QDAuthRealm {
        private final Map<AuthToken, AuthSession> sessions = new HashMap<>();

        @Override
        public Promise<AuthSession> authenticate(AuthToken authToken, TypedMap connectionVariables) {
            log.info("authenticate 1)" + authToken);
            log.info("authenticate 2)" + GOOD_USER);
            log.info("authenticate 3)" + BAD_USER);
            AuthSession session = getSession(authToken);
            Promise<AuthSession> sessionPromise = new Promise<>();
            sessionPromise.whenDone(promise -> sessions.clear());
            if (session == null) {
                sessionPromise.completeExceptionally(
                    new Exception("REPEAT " + (authToken.equals(GOOD_USER) ? "good" : "bad")));
            } else {
                sessionPromise.complete(session);
            }
            return sessionPromise;
        }

        @Override
        public String getAuthenticationInfo() {
            return "Required";
        }

        private AuthSession getSession(AuthToken accessToken) throws SecurityException {
            AuthSession session = sessions.get(accessToken);
            if (session == null) {
                session = new AuthSession(accessToken);
                session.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY,
                    BasicChannelShaperFactory.ALL_DATA);
                return sessions.put(accessToken, session);
            }
            return session;
        }
    }

    //-------------------------------------------------------------

    private void initClient(String clientAddress, String... symbols) {
        dxClientEndpoint = client.getDXEndpoint();
        client.getClient().getService("*").addServiceDescriptorsListener(list -> System.out.println("ADV = " + list));
        DXFeed feed = dxClientEndpoint.getFeed();
        if (symbols != null) {
            DXFeedSubscription<MarketEvent> sub = feed.createSubscription(Quote.class);
            sub.addEventListener(new DXFeedEventListener<MarketEvent>() {
                @Override
                public void eventsReceived(List<MarketEvent> events) {
                    for (final MarketEvent event : events) {
                        if (event instanceof Quote) {
                            if (receiveQuote.getCount() > 0) {
                                log.info("First event " + event);
                                receiveQuote.countDown();
                                continue;
                            }
                            log.info("Tick " + event);
                            log.info("Tick " + recordsLatch.getCount());
                            synchronized (this) {
                                recordsLatch.countDown();
                                if (recordsLatch.getCount() % symbols.length == 0) {
                                    readyToChange = true;
                                    if (waitingThread != null)
                                        LockSupport.unpark(waitingThread);
                                }
                                if (recordsLatch.getCount() == 0)
                                    sub.removeEventListener(this);
                            }
                        }
                    }
                }
            });
            log.info(" symbols = " + Arrays.toString(symbols));
            sub.addSymbols((Object[]) symbols);
        }
        NTU.connect(client, clientAddress);
    }


    private void initServer(String serverAddress) {
        NTU.connect(server, serverAddress);
        log.info("--- initServer --- ");
        dxServerEndpoint = server.getDXEndpoint();
        publisher = dxServerEndpoint.getPublisher();
    }

    private String initServerSimple(String opts) {
        int port = NTU.connectServer(server, null, opts);
        log.info("--- initServer --- ");
        dxServerEndpoint = server.getDXEndpoint();
        publisher = dxServerEndpoint.getPublisher();
        return NTU.localHost(port);
    }

    private void startUpdateQuotes(boolean waitSub, boolean updateQuote, int quoteUpdateCount, Quote... quotes)
        throws InterruptedException
    {
        if (waitSub) {
            CountDownLatch subReceived = new CountDownLatch(quotes.length);
            publisher.getSubscription(Quote.class).addChangeListener(sub -> {
                for (Object o : sub) {
                    subReceived.countDown();
                }
            });
            assertTrue(subReceived.await(5, TimeUnit.SECONDS));
        }
        for (Quote quote : quotes)
            quote.setBidPrice(100);
        publisher.publishEvents(Arrays.asList(quotes));
        if (updateQuote)
            updateQuote(quoteUpdateCount, quotes);
    }

    private void updateQuote(int quoteUpdateCount, Quote... quotes) throws InterruptedException {
        if (!receiveQuote.await(10, TimeUnit.SECONDS)) {
            log.info("receiveQuote = " + receiveQuote.getCount());
            fail();
        }
        int counter = 0;
        waitingThread = Thread.currentThread();
        while (counter < quoteUpdateCount) {
            long startTime = System.currentTimeMillis();
            while (!readyToChange) {
                LockSupport.parkNanos(100_0000_000);
                if (System.currentTimeMillis() > startTime + (10 * 1000))
                    fail();
            }
            Random random = new Random();
            readyToChange = false;
            for (Quote quote : quotes) {
                quote.setBidPrice(random.nextDouble() * 100);
                log.info("counter = " + counter);
                log.info("publish = " + quote);
                publisher.publishEvents(Collections.singletonList(quote));
                counter++;
            }
        }
    }
}
