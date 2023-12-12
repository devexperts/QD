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
package com.devexperts.qd.sample.stresstest;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSScheduler;
import com.devexperts.mars.jvm.CpuCounter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Network stress-test server.
 */
public class TSTServer extends ApplicationConnectionFactory {
    private static final Logging log = Logging.getLogging(TSTServer.class);

    private final Properties properties;
    final long distributionPeriod;
    final int numberOfPatterns;
    final int[] sizes;
    final double[] msgsPerDP;
    final byte[] data;
    final double messageSizeVariation;
    final Random rnd = new Random();
    final ArrayList<ServerConnection> connections = new ArrayList<ServerConnection>();
    final int numberOfClients;
    boolean failed;

    public TSTServer(Properties properties) throws IOException {
        this.properties = properties;
        numberOfClients = Integer.parseInt(properties.getProperty("numberOfClients"));
        distributionPeriod = Long.parseLong(properties.getProperty("distributionPeriod"));
        messageSizeVariation = Double.parseDouble(properties.getProperty("messageSizeVariation"));
        String[] patterns = properties.getProperty("patterns").split(";");
        numberOfPatterns = patterns.length;
        sizes = new int[numberOfPatterns];
        msgsPerDP = new double[numberOfPatterns];
        int maxSize = 0;
        double sizeOverhead = Double.parseDouble(properties.getProperty("sizeOverhead"));
        int i = 0;
        for (String s : patterns) {
            String[] parts = s.split(":");
            if (parts.length != 2)
                throw new IOException("bad patterns");
            sizes[i] = (int) (Integer.parseInt(parts[0]) * sizeOverhead);
            maxSize = Math.max(maxSize, sizes[i]);
            double messagesPerMinute = Double.parseDouble(parts[1]);
            msgsPerDP[i] = messagesPerMinute / (60 * 1000) * distributionPeriod * numberOfClients;
            i++;
        }
        data = new byte[(int) Math.ceil(maxSize * (1 + messageSizeVariation)) + 1];
        rnd.nextBytes(data);
        log.info("Using distribution period " + distributionPeriod + ", distribution patterns " + properties.getProperty("patterns"));
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        new TSTServer(loadProperties()).doWork();
    }

    private void doWork() throws InterruptedException {
        String address = properties.getProperty("address");
        if (SystemProperties.parseBooleanValue(properties.getProperty("useSSL"))) {
            address = "ssl[isServer]+" + address;
            log.info("Using SSL");
        }
        MessageConnectors.startMessageConnectors(MessageConnectors.createMessageConnectors(this, address));
        log.info("Waiting for " + numberOfClients + " connections...");
        synchronized (connections) {
            while (connections.size() != numberOfClients)
                connections.wait();
        }
        log.info(numberOfClients + " connections established");

        long loggingPeriod = TimePeriod.valueOf(properties.getProperty("loggingPeriod")).getTime();
        MARSScheduler.schedule(new Runnable() {
            CpuCounter cpuCounter = new CpuCounter();
            long lastLoggingTime = System.currentTimeMillis();
            public void run() {
                long curTime = System.currentTimeMillis();
                long deltaTime = curTime - lastLoggingTime;
                long bytes = 0;
                long messages = 0;
                for (ServerConnection connection : connections) {
                    bytes += connection.getBytesCount();
                    messages += connection.getMessagesCount();
                }
                log.info("clients: " + connections.size() +
                    ", messages/sec: " + (messages * 1000 / deltaTime) +
                    ", bytes/sec: " + (bytes * 1000 / deltaTime) +
                    ", CPU usage: " + Math.floor(cpuCounter.getCpuUsage() * 10000) / 100 + "%");
                lastLoggingTime = curTime;
            }
        }, loggingPeriod, TimeUnit.MILLISECONDS);

        double[] numberOfRequests = new double[numberOfPatterns];
        long prevTime = System.currentTimeMillis();
        while (!failed) {
            Thread.sleep(distributionPeriod);
            long curTime = System.currentTimeMillis();

            // distributing data
            long delta = curTime - prevTime;
            for (int i = 0; i < numberOfPatterns; i++) {
                numberOfRequests[i] += msgsPerDP[i] * delta / distributionPeriod;
                while (numberOfRequests[i] >= 1) {
                    numberOfRequests[i]--;
                    int k = rnd.nextInt(connections.size());
                    ServerConnection connection = connections.get(k);
                    int size = (int) (sizes[i] * (1 - messageSizeVariation + rnd.nextDouble() * 2 * messageSizeVariation));
                    connection.requests.put(size);
                    connection.sendChunks();
                }
            }
            prevTime = curTime;
        }
        log.info("Failed!");
    }

    static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream("tst.config"));
        return properties;
    }

    public ApplicationConnection createConnection(TransportConnection transportConnection) throws IOException {
        return new ServerConnection(this, transportConnection);
    }

    public String toString() {
        return "TSTServer";
    }
}
