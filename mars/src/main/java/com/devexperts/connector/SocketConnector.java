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

import com.devexperts.util.TimeFormat;

import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * SocketConnector provides {@link Socket} instances for {@link SocketHandler} by connecting to one of specified
 * addresses using round-robin algorithm. Maintains only single connection with automatic reconnect.
 */
class SocketConnector extends SocketController {

    private final String address;
    private final Set<SocketAddress> parsed_addresses = new HashSet<SocketAddress>();

    private final Random random = new Random();
    private final List<SocketAddress> resolved_addresses = new ArrayList<SocketAddress>();
    private int current_address; // Index of current address.

    private SocketHandler handler; // Current active handler.
    private long next_attempt_time; // Time for next connection attempt for purposes of (re)connection delay.

    /* to reduce needless logs amount */
    private String prevConnectStatus;
    private long prevConnectStatusTime;
    private long prevConnectStatusNumber;

    private String prevResolveStatus;
    private long prevResolveStatusTime;
    private long prevResolveStatusNumber;

    /**
     * Creates new connector for specified parameters.
     *
     * @param address must be client-side address list formatted as "host1:port1[,host2:port2[,...]]".
     */
    SocketConnector(Connector connector, String address) throws ParseException {
        super(connector);
        this.address = address;
        for (StringTokenizer st = new StringTokenizer(address, ","); st.hasMoreTokens();) {
            String s = st.nextToken();
            SocketAddress sa = SocketAddress.valueOf(s);
            if (sa.getHost().isEmpty())
                throw new ParseException("Host name is missing.", 0);
            parsed_addresses.add(sa);
        }
        if (parsed_addresses.isEmpty())
            throw new ParseException("No addresses found.", 0);
    }

    public String toString() {
        return "SocketConnector-" + address + ": " + STATE_NAMES[state];
    }

    synchronized void start() {
        if (state == CLOSED) {
            return;
        }
        if (state != NEW) {
            throw new IllegalStateException("Connector may be started only once.");
        }
        state = CONNECTING;
        handler = createNewSocket(address);
        handler.start();
    }

    synchronized void close() {
        if (state == CLOSED) {
            return;
        }
        state = CLOSED;
        if (handler != null) {
            handler.close();
        }
        handler = null;
    }

    synchronized void handlerClosed(SocketHandler handler) {
        if (state == CLOSED) {
            return;
        }
        if (handler != this.handler) {
            return; // Avoid problems caused by delayed threads or multiple close calls.
        }
        state = CONNECTING;
        this.handler = createNewSocket(address);
        this.handler.start();
    }

    Socket acquireSocket() {
        // There is only 1 active SocketHandler at a time. Therefore, only 1 thread
        // to call acquireSocket() method and to work with resolved_addresses.
        // Thus, no synchronization is required for this part.
        long time = System.currentTimeMillis();
        if (time < next_attempt_time)
            try {
                Thread.sleep(next_attempt_time - time);
                time = System.currentTimeMillis();
            } catch (InterruptedException e) {
                return null;
            }
        next_attempt_time = time + connector.getSkewedPeriod(connector.getReconnectionPeriod());
        current_address++;
        if (current_address >= resolved_addresses.size()) {
            resolveAddresses();
            current_address = 0;
            if (resolved_addresses.isEmpty())
                return null; // Will create new SocketHandler which will wait for reconnect and resolving.
        }
        SocketAddress address = resolved_addresses.get(current_address);

        StringBuilder connectingMsgBuf = new StringBuilder().append("connecting... ");
        Throwable throwable = null;

        String connectorState = null;
        try {
            Socket socket = new Socket(address.getHost(), address.getPort());
            synchronized (this) {
                // If CLOSED meanwhile, then SocketHandler is CLOSED too and it will proceed
                // with cleanup normally. No need to bother here. Just track proper 'state'.
                if (state == CONNECTING) {
                    state = CONNECTED;
                }
            }

            connectingMsgBuf.append("established");
            connectorState = ConnectorStates.ESTABLISHED_STATE;
            return socket;
        } catch (Throwable t) {
            throwable = t;
            connectingMsgBuf.append("failed");
            return null;
        } finally {
            String curConnectStatus = connectingMsgBuf.toString();
            long curTime = System.currentTimeMillis();
            boolean isEqual = curConnectStatus.equals(prevConnectStatus);
            if (isEqual && (curTime - prevConnectStatusTime < LOG_DELAY)) {
                prevConnectStatusNumber++;
            } else {
                if (prevConnectStatusNumber != 0) {
                    StringBuilder buf = new StringBuilder(curConnectStatus.length() + 50);
                    buf.append('[');
                    if (isEqual) {
                        buf.append(prevConnectStatusNumber + 1).append(" msg, address=").append(address).append(']');
                        connector.log(buf.append(prevConnectStatus).toString(), throwable, !isStandardState(throwable), null);
                    } else {
                        buf.append(prevConnectStatusNumber).append(" msg, address=").append(address).append(", last=")
                            .append(TimeFormat.DEFAULT.withMillis().format(prevConnectStatusTime)).append(']');
                        connector.log(buf.append(prevConnectStatus).toString(), null, null);
                        connector.log(curConnectStatus, throwable, !isStandardState(throwable), connectorState);
                    }
                    prevConnectStatusNumber = 0;
                } else { // prevConnectStatusNumber == 0
                    connector.log(curConnectStatus, throwable, !isStandardState(throwable), connectorState);
                }

                prevConnectStatusTime = curTime;
                prevConnectStatus = curConnectStatus;
            }
        }
    }

