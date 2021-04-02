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
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.model.IndexedEventModel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

public class DXFeedTimeAndSales {
    private static final int NUMBER_OF_PRESENT_TRADES = 30;

    private JPanel form;
    private JTextField symbolText;
    private JLabel description;
    private JTable timeAndSalesTable;

    private final IndexedEventModel<TimeAndSale> timeAndSales = new IndexedEventModel<>(TimeAndSale.class);
    private final DXFeedSubscription<Profile> profileSub = new DXFeedSubscription<>(Profile.class);

    private final DefaultTableModel tableModel = new DefaultTableModel();

    public static void main(String[] args) {
        DXEndpoint.getInstance().executor(new SwingExecutor(20)); // configure Swing executor for 50 fps
        DXFeedTimeAndSales instance = new DXFeedTimeAndSales();
        SwingUtilities.invokeLater(instance::go);
    }

    private DXFeedTimeAndSales() {
        DXFeed feed = DXFeed.getInstance();
        timeAndSales.setSizeLimit(NUMBER_OF_PRESENT_TRADES);
        timeAndSales.attach(feed);
        profileSub.attach(feed);
    }

    private void go() {
        setupUI();
        initTableModel();
        initListeners();
        createFrame();
    }

    private void initListeners() {
        timeAndSales.getEventsList().addListener(change -> timeAndSalesReceived());
        profileSub.addEventListener(this::profilesReceived);
        symbolText.addActionListener(e -> symbolChanged());
    }

    private void createFrame() {
        JFrame frame = new JFrame("DXFeed Time & Sales");
        frame.add(form);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void initTableModel() {
        tableModel.addColumn("Time");
        tableModel.addColumn("Index");
        tableModel.addColumn("EX");
        tableModel.addColumn("Price");
        tableModel.addColumn("Size");
        tableModel.addColumn("Bid");
        tableModel.addColumn("Ask");
        tableModel.addColumn("SC");
        timeAndSalesTable.setModel(tableModel);
    }

    private void symbolChanged() {
        timeAndSales.clear();
        profileSub.clear();
        timeAndSales.clear();
        description.setText("");
        tableModel.setRowCount(0);
        String symbol = symbolText.getText();
        if (symbol.length() > 0) {
            timeAndSales.setSymbol(symbol);
            profileSub.setSymbols(Collections.singletonList(symbol));
        }
    }

    private synchronized void timeAndSalesReceived() {
        ArrayList<TimeAndSale> rows = new ArrayList<>(timeAndSales.getEventsList());
        Collections.sort(rows, Comparator.comparing(TimeAndSale::getTime));
        tableModel.setRowCount(0);
        for (TimeAndSale timeAndSale : rows) {
            tableModel.addRow(new Object[] {
                timeAndSale.getTime(),
                timeAndSale.getIndex(),
                timeAndSale.getExchangeCode(),
                timeAndSale.getPrice(),
                timeAndSale.getSizeAsDouble(),
                timeAndSale.getBidPrice(),
                timeAndSale.getAskPrice(),
                timeAndSale.getExchangeSaleConditions()
            });
        }
    }

    private void profilesReceived(List<Profile> events) {
        for (Profile event : events) {
            if (event.getDescription() != null) {
                description.setText(event.getDescription());
            }
        }
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
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(panel1, gbc);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));

        JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(spacer1, gbc);

        symbolText = new JTextField();
        symbolText.setColumns(10);
        symbolText.setMaximumSize(symbolText.getPreferredSize());
        symbolText.setMinimumSize(symbolText.getPreferredSize());
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(symbolText, gbc);

        JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(spacer2, gbc);

        description = new JLabel();
        description.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 3.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(description, gbc);

        JLabel symbolLabel = new JLabel();
        symbolLabel.setHorizontalAlignment(10);
        symbolLabel.setText("Symbol");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(symbolLabel, gbc);

        JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(panel2, gbc);
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));

        JScrollPane scrollPane1 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(scrollPane1, gbc);

        timeAndSalesTable = new JTable();
        scrollPane1.setViewportView(timeAndSalesTable);
    }
}
