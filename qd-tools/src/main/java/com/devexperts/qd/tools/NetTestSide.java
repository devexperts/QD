/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
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
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        this.statisticsCollector = new NetTestStatisticsCollector(this);
        this.connectors = new ArrayList<>();
        this.threads = new ArrayList<>();
        List<String> generateSymbols;
        if (config.ipfPath != null) {
            try {
                List<InstrumentProfile> readProfiles = new InstrumentProfileReader().readFromFile(config.ipfPath);
                log.info("Read " + readProfiles.size() + " profiles from " + LogUtil.hideCredentials(config.ipfPath));
                generateSymbols = readProfiles.stream().map(InstrumentProfile::getSymbol).limit(config.totalSymbols)
                    .collect(Collectors.toList());
            } catch (IOException e) {
                log.error("Error reading source " + LogUtil.hideCredentials(config.ipfPath), e);
                throw new IllegalArgumentException(e);
            }
        } else if (config.minLength > 0 && config.minLength == config.maxLength) {
            generateSymbols = SymbolGenerator.generateSymbols(config.totalSymbols, config.minLength);
        } else if (config.minLength > 0) {
            generateSymbols = SymbolGenerator.generateSymbols(config.totalSymbols, config.minLength, config.maxLength);
        } else {
            generateSymbols = SymbolGenerator.generateSymbols(config.totalSymbols);
        }
        this.symbols = new SymbolList(generateSymbols.toArray(new String[0]), SCHEME.getCodec());
        log.info(this.symbols.size() + " symbols were loaded (including " + this.symbols.getUncodedCount() + " not coded)");
    }

    protected abstract void createDistributor(QDEndpoint endpoint, int index);

    protected abstract void createAgent(QDEndpoint endpoint, int index);

    public void start() {
        log.info("Using record " + RECORD_NAME);
        QDEndpoint firstEndpoint = null;
        for (int i = 1; i <= config.instanceCount; i++) {
            QDEndpoint endpoint = config.optionCollector.createEndpoint(config.name + "." + i);
            createDistributor(endpoint, i);
            createAgent(endpoint, i);
            if (firstEndpoint == null)
                firstEndpoint = endpoint;
        }
        firstEndpoint.registerMonitoringTask(statisticsCollector);
        MessageConnectors.startMessageConnectors(connectors);
    }

    public SymbolList createSublist() {
        if (config.sliceSelection) {
            return symbols.selectNextSequenceSublist(config.symbolsPerEntity);
        } else {
            return symbols.selectRandomSublist(config.symbolsPerEntity);
        }
    }
}