    private boolean isStandardState(Throwable t) {
        if (t == null) {
            return true;
        }
        String message = t.getMessage();
        return message.contains("refused") || message.contains("timeout");
    }

    /**
     * Resolves anew all parsed addresses and updates list of resolved addresses accordingly. Existing resolved
     * addresses are not reordered during update, but new addresses are inserted into random positions.
     */
    private void resolveAddresses() {
        // Resolve parsed addresses.
        Set<SocketAddress> addresses = new HashSet<SocketAddress>();
        for (SocketAddress address : parsed_addresses) {
            StringBuilder resolvingMsgBuf = new StringBuilder().append("Resolving ").append(address.getHost());
            UnknownHostException exception = null;
            try {
                InetAddress[] addrs = InetAddress.getAllByName(address.getHost());
                for (int i = 0; i < addrs.length; i++) {
                    addresses.add(new SocketAddress(address.getSpec(), addrs[i].getHostAddress(), address.getPort()));
                }
            } catch (UnknownHostException e) {
                exception = e;
                resolvingMsgBuf.append("... FAILED");
            } finally {
                String curResolveStatus = resolvingMsgBuf.toString();
                long curTime = System.currentTimeMillis();
                if (exception != null) {
                    boolean isEqual = curResolveStatus.equals(prevResolveStatus);
                    if (isEqual && (curTime - prevResolveStatusTime < LOG_DELAY)) {
                        prevResolveStatusNumber++;
                    } else {
                        if (prevResolveStatusNumber != 0) {
                            StringBuilder buf = new StringBuilder(curResolveStatus.length() + 50);
                            buf.append('[');
                            if (isEqual) {
                                buf.append(prevResolveStatusNumber + 1).append(" msg]");
                                connector.log(buf.append(prevResolveStatus).toString(), exception, null);
                            } else {
                                buf.append(prevResolveStatusNumber).append(" msg, LAST=")
                                    .append(TimeFormat.DEFAULT.withMillis().format(prevResolveStatusTime)).append(']');
                                connector.log(buf.append(prevResolveStatus).toString(), null, null);
                                connector.log(curResolveStatus, exception, null);
                            }
                            prevResolveStatusNumber = 0;
                        } else {
                            connector.log(curResolveStatus, exception, null);
                        }

                        prevResolveStatusTime = curTime;
                        prevResolveStatus = curResolveStatus;
                    }
                }
            }
        }
        // Remove gone addresses and insert new ones into random positions.
        resolved_addresses.retainAll(addresses);
        addresses.removeAll(resolved_addresses);
        for (SocketAddress address : addresses)
            resolved_addresses.add(random.nextInt(resolved_addresses.size() + 1), address);
    }
}
