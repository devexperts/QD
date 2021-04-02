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

import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeUtil;

import java.net.Socket;

/**
 * SocketHandler acquires, configures and handles single {@link Socket} until close; creates and manages 2 threads for
 * blocking data processing (reading and writing).
 */
class SocketHandler implements ConnectionAdapterListener, Runnable {
    private final Connector connector;
    private final SocketController controller;

    private Thread reader;
    private Thread writer;

    private String address;

    private Socket socket;
    private ConnectionAdapter adapter;
    private int heartbeat_period;

    private boolean data_available = true;

    private static final String[] STATE_NAMES = {"NEW", "CONNECTING", "CONNECTED", "CLOSED"};
    private static final int NEW = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;
    private static final int CLOSED = 3;

    private volatile int state = NEW;

    private static final long LOG_DELAY = 2 * TimeUtil.MINUTE;

    private final ControlPack controlPack;

    // ========== Construction and Public API ==========

    /**
     * Creates new handler with specified parameters.
     *
     * @param address is used only to generate proper name for threads and logging.
     */
    SocketHandler(Connector connector, SocketController controller, String address, ControlPack controlPack) {
        this.connector = connector;
        this.controller = controller;
        this.address = address;
        this.controlPack = controlPack != null ? controlPack : new ControlPack();
    }

    public String toString() {
        return "SocketHandler-" + LogUtil.hideCredentials(address) + ": " + STATE_NAMES[state];
    }

    /**
     * Starts this handler to acquire new {@link Socket}. Handler may be started only once.
     */
    void start() {
        makeConnecting();
    }

    /**
     * Closes this handler. Does nothing if handler is already closed.
     */
    void close() {
        runDisconnect(null);
    }

    // ========== State Switching Internal API ==========

    /**
     * Switches from NEW to CONNECTING state and returns 'true' if successful.
     */
    private synchronized boolean makeConnecting() {
        if (state == CLOSED) {
            return false;
        }
        if (state != NEW) {
            throw new IllegalStateException("Handler may be started only once.");
        }
        reader = new Thread(this, "SocketHandler-" + LogUtil.hideCredentials(address));
        reader.setDaemon(true);
        reader.start();
        state = CONNECTING;
        return true;
    }

    /**
     * Switches from CONNECTING to CONNECTED state and returns 'true' if successful.
     */
    private synchronized boolean makeConnected(Socket socket, ConnectionAdapter adapter) {
        if (state == CLOSED)
            return false;
        if (state != CONNECTING)
            throw new IllegalStateException("Handler may be connected only once.");
        reader.setName("SocketReader-" + LogUtil.hideCredentials(address));
        writer = new Thread(this, "SocketWriter-" + LogUtil.hideCredentials(address));
        writer.setDaemon(true);
        writer.start();
        this.socket = socket;
        this.adapter = adapter;
        state = CONNECTED;
        return true;
    }

    /**
     * Switches to CLOSED state and returns 'true' if successful.
     */
    private synchronized boolean makeClosed() {
        if (state == CLOSED)
            return false;
        notifyAll();
        if (reader != null)
            reader.interrupt();
        if (writer != null)
            writer.interrupt();
        state = CLOSED;
        return true;
    }

    // ========== Public Interfaces Implementation ==========

    public synchronized void dataAvailable(ConnectionAdapter adapter) {
        if (adapter != this.adapter)
            return;
        if (data_available)
            return;
        data_available = true;
        notifyAll();
    }

    public void adapterClosed(ConnectionAdapter adapter) {
        if (adapter != this.adapter)
            return;
        close();
    }

    public void run() {
        Thread thread = Thread.currentThread();
        synchronized (this) {
            // Synchronization is also required to synch newly started thread with state machine.
            if (thread != reader && thread != writer) { // A bit of paranoia...
                throw new IllegalStateException("Illegal thread: " + thread.getName());
            }
            if (state == CLOSED) {
                return;
            }
        }
        if (thread == reader && !runConnect()) {
            return;
        }
        Throwable error = null;
        try {
            if (thread == reader) {
                runRead();
            } else {
                runWrite();
            }
        } catch (InterruptedException e) {
            // This is a way to stop the thread. Ignore.
        } catch (Throwable t) {
            error = t;
        } finally {
            runDisconnect(error);
        }
    }

    // ========== Internal Implementation ==========

    /**
     * Cleanups specified socket and adapter if not null.
     */
    private void cleanup(Socket socket, ConnectionAdapter adapter) {
        if (socket != null)
            try {
                socket.close();
            } catch (Throwable t) {
                connector.log("Cleanup failed " + LogUtil.hideCredentials(connector.getSocketAddress(socket)), t, null);
            }
        if (adapter != null)
            try {
                adapter.close();
            } catch (Throwable t) {
                connector.log("Cleanup failed " + LogUtil.hideCredentials(adapter), t, null);
            }
    }

