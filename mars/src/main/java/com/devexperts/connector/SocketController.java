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
package com.devexperts.connector;

import com.devexperts.util.TimeUtil;

import java.net.Socket;

/**
 * SocketController provides {@link Socket} instances for {@link SocketHandler} according to single
 * communication route (either listening for incoming connections or establishing outgoing one).
 */
abstract class SocketController {

    protected final Connector connector;

    protected static final String[] STATE_NAMES = {"NEW", "CONNECTING", "CONNECTED", "CLOSED"};
    protected static final int NEW = 0;
    protected static final int CONNECTING = 1;
    protected static final int CONNECTED = 2;
    protected static final int CLOSED = 3;

    protected int state = NEW;

    /* to reduce needless logs amount */
    protected static final long LOG_DELAY = 2 * TimeUtil.MINUTE;

    private SocketHandler.ControlPack handlerControlPack;


    protected SocketController(Connector connector) {
        this.connector = connector;
    }

    /**
     * Starts this controller to acquire new {@link Socket}. Controller may be started only once.
     */
    abstract void start();

    /**
     * Closes this controller and all acquired {@link Socket}. Does nothing if controller is already closed.
     */
    abstract void close();

    /**
     * Reports that specified {@link SocketHandler} has closed. May lead to reconnect and more handlers if required.
     */
    abstract void handlerClosed(SocketHandler handler);

    /**
     * Acquires new {@link Socket} in dedicated thread. May perform any auxilary blocking operations
     * (like DNS resolving or server socket creating), but shall return as soon as new {@link Socket} is acquired.
     * Shall return 'null' to indicate that calling {@link SocketHandler} shall close.
     */
    abstract Socket acquireSocket();

    protected SocketHandler createNewSocket(String address) {
        SocketHandler handler = new SocketHandler(connector, this, address, handlerControlPack);
        handlerControlPack = handler.getControlPack();
        return handler;
    }

}
