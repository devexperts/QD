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

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSScheduler;
import com.devexperts.mars.jvm.CpuCounter;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static com.devexperts.qd.sample.stresstest.TSTServer.loadProperties;

/**
 * Network stress-test clients.
 */
public class TSTClients {
    private static final Logging log = Logging.getLogging(TSTClients.class);

    public static void main(String[] args) throws IOException, InterruptedException, GeneralSecurityException {
        new TSTClients(loadProperties()).doWork();
    }

    private final Properties properties;
    final String host;
    final int port;
    final long heartbeatPeriod;
    final long heartbeatPeriodVariation;
    final SocketFactory socketFactory;

    public TSTClients(Properties properties) throws GeneralSecurityException, IOException {
        this.properties = properties;
        host = properties.getProperty("host");
        port = Integer.parseInt(properties.getProperty("port"));
        heartbeatPeriod = TimePeriod.valueOf(properties.getProperty("heartbeatPeriod")).getTime();
        heartbeatPeriodVariation = TimePeriod.valueOf(properties.getProperty("heartbeatPeriodVariation")).getTime();
        boolean useSSL = SystemProperties.parseBooleanValue(properties.getProperty("useSSL"));
        if (useSSL) {
            log.info("Using SSL");
            SSLContext context = SSLContext.getInstance("TLS");
            String password = "qdsample";
            String keyStoreType = "jks";
            String keyStoreUrl = "samplecert\\qdkeystore";  // todo:
            String keyManagementAlgorithm = "SunX509";
            String trustManagementAlgorithm = "PKIX";
            char[] passwordChars = password.toCharArray();
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            ks.load(new URLInputStream(keyStoreUrl), passwordChars);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagementAlgorithm);
            kmf.init(ks, passwordChars);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(trustManagementAlgorithm);
            tmf.init(ks);
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            socketFactory = context.getSocketFactory();
        } else {
            socketFactory = SocketFactory.getDefault();
        }
    }

    @SuppressWarnings({"InfiniteLoopStatement"})
    private void doWork() throws IOException, InterruptedException {
        int n = Integer.parseInt(properties.getProperty("numberOfClients"));
        log.info("Starting " + n + " clients...");
        long startTime = System.currentTimeMillis();
        final ArrayList<Client> clients = new ArrayList<Client>();
        for (int i = 0; i < n; i++) {
            Client client = new Client(this);
            clients.add(client);
            client.start();
            if (i % 100 == 99)
                log.info(i + 1 + " clients started");
        }
        log.info(n + " clients started in " + (System.currentTimeMillis() - startTime) + "ms");

        long loggingPeriod = TimePeriod.valueOf(properties.getProperty("loggingPeriod")).getTime();
        MARSScheduler.schedule(new Runnable() {
            long lastTime = System.currentTimeMillis();
            CpuCounter cpuCounter = new CpuCounter();
            public void run() {
                long cnt = 0;
                long reads = 0;
                long bytes = 0;
                for (Client client : clients) {
                    if (!client.isAlive())
                        continue;
                    cnt++;
                    reads += client.getReadsCount();
                    bytes += client.getBytesCount();
                }
                long curTime = System.currentTimeMillis();
                long deltaTime = curTime - lastTime;
                log.info("clients: " + cnt +
                    ", reads/sec: " + (reads * 1000 / deltaTime) +
                    ", bytes/sec: " + (bytes * 1000 / deltaTime) +
                    ", CPU usage: " + Math.floor(cpuCounter.getCpuUsage() * 10000) / 100 + "%");
                lastTime = curTime;
            }
        }, loggingPeriod, TimeUnit.MILLISECONDS);
    }
}