    /**
     * Acquires {@link Socket}, creates {@link ConnectionAdapter}, configures both and switches to CONNECTED state.
     * Returns 'true' if successful, otherwise returns 'false' and cleanups allocated resources itself.
     */
    private boolean runConnect() {
        synchronized (this) {
            if (state != CONNECTING)
                return false;
        }
        // Allocate socket and adapter using local variables to keep state machine clear and prevent double close.
        Socket socket = null;
        ConnectionAdapter adapter = null;
        boolean connected = false;
        Throwable error = null;
        try {
            // Create and configure socket.
            socket = controller.acquireSocket();
            if (socket == null) {
                return false;
            }
            address = connector.getSocketAddress(socket);
            heartbeat_period = connector.getHeartbeatPeriod();
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(connector.getHeartbeatTimeout());

            // Create and configure adapter.
            adapter = connector.createConnectionAdapter(socket);
            if (adapter == null) {
                return false;
            }
            adapter.setListener(this);
            adapter.start();

            // Switch state machine and store socket and adapter into fields.
            return makeConnected(socket, adapter) && (connected = true);
        } catch (Throwable t) {
            error = t;
            return false;
        } finally {
            if (!connected) {
                // Cleanup state machine, socket and adapter using local variables.
                controller.handlerClosed(this);
                cleanup(socket, adapter);
                if (makeClosed()) {
                    if (error != null) {
                        long curTime = System.currentTimeMillis();
                        if (curTime - controlPack.prevConnectStatusTime < LOG_DELAY) {
                            controlPack.prevConnectStatusNumber++;
                        } else {
                            controlPack.prevConnectStatusTime = curTime;
                            StringBuilder sb = new StringBuilder(error.getMessage());
                            if (controlPack.prevConnectStatusNumber != 0) {
                                sb.append('[').append(controlPack.prevConnectStatusNumber + 1);
                                sb.append(" msg, address=").append(LogUtil.hideCredentials(address)).append(']');
                                controlPack.prevConnectStatusNumber = 0;
                            }
                            connector.log(sb.toString(), error, null);
                        }
                    }
                }
            }
        }
    }

    /**
     * Disconnects this handler and cleanups resources if was CONNECTED.
     */
    private void runDisconnect(Throwable error) {
        if (!makeClosed()) {
            return;
        }
        controller.handlerClosed(this);
        cleanup(socket, adapter);

        connector.log("Disconnect for " + LogUtil.hideCredentials(address), error, ConnectorStates.DISCONNECTED_STATE);
    }

    /**
     * Performs blocking read operation in dedicated reading thread.
     */
    private void runRead() throws Throwable {
        while (!Thread.interrupted()) {
            synchronized (this) {
                if (state != CONNECTED) {
                    return;
                }
            }
            int bytes = adapter.readData();
            if (bytes < 0) { // Considered as EOF signal.
                return;
            }
        }
    }

    /**
     * Performs blocking write operation in dedicated writing thread; also writes heartbeats if required.
     */
    private void runWrite() throws Throwable {
        long time = System.currentTimeMillis();
        long next_heartbeat = heartbeat_period == 0 ? Long.MAX_VALUE :
            time + connector.getSkewedPeriod(heartbeat_period);
        while (!Thread.interrupted()) {
            boolean do_write;
            synchronized (this) {
                while (state == CONNECTED && !data_available && time < next_heartbeat) {
                    wait(next_heartbeat - time);
                    time = System.currentTimeMillis();
                }
                if (state != CONNECTED) {
                    return;
                }
                do_write = data_available;
                data_available = false;
            }
            int bytes = 0;
            if (do_write) {
                bytes = adapter.writeData();
            }
            if (bytes == 0 && time >= next_heartbeat) {
                bytes = adapter.writeHeartbeat();
            }
            if (bytes < 0) { // Considered as EOF signal.
                return;
            }
            if (bytes > 0) {
                time = System.currentTimeMillis();
                next_heartbeat = heartbeat_period == 0 ? Long.MAX_VALUE :
                    time + connector.getSkewedPeriod(heartbeat_period);
            }
        }
    }

    ControlPack getControlPack() {
        return controlPack;
    }

    static class ControlPack {
        ControlPack() {
        }

        /* to reduce needless logs amount */
        private long prevConnectStatusTime;
        private long prevConnectStatusNumber;
    }
}
