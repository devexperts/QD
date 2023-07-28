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
package com.dxfeed.sample.ui.swing;

import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Trade;
import com.dxfeed.ondemand.OnDemandService;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

public class DXFeedQuoteTableOnDemand {
    private static final List<String> SYMBOLS = readSymbols("symbols_ondemand.txt");
    private static final int N = SYMBOLS.size();
    private static final Map<String, Integer> SYMBOL_IDS = new HashMap<>();

    static {
        for (int i = 0; i < N; i++) {
            SYMBOL_IDS.put(SYMBOLS.get(i), i);
        }
    }

    private static int getSymbolId(MarketEvent event) {
        return SYMBOL_IDS.get(event.getEventSymbol()); // this would throw NPE if event symbol is unknown, but we won't actually receive any unknown symbols
    }

    private static List<String> readSymbols(String fileName) {
        try {
            Scanner in = new Scanner(new File(fileName));
            in.useDelimiter("[,\\s]+");
            try {
                List<String> result = new ArrayList<>();
                while (in.hasNext())
                    result.add(in.next());
                return result;
            } finally {
                in.close();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private JPanel form;
    private JTable quoteTable;
    private JButton replayFlashcrashButton;
    private JButton pauseButton;
    private JButton stopAndResumeButton;
    private JComboBox<String> speedComboBox;
    private JLabel replayTimeLabel;
    private JLabel statusLabel;
    private JButton stopAndClearButton;

    private final SimpleDateFormat timeFormat;

    {
        timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'EST'");
        timeFormat.setTimeZone(TimeUtil.getTimeZone("America/New_York"));
    }

    private final DXEndpoint endpoint = DXEndpoint.getInstance();
    private final OnDemandService onDemand = OnDemandService.getInstance(endpoint);
    private final DXFeed feed = endpoint.getFeed();
    private final DXFeedSubscription<Quote> quoteSub;
    private final DXFeedSubscription<Trade> tradeSub;

    private final DefaultTableModel tableModel = new DefaultTableModel();

    private final Quote[] quotes = new Quote[N];
    private final Trade[] trades = new Trade[N];

    {
        for (int i = 0; i < N; i++) {
            quotes[i] = new Quote();
            trades[i] = new Trade();
        }
    }

    public static void main(String[] args) {
        DXEndpoint.getInstance().executor(new SwingExecutor(20)); // configure Swing executor for 50 fps
        DXFeedQuoteTableOnDemand instance = new DXFeedQuoteTableOnDemand();
        SwingUtilities.invokeLater(instance::go);
    }

    public DXFeedQuoteTableOnDemand() {
        quoteSub = feed.createSubscription(Quote.class);
        tradeSub = feed.createSubscription(Trade.class);
    }

    private void go() {
        setupUI();
        initTableModel();
        initListeners();
        createFrame();
    }

    private void createFrame() {
        JFrame frame = new JFrame("DXFeed Quote Table OnDemand");
        frame.add(form);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void initListeners() {
        quoteSub.addEventListener(events -> {
            for (Quote quote : events) {
                quotes[getSymbolId(quote)] = quote;
            }
            updateTableModel();
        });
        tradeSub.addEventListener(events -> {
            for (Trade trade : events) {
                trades[getSymbolId(trade)] = trade;
            }
            updateTableModel();
        });

        quoteSub.addSymbols(SYMBOLS);
        tradeSub.addSymbols(SYMBOLS);

        endpoint.addStateChangeListener(evt -> statusLabel.setText("Status: " + evt.getNewValue()));

        onDemand.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
            case "replaySupported":
                replayFlashcrashButton.setEnabled((Boolean) evt.getNewValue());
                break;
            case "replay":
                updateReplay((Boolean) evt.getNewValue());
                break;
            case "speed":
                speedComboBox.setSelectedItem(evt.getNewValue().toString());
                break;
            case "time":
                replayTimeLabel.setText("Replay time: " + timeFormat.format(evt.getNewValue()));
                break;
            }
        });

        updateReplay(false);

        replayFlashcrashButton.addActionListener(e -> replayFlashCrash());
        pauseButton.addActionListener(e -> onDemand.pause());
        stopAndResumeButton.addActionListener(e -> onDemand.stopAndResume());
        stopAndClearButton.addActionListener(e -> onDemand.stopAndClear());
        speedComboBox.addActionListener(e -> onDemand.setSpeed(Double.valueOf(speedComboBox.getSelectedItem().toString())));
    }

    private void replayFlashCrash() {
        try {
            onDemand.replay(timeFormat.parse("2010-05-06 14:47:48 EST"), 1);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void updateReplay(boolean replay) {
        pauseButton.setEnabled(replay);
        speedComboBox.setEnabled(replay);
        replayTimeLabel.setEnabled(replay);
    }

    private void initTableModel() {
        tableModel.addColumn("Symbol");
        tableModel.addColumn("Last");
        tableModel.addColumn("LastEx");
        tableModel.addColumn("Last Size");
        tableModel.addColumn("Bid");
        tableModel.addColumn("Ask");
        tableModel.addColumn("Bid Size");
        tableModel.addColumn("Ask Size");
        updateTableModel();
        quoteTable.setModel(tableModel);
    }

    private void updateTableModel() {
        tableModel.setNumRows(0);
        for (int i = 0; i < N; i++) {
            Quote quote = quotes[i];
            Trade trade = trades[i];
            tableModel.addRow(new Object[] {
                SYMBOLS.get(i),
                trade.getPrice(),
                trade.getExchangeCode(),
                trade.getSizeAsDouble(),
                quote.getBidPrice(),
                quote.getAskPrice(),
                quote.getBidSizeAsDouble(),
                quote.getAskSizeAsDouble(),
            });
        }
    }

    private void setupUI() {
        form = new JPanel();
        form.setLayout(new GridBagLayout());
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(panel1, gbc);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));

        JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, BorderLayout.CENTER);

        quoteTable = new JTable();
        scrollPane1.setViewportView(quoteTable);

        JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        panel1.add(panel2, BorderLayout.NORTH);

        JPanel panel3 = new JPanel();
        panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(panel3, gbc);

        replayFlashcrashButton = new JButton();
        replayFlashcrashButton.setText("Replay \"Flashcrash\" ");
        replayFlashcrashButton.setMnemonic('F');
        replayFlashcrashButton.setDisplayedMnemonicIndex(8);
        panel3.add(replayFlashcrashButton);

        stopAndResumeButton = new JButton();
        stopAndResumeButton.setEnabled(true);
        stopAndResumeButton.setText("Stop&Resume");
        stopAndResumeButton.setMnemonic('R');
        stopAndResumeButton.setDisplayedMnemonicIndex(5);
        panel3.add(stopAndResumeButton);

        stopAndClearButton = new JButton();
        stopAndClearButton.setText("Stop&Clear");
        stopAndClearButton.setMnemonic('C');
        stopAndClearButton.setDisplayedMnemonicIndex(5);
        panel3.add(stopAndClearButton);

        JLabel setSpeedLabel = new JLabel();
        setSpeedLabel.setText("Set speed");
        panel3.add(setSpeedLabel);

        speedComboBox = new JComboBox<>();
        DefaultComboBoxModel<String> defaultComboBoxModel1 = new DefaultComboBoxModel<>();
        defaultComboBoxModel1.addElement("0.0");
        defaultComboBoxModel1.addElement("0.1");
        defaultComboBoxModel1.addElement("0.5");
        defaultComboBoxModel1.addElement("1.0");
        defaultComboBoxModel1.addElement("2.0");
        defaultComboBoxModel1.addElement("3.0");
        defaultComboBoxModel1.addElement("5.0");
        defaultComboBoxModel1.addElement("10.0");
        speedComboBox.setModel(defaultComboBoxModel1);
        panel3.add(speedComboBox);

        pauseButton = new JButton();
        pauseButton.setText("Pause");
        pauseButton.setMnemonic('P');
        pauseButton.setDisplayedMnemonicIndex(0);
        panel3.add(pauseButton);

        JPanel panel4 = new JPanel();
        panel4.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(panel4, gbc);

        statusLabel = new JLabel();
        statusLabel.setText("Status");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel4.add(statusLabel, gbc);

        replayTimeLabel = new JLabel();
        replayTimeLabel.setText("Replay time: 0");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel4.add(replayTimeLabel, gbc);
    }
}
