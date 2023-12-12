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
package com.devexperts.qd.tools;

import com.devexperts.connector.proto.JVMId;
import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.common.MARSPlugin;
import com.devexperts.qd.qtp.QTPWorkerThread;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tracks that time is synchronized across local network by sending multicast messages.
 */
public class TimeSyncTracker implements TimeSyncTrackerMBean, MARSPlugin {
    public static final String MBEAN_NAME = Management.getMBeanNameForClass(TimeSyncTracker.class);

    private static final int DEFAULT_PORT = 5145;
    private static final String DEFAULT_ADDR = "239.192.51.45";

    private static final int DELTAS = 8; // number of time deltas in JvmInfo; must be >= 4

    private static final byte[] TYPE_TIME = { 'Q', 'D', 'S', 'T' };
    private static final byte[] TYPE_REQUEST = { 'Q', 'D', 'S', 'R' };
    private static final byte[] TYPE_RESPONSE = { 'Q', 'D', 'S', 'r' };

    private static final int TYPE_SIZE = 4;
    private static final int UID_SIZE = 16;
    private static final int TIME_SIZE = 8;
    private static final int PACKET_SIZE = TYPE_SIZE + UID_SIZE + TIME_SIZE;

    private static final long MAX_DEVIATION = 100;
    private static final long DRIFT_LIMIT = 300;

    private static final long TICK_PERIOD = 10000;
    private static final int EXPIRATION_TICKS = 10; // expire after 10 ticks
    private static final int REPORT_TICKS = 6; // report every ~minute

    private static final Logging log = Logging.getLogging(TimeSyncTracker.class);

    // initialize only when getInstance is called
    private static class InstanceHolder {
        static final TimeSyncTracker INSTANCE = new TimeSyncTracker(MARSNode.getRoot());
    }

    /**
     * @deprecated Don't use this method directly. TimeSyncTracker will get started in
     * every JVM that uses MARS as automatically as MARS plugin.
     */
    public static TimeSyncTracker getInstance() {
        return InstanceHolder.INSTANCE;
    }

    // -------------------- instance --------------------

    private final MARSNode mainNode;
    private final MARSNode deltaNode;
    private final MARSNode rangeNode;
    private final MARSNode hostsNode;
    private final MARSNode peersNode;
    private final MARSNode totalNode;

    private final int port = SystemProperties.getIntProperty("com.devexperts.qd.tools.TimeSyncTracker.port", DEFAULT_PORT);
    private final String addr = SystemProperties.getProperty("com.devexperts.qd.tools.TimeSyncTracker.addr", DEFAULT_ADDR);
    private final JvmIdWithAddress jvmId;

    private Management.Registration registration;
    private MulticastSocket socket;
    private Sender sender;
    private Receiver receiver;
    private int tick;
    private int nextReportTick;
    private long medianDelta;
    private final Map<JvmIdWithAddress, JvmInfo> peers = new TreeMap<>();
    private final Map<InetAddress, byte[]> addresses = new HashMap<>();
    private JvmIdWithAddress tmpJvmId = new JvmIdWithAddress(null);

    private boolean verboseRequest;
    private final Set<JvmIdWithAddress> visitedRequests = new HashSet<>();

    TimeSyncTracker(MARSNode root) {
        mainNode = root.subNode("timesync", "Time synchronization statistics in local cluster.");
        deltaNode = root.subNode("timesync.delta", "Time delta from median in local cluster, milliseconds.");
        rangeNode = root.subNode("timesync.range", "Time range in local cluster, milliseconds.");
        hostsNode = root.subNode("timesync.stableHosts", "Number of stable hosts in local cluster.");
        peersNode = root.subNode("timesync.stablePeers", "Number of stable peers in local cluster.");
        totalNode = root.subNode("timesync.totalPeers", "Total number of peers in local cluster.");
        jvmId = new JvmIdWithAddress(new byte[0]); // we don't know what addr our packets will come from
    }

    @Override
    public synchronized void start() {
        if (socket != null)
            return; // already running
        try {
            //noinspection SocketOpenedButNotSafelyClosed
            socket = new MulticastSocket(port);
        } catch (IOException e) {
            log.error("Failed to create multicast socket for time synchronization tracker", e);
            return;
        }
        try {
            socket.joinGroup(InetAddress.getByName(addr));
        } catch (IOException e) {
            // Workaround for JDK-8178161 (see QD-1131, QD-1262)
            String osName = System.getProperty("os.name");
            if (e.getMessage().contains("assign requested address") && osName.toLowerCase().startsWith("mac")) {
                log.info("Time synchronization tracker initialization failed - unsupported on MacOS");
            } else {
                log.error("Failed to join multicast group for time synchronization tracker", e);
            }
            closeSocket();
            return;
        }
        registration = Management.registerMBean(this, null, MBEAN_NAME);
        sender = new Sender();
        receiver = new Receiver();
        sender.start();
        receiver.start();
    }

