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
package com.dxfeed.sample.ui.swing;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.Trade;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

public class DXFeedQuoteTable {
    private static final List<String> SYMBOLS = readSymbols("symbols.txt");
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

    private final DefaultTableModel tableModel = new DefaultTableModel();

    private final Quote[] quotes = new Quote[N];
    private final Trade[] trades = new Trade[N];
    private final Summary[] summaries = new Summary[N];
    private final Profile[] profiles = new Profile[N];

    {
        for (int i = 0; i < N; i++) {
            quotes[i] = new Quote();
            trades[i] = new Trade();
            summaries[i] = new Summary();
            profiles[i] = new Profile();
        }
    }

    public static void main(String[] args) {
        DXEndpoint.getInstance().executor(new SwingExecutor(20)); // configure Swing executor for 50 fps
        DXFeedQuoteTable instance = new DXFeedQuoteTable();
        SwingUtilities.invokeLater(instance::go);
    }

    private void go() {
        setupUI();
        initTableModel();
        initListeners();
        createFrame();
    }

    private void createFrame() {
        JFrame frame = new JFrame("DXFeed Quote Table");
        frame.add(form);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void initListeners() {
        DXFeed feed = DXFeed.getInstance();
        DXFeedSubscription<Quote> quoteSub = feed.createSubscription(Quote.class);
        DXFeedSubscription<Trade> tradeSub = feed.createSubscription(Trade.class);
        DXFeedSubscription<Summary> summarySub = feed.createSubscription(Summary.class);
        DXFeedSubscription<Profile> profileSub = feed.createSubscription(Profile.class);

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
        summarySub.addEventListener(events -> {
            for (Summary summary : events) {
                summaries[getSymbolId(summary)] = summary;
            }
            updateTableModel();
        });
        profileSub.addEventListener(events -> {
            for (Profile profile : events) {
                profiles[getSymbolId(profile)] = profile;
            }
            updateTableModel();
        });

        quoteSub.addSymbols(SYMBOLS);
        tradeSub.addSymbols(SYMBOLS);
        summarySub.addSymbols(SYMBOLS);
        profileSub.addSymbols(SYMBOLS);
    }

    private void initTableModel() {
        tableModel.addColumn("Symbol");
        tableModel.addColumn("Last");
        tableModel.addColumn("LastEx");
        tableModel.addColumn("Change");
        tableModel.addColumn("Bid");
        tableModel.addColumn("BidEx");
        tableModel.addColumn("Ask");
        tableModel.addColumn("AskEx");
        tableModel.addColumn("High");
        tableModel.addColumn("Low");
        tableModel.addColumn("Open");
        tableModel.addColumn("Bid Size");
        tableModel.addColumn("Ask Size");
        tableModel.addColumn("Last Size");
        tableModel.addColumn("Volume");
        tableModel.addColumn("Description");
        updateTableModel();
        quoteTable.setModel(tableModel);
    }

    private void updateTableModel() {
        tableModel.setNumRows(0);
        for (int i = 0; i < N; i++) {
            Quote quote = quotes[i];
            Trade trade = trades[i];
            Summary summary = summaries[i];
            Profile profile = profiles[i];
            tableModel.addRow(new Object[] {
                SYMBOLS.get(i),
                trade.getPrice(),
                trade.getExchangeCode(),
                formatNetChange(trade.getPrice() - summary.getPrevDayClosePrice()),
                quote.getBidPrice(),
                quote.getBidExchangeCode(),
                quote.getAskPrice(),
                quote.getAskExchangeCode(),
                summary.getDayHighPrice(),
                summary.getDayLowPrice(),
                summary.getDayOpenPrice(),
                quote.getBidSizeAsDouble(),
                quote.getAskSizeAsDouble(),
                trade.getSizeAsDouble(),
                trade.getDayVolumeAsDouble(),
                profile.getDescription(),
            });
        }
    }

    private String formatNetChange(double netChange) {
        netChange = Math.floor(netChange * 1e10 + 0.5) / 1e10;
        String netChangeStr = Double.toString(netChange);
        return (netChange > 0 ? "+" : "") + netChangeStr;
    }

    private void setupUI() {
        form = new JPanel();
        form.setLayout(new GridBagLayout());
        JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
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
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scrollPane1, gbc);
        scrollPane1.setPreferredSize(new Dimension(900, 600));

        quoteTable = new JTable();
        scrollPane1.setViewportView(quoteTable);
    }
}
