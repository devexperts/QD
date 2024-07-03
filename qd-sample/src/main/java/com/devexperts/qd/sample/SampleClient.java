/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.connector.proto.Configurable;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory;
import com.devexperts.qd.stats.QDStats;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

public class SampleClient {
    private SampleClient() {}

    public static void main(String[] args) {
        initClient(args.length == 0 ? "127.0.0.1:5555" : args[0], 1236);
    }

    public static final String[] STRINGS = {
        "SPX", "NDX", "DJX", "QQQ", "XEO",
        "C", "T", "IBM", "BEA", "AOL",
        "MSFT", "INTC", "SUNW", "ORCL", "YHOO",
        "/LNY", "/SOY", "/GS3U", "/SP3UG", "Very Long"
    };

    static void initClient(String address, int jmxHtmlPort) {
        DataScheme scheme = SampleScheme.getInstance();
        QDEndpoint endpoint = QDEndpoint.newBuilder()
            .withName("client")
            .withScheme(scheme)
            .withCollectors(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .withProperties(Sample.getMonitoringProps(jmxHtmlPort))
            .build();
        endpoint.getStream().setEnableWildcards(true);

        // Client QD connection.
        endpoint.addConnectors(MessageConnectors.createMessageConnectors(
            new ClientAdapterFactory(endpoint), address, endpoint.getRootStats()));
        endpoint.startConnectors();

        // Ticker GUI creation.
        List<String> tickerSymbols = new ArrayList<>(Arrays.asList(STRINGS));
        TickerModel tickerModel = new TickerModel(endpoint.getTicker(), createColumns(new DataRecord[] {
            scheme.getRecord(0), scheme.getRecord(1) }), tickerSymbols.toArray(new String[0]));
        JPanel tickerPanel = new JPanel(new BorderLayout());
        tickerPanel.add(new ActivatorCheckBox(tickerModel), BorderLayout.SOUTH);
        tickerPanel.add(new JLabel("Ticker"), BorderLayout.NORTH);
        tickerPanel.add(new JScrollPane(new JTable(tickerModel)), BorderLayout.CENTER);

        // Stream GUI creation.
        StreamModel streamModel = new StreamModel(endpoint.getStream(), createColumns(scheme.getRecord(1)), STRINGS);
        JPanel streamPanel = new JPanel(new BorderLayout());
        streamPanel.add(new ActivatorCheckBox(streamModel), BorderLayout.SOUTH);
        streamPanel.add(new JLabel("Stream subscribe by list"), BorderLayout.NORTH);
        streamPanel.add(new JScrollPane(new JTable(streamModel)), BorderLayout.CENTER);

        // Stream GUI creation 2
        StreamModel streamModel2 = new StreamModel(endpoint.getStream(),
            createColumns(scheme.getRecord(1)), new String[] {"*"});
        JPanel streamPanel2 = new JPanel(new BorderLayout());
        streamPanel2.add(new ActivatorCheckBox(streamModel2), BorderLayout.SOUTH);
        streamPanel2.add(new JLabel("Stream subscribe by *"), BorderLayout.NORTH);
        streamPanel2.add(new JScrollPane(new JTable(streamModel2)), BorderLayout.CENTER);

        // History GUI creation.
        HistoryModel historyModel = new HistoryModel(endpoint.getHistory(),
            createColumns(scheme.getRecord(2)), STRINGS[0]);
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.add(new ActivatorCheckBox(historyModel), BorderLayout.SOUTH);
        historyPanel.add(new JLabel("History for " + STRINGS[0]), BorderLayout.NORTH);
        historyPanel.add(new JScrollPane(new JTable(historyModel)), BorderLayout.CENTER);

        // Frame packaging.
        JFrame frame = new JFrame();
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 2));
        panel.add(tickerPanel);
        panel.add(streamPanel);
        panel.add(streamPanel2);
        panel.add(historyPanel);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800);
        frame.setVisible(true);
    }

    private static GUIColumn[] createColumns(DataRecord record) {
        return createColumns(new DataRecord[] { record });
    }

    private static GUIColumn[] createColumns(DataRecord[] records) {
        ArrayList<GUIColumn> result = new ArrayList<>();
        for (DataRecord record : records) {
            int nint = record.getIntFieldCount();
            int nobj = record.getObjFieldCount();
            for (int j = 0; j < nint; j++) {
                result.add(new SampleColumn.IntColumn(record.getIntField(j)));
            }
            for (int j = 0; j < nobj; j++) {
                result.add(new SampleColumn.ObjColumn(record.getObjField(j)));
            }
        }
        return result.toArray(new GUIColumn[0]);
    }

    private static class ClientAdapterFactory extends DistributorAdapter.Factory
        implements SocketMessageAdapterFactory
    {
        boolean customAuth;

        private ClientAdapterFactory(QDEndpoint endpoint) {
            super(endpoint, null);
        }

        public boolean isCustomAuth() {
            return customAuth;
        }

        @Configurable
        public void setCustomAuth(boolean customAuth) {
            this.customAuth = customAuth;
        }

        @Override
        public MessageAdapter createAdapterWithSocket(Socket socket, QDStats stats)
            throws SecurityException, IOException
        {
            // Send auth token to the other side.
            if (customAuth) {
                socket.getOutputStream().write(SampleServer.AUTH_TOKEN_BYTES);
            }
            return createAdapter(stats);
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new SwingDistributorAdapter(stats);
        }

        private class SwingDistributorAdapter extends DistributorAdapter {
            SwingDistributorAdapter(QDStats stats) {
                super(ClientAdapterFactory.this.endpoint, ClientAdapterFactory.this.ticker,
                    ClientAdapterFactory.this.stream, ClientAdapterFactory.this.history, null, null, stats, null);
            }

            @Override
            protected void processData(DataIterator iterator, final MessageType message) {
                final RecordBuffer buf = new RecordBuffer();
                buf.processData(iterator);
                SwingUtilities.invokeLater(() -> SwingDistributorAdapter.super.processData(buf, message));
            }
        }
    }
}