    @Override
    public synchronized void stop() {
        handleClose(null);
        peers.clear();
        addresses.clear();
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    @Override
    public synchronized boolean isActive() {
        return socket != null;
    }

    @Override
    public synchronized int getPeerCount() {
        return peers.size();
    }

    @Override
    public synchronized String[] getPeerIds() {
        int n = peers.size();
        String[] result = new String[n];
        Iterator<JvmIdWithAddress> it = peers.keySet().iterator();
        for (int i = 0; i < n; i++)
            result[i] = it.next().toString();
        return result;
    }

    @Override
    public synchronized long[] getPeerDeltas() {
        int n = peers.size();
        long[] result = new long[n];
        Iterator<JvmInfo> it = peers.values().iterator();
        for (int i = 0; i < n; i++)
            result[i] = it.next().delta;
        return result;
    }

    @Override
    public synchronized long[] getPeerDeviations() {
        int n = peers.size();
        long[] result = new long[n];
        Iterator<JvmInfo> it = peers.values().iterator();
        for (int i = 0; i < n; i++)
            result[i] = it.next().deviation;
        return result;
    }

    @Override
    public synchronized long getMedianDelta() {
        return medianDelta;
    }

    private synchronized void handleClose(Throwable reason) {
        if (socket == null)
            return;
        log.error("Stopped time synchronization tracker", reason);
        closeSocket();
        sender.close();
        receiver.close();
        sender = null;
        receiver = null;
    }

    private void closeSocket() {
        socket.close();
        socket = null;
    }

    private synchronized MulticastSocket getSocket() throws IOException {
        MulticastSocket result = socket;
        if (result == null)
            throw new IOException("TimeSyncTracker is not running");
        return result;
    }

    @Override
    public synchronized void sendRequest(boolean verbose) {
        verboseRequest = verbose;
        visitedRequests.clear();
        try {
            send(TYPE_REQUEST, null, 0);
        } catch (IOException e) {
            log.error("Failed to send request", e);
        }
    }

    // Sends packet with specified type and original packet (for response)
    private void send(byte[] type, byte[] original, int original_length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(PACKET_SIZE + original_length);
        buf.put(type);
        buf.put(jvmId.getUID());
        buf.putLong(System.currentTimeMillis());
        if (original_length > 0)
            buf.put(original, 0, original_length);
        getSocket().send(new DatagramPacket(buf.array(), buf.position(), InetAddress.getByName(addr), port));
    }

    // Receives single packet and processes it (updates info and sends replies)
    private void receive(ByteBuffer buf, DatagramPacket packet, byte[] msg_type, byte[] msg_uid, long[] deltas) throws IOException {
        // WARNING! This method reuses all it's parameters to reduce garbage rate. Pay attention for proper reuse.
        buf.clear();
        packet.setData(buf.array(), 0, buf.capacity());
        getSocket().receive(packet);
        long time = System.currentTimeMillis();
        if (packet.getLength() < PACKET_SIZE)
            return;
        buf.limit(packet.getLength());

        buf.get(msg_type);
        buf.get(msg_uid);
        long msg_time = buf.getLong();
        if (Arrays.equals(msg_uid, jvmId.getUID())) // ignore our own messages
            return;

        if (Arrays.equals(msg_type, TYPE_TIME)) {
            // WARNING! msg_uid is reused, so it shall be properly cloned before keeping it for long time.
            update(packet.getAddress(), msg_uid, msg_time - time, deltas);
        } else if (Arrays.equals(msg_type, TYPE_REQUEST)) {
            send(TYPE_RESPONSE, buf.array(), packet.getLength());
        } else if (Arrays.equals(msg_type, TYPE_RESPONSE)) {
            if (packet.getLength() < 2 * PACKET_SIZE)
                return;

            byte[] original_type = new byte[TYPE_SIZE];
            byte[] original_uid = new byte[UID_SIZE];
            buf.get(original_type);
            buf.get(original_uid);
            long original_time = buf.getLong();

            if (!Arrays.equals(original_type, TYPE_REQUEST) || !Arrays.equals(original_uid, jvmId.getUID()))
                return;

            // WARNING! msg_uid is reused, so msgJvmId shall be properly cloned before keeping it for long time
            JvmIdWithAddress msgJvmId = new JvmIdWithAddress(msg_uid, packet.getAddress().getAddress());
            handleResponse(msgJvmId, original_time, msg_time, time);
        }
    }

    private synchronized void update(InetAddress address, byte[] msg_uid, long delta, long[] deltas) {
        // WARNING! msg_uid is reused, so this method clones it before keeping it for long time.
        if (!isActive())
            return;
        byte[] addr = addresses.get(address);
        if (addr == null)
            addresses.put(address, addr = address.getAddress());
        tmpJvmId = new JvmIdWithAddress(msg_uid, addr);
        JvmInfo info = peers.get(tmpJvmId);
        if (info == null) {
            JvmIdWithAddress otherJvmId = new JvmIdWithAddress(msg_uid.clone(), addr);
            peers.put(otherJvmId, info = new JvmInfo(otherJvmId));
        }
        info.update(delta, tick, deltas);
    }

    private synchronized void handleResponse(JvmIdWithAddress msgJvmId, long original_time, long msg_time, long time) {
        // WARNING! msgJvmId reuses uid, but this method does not keep it, so it's ok not to clone it.
        long delta = msg_time - (original_time + time) / 2;
        if (verboseRequest || visitedRequests.add(new JvmIdWithAddress(new byte[0], msgJvmId.address)))
            log.info("Peer " + msgJvmId + ": delta " + delta + " ms, roundtrip " + (time - original_time) + " ms");
    }

    private synchronized void tick() {
        tick++;
    }

    public synchronized void analyze(boolean forceLog) {
        if (!isActive())
            return;
        int stablePeers = 0;
        ArrayList<JvmInfo> infos = new ArrayList<>();
        byte[] visited = null;
        for (Iterator<JvmInfo> it = peers.values().iterator(); it.hasNext();) {
            JvmInfo info = it.next();
            if (info.lastTick + EXPIRATION_TICKS < tick) {
                it.remove();
                continue;
            }
            if (info.deviation < MAX_DEVIATION) {
                stablePeers++;
                // peers are sorted by address first, so comparing with previous address is enough to filter by unique address
                if (!Arrays.equals(visited, info.jvmId.address)) {
                    visited = info.jvmId.address;
                    infos.add(info);
                }
            }
        }
        if (addresses.size() > peers.size() * 2) // memory leak prevention in a cheap way
            addresses.clear();
        if (infos.isEmpty())
            return;
        Collections.sort(infos);
        medianDelta = -infos.get(infos.size() / 2).delta;
        long range = infos.get(infos.size() - 1).delta - infos.get(0).delta;
        mainNode.setValue(medianDelta + " ms off " + infos.size() + " hosts (~" + range + " ms)");
        deltaNode.setDoubleValue(medianDelta);
        rangeNode.setDoubleValue(range);
        hostsNode.setDoubleValue(infos.size());
        peersNode.setDoubleValue(stablePeers);
        totalNode.setDoubleValue(peers.size());
        if (forceLog || Math.abs(medianDelta) > DRIFT_LIMIT && tick >= nextReportTick) {
            String msg = "Local time delta is " + medianDelta + " ms against median of " + infos.size() + " stable hosts (" + stablePeers + "/" + peers.size() + " peers), range is " + range + " ms";
            if (Math.abs(medianDelta) > DRIFT_LIMIT)
                log.error(msg);
            else
                log.info(msg);
            nextReportTick = tick + REPORT_TICKS;
        }
    }

    @Override
    public synchronized void dumpPeers(boolean verbose) {
        Set<JVMId> visited = new HashSet<>();
        for (JvmInfo info : peers.values()) {
            if (verbose || info.deviation < MAX_DEVIATION && visited.add(new JvmIdWithAddress(new byte[0], info.jvmId.address)))
                log.info("Peer " + info.jvmId + ": delta " + fmt(info.delta) + " ms, deviation " + fmt(info.deviation) + " ms");
        }
    }

    private String fmt(long t) {
        return t == Long.MAX_VALUE ? "N/A" : Long.toString(t);
    }

    //------------------- Helper classes -------------------

    private static class JvmIdWithAddress extends JVMId {
        // todo: contain JVMid instead of extending it
        byte[] address;

        JvmIdWithAddress(byte[] uid, byte[] address) {
            super(uid);
            this.address = address;
        }

        JvmIdWithAddress(byte[] address) {
            super(JVM_ID.getUID());
            this.address = address;
        }

        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof JvmIdWithAddress))
                return false;
            JvmIdWithAddress other = (JvmIdWithAddress) o;
            return Arrays.equals(address, other.address) && super.equals(other);
        }

        public int hashCode() {
            int result = 0;
            for (byte b : address)
                result = result * 27 + b;
            result = result * 27 + super.hashCode();
            return result;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (byte b : address)
                sb.append(b & 0xff).append('.');
            sb.append(super.toString());
            return sb.toString();
        }

        @Override
        public int compareTo(JVMId o) {
            if (!(o instanceof JvmIdWithAddress))
                throw new IllegalArgumentException();
            JvmIdWithAddress other = (JvmIdWithAddress) o;
            int i = arrayCompare(address, other.address);
            if (i != 0)
                return i;
            return super.compareTo(o);
        }
    }

    private static class JvmInfo implements Comparable<JvmInfo> {
        final JvmIdWithAddress jvmId;
        final long[] deltas = new long[DELTAS];
        int lastTick;

        long delta;
        long deviation;

        JvmInfo(JvmIdWithAddress jvmId) {
            this.jvmId = jvmId;
            Arrays.fill(deltas, Long.MAX_VALUE);
        }

        public void update(long newDelta, int tick, long[] d) {
            System.arraycopy(deltas, 1, deltas, 0, deltas.length - 1);
            deltas[deltas.length - 1] = newDelta;
            lastTick = tick;

            for (long del : deltas)
                if (del == Long.MAX_VALUE) {
                    delta = Long.MAX_VALUE;
                    deviation = Long.MAX_VALUE;
                    return;
                }
            System.arraycopy(deltas, 0, d, 0, deltas.length);
            Arrays.sort(d);
            long a = 0;
            int i = deltas.length / 4;
            int j = deltas.length - 1 - i;
            for (int k = i; k <= j; k++)
                a += d[k];
            delta = a / (j - i + 1);
            deviation = d[j] - d[i];
        }

        @Override
        public int compareTo(JvmInfo other) {
            if (delta < other.delta)
                return -1;
            if (delta > other.delta)
                return 1;
            return jvmId.compareTo(other.jvmId);
        }
    }

    private class Sender extends QTPWorkerThread {
        Sender() {
            super("TimeSyncTrackerSender");
        }

        @Override
        protected void doWork() throws InterruptedException, IOException {
            while (!isClosed()) {
                send(TYPE_TIME, null, 0);
                tick();
                Thread.sleep((long) (TICK_PERIOD * (0.5 + Math.random())));
                analyze(false);
            }
        }

        @Override
        protected void handleShutdown() {
            TimeSyncTracker.this.stop();
        }

        @Override
        protected void handleClose(Throwable reason) {
            TimeSyncTracker.this.handleClose(reason);
        }
    }

    private class Receiver extends QTPWorkerThread {
        private final ByteBuffer buf = ByteBuffer.allocate(2 * PACKET_SIZE);
        private final DatagramPacket packet = new DatagramPacket(buf.array(), buf.capacity());
        private final byte[] msg_type = new byte[TYPE_SIZE];
        private final byte[] msg_uid = new byte[UID_SIZE];
        private final long[] deltas = new long[DELTAS];

        Receiver() {
            super("TimeSyncTrackerReceiver");
        }

        @Override
        protected void doWork() throws InterruptedException, IOException {
            while (!isClosed()) {
                receive(buf, packet, msg_type, msg_uid, deltas);
            }
        }

        @Override
        protected void handleShutdown() {
            TimeSyncTracker.this.stop();
        }

        @Override
        protected void handleClose(Throwable reason) {
            TimeSyncTracker.this.handleClose(reason);
        }
    }

    @Override
    public String toString() {
        return "time synchronization tracker using multicast " + addr + ":" + port + " with " + jvmId;
    }

    @SuppressWarnings("UnusedDeclaration")
    @ServiceProvider
    public static class PluginFactory extends Factory {
        @Override
        public MARSPlugin createPlugin(MARSEndpoint marsEndpoint) {
            return new TimeSyncTracker(marsEndpoint.getRoot());
        }
    }
}
