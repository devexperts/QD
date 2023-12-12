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

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.SystemProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * This class represents either producer or consumer side of NetTest tool.
 * Each side creates one or several collectors of specified type and
 * connects each of them to an external-address on the one side
 * (agent-side or distributor-side) and to special processing thread which
 * gathers statistics ({@link NetTestWorkingThread}) on the other side.
 *
 * @see NetTest
 * @see NetTestProducerSide
 * @see NetTestConsumerSide
 */
abstract class NetTestSide {

    private static final Logging log = Logging.getLogging(NetTestSide.class);
    
    private static final long GENERATOR_SEED = 1416948710541751L;

    private static final String RECORD_NAME =
        SystemProperties.getProperty(NetTest.class, "record", "TimeAndSale");

    protected static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    protected static final DataRecord RECORD = SCHEME.findRecordByName(RECORD_NAME);

    protected final NetTestConfig config;
    protected final SymbolList symbols;
    protected final NetTestStatisticsCollector statisticsCollector;
    protected final List<MessageConnector> connectors;
    protected final List<NetTestWorkingThread> threads;

    NetTestSide(NetTestConfig config) {
        this.config = config;
        this.symbols = generateSymbols(config);
        this.statisticsCollector = new NetTestStatisticsCollector(this);
        this.connectors = new ArrayList<>();
        this.threads = new ArrayList<>();
    }

    private static SymbolList generateSymbols(NetTestConfig config) {
        SymbolCodec codec = SCHEME.getCodec();
        Random rnd = new Random(GENERATOR_SEED);
        int n = config.totalSymbols;
        Set<String> symbolSet = new HashSet<String>(n + (n >> 1));
        while (symbolSet.size() < n) {
            symbolSet.add(generateSymbol(rnd));
        }
        String[] s = symbolSet.toArray(new String[n]);
        SymbolList res = new SymbolList(s, codec);
        log.info(res.size() + " random symbols were generated (including " + res.getUncodedCount() + " not coded)");
        return res;
    }

    private static String generateSymbol(Random r) {
        int len = r.nextInt(3) + r.nextInt(4) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (r.nextInt(50) == 0)
                sb.append((char) ('0' + r.nextInt(10)));
            else
                sb.append((char) ('A' + r.nextInt(26)));
        }
        return sb.toString();
    }

    protected abstract void createDistributor(QDEndpoint endpoint, int index);

    protected abstract void createAgent(QDEndpoint endpoint, int index);

    public void start() {
        log.info("Using record " + RECORD_NAME);
        QDEndpoint firstEndpoint = null;
        for (int i = 1; i <= config.connectionsNum; i++) {
            QDEndpoint endpoint = config.optionCollector.createEndpoint("nettest." + i);
            createDistributor(endpoint, i);
            createAgent(endpoint, i);
            if (firstEndpoint == null)
                firstEndpoint = endpoint;
        }
        firstEndpoint.registerMonitoringTask(statisticsCollector);
        MessageConnectors.startMessageConnectors(connectors);
    }
}
