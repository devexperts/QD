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
package com.devexperts.qd.qtp.auth;

import com.devexperts.auth.AuthToken;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

import java.io.Console;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.concurrent.GuardedBy;

@ServiceProvider
public class ConsoleLoginHandlerFactory implements QDLoginHandlerFactory {

    public static final String NAME = "console";

    private static final Lock CONSOLE_LOCK = new ReentrantLock();

    @Override
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
            throws InvalidFormatException
    {
        if (login.equalsIgnoreCase(NAME))
            return new ConsoleLoginHandler(factory.getUser(), factory.getPassword());
        return null;
    }

    private static class ConsoleLoginHandler implements QDLoginHandler {
        private static final String USER = "User: ";
        private static final String PASSWORD = "Password: ";
        private volatile AuthToken token;
        private String factoryUser;
        private String factoryPassword;

        @GuardedBy("this")
        private Promise<AuthToken> promise; // promise !=  null when we have a running ConsoleThread

        private ConsoleLoginHandler(String factoryUser, String factoryPassword) {
            this.factoryUser = factoryUser;
            this.factoryPassword = factoryPassword;
            if (!factoryUser.isEmpty() && !factoryPassword.isEmpty())
                token = AuthToken.createBasicToken(factoryUser, factoryPassword);
        }

        @Override
        public synchronized Promise<AuthToken> login(String reason) {
            if (promise != null)
                return promise;
            if (reason.startsWith(MessageAdapter.AUTHENTICATION_LOGIN_REQUIRED) && token != null) {
                Promise<AuthToken> promise = new Promise<>();
                promise.complete(token);
                return promise;
            }
            // Here is are not running console login yet, and we don't have a token or we are told that our token was invalid
            Thread t = new ConsoleThread(reason);
            promise = new Promise<AuthToken>() {
                @Override
                protected void handleDone(PromiseHandler<? super AuthToken> handler) {
                    t.interrupt();
                    promiseDone();
                    super.handleDone(handler);
                }
            };
            t.start();
            return promise;
        }

        private synchronized void promiseDone() {
            promise = null;
        }

        private synchronized void done(AuthToken token) {
            this.token = token;
            if (promise != null)
                promise.complete(token);
        }

        @Override
        public AuthToken getAuthToken() {
            return token;
        }

        class ConsoleThread extends Thread {
            private final String reason;

            ConsoleThread(String reason) {
                super("ConsoleLoginHandler-" + reason);
                this.reason = reason;
            }

            @Override
            public void run() {
                try {
                    CONSOLE_LOCK.lockInterruptibly();
                } catch (InterruptedException e) {
                    return; // don't need to login anymore -- promise was cancelled
                }
                try {
                    String user = factoryUser;
                    String password = factoryPassword;
                    Console console = System.console();
                    if (console == null) {
                        Scanner scanner = new Scanner(System.in);
                        System.out.println(reason);
                        if (user.isEmpty()) {
                            System.out.print(USER);
                            user = scanner.nextLine();
                        }
                        if (password.isEmpty()) {
                            System.out.print(PASSWORD);
                            password = scanner.nextLine();
                        }
                    } else {
                        console.format("%s%n", reason);
                        if (user.isEmpty())
                            user = console.readLine(USER);
                        if (password.isEmpty())
                            password = String.valueOf(console.readPassword(PASSWORD));
                    }
                    done(AuthToken.createBasicToken(user, password));
                } finally {
                    CONSOLE_LOCK.unlock();
                }
            }
        }
    }

}
