/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.sample.ui.swing;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import com.dxfeed.api.*;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.market.OrderBookModel;

public class DXFeedMarketDepth {
    private JTextField symbolText;
    private JTable bidTable;
    private JTable askTable;
    private JPanel form;
    private JLabel description;

    private final OrderBookModel orderBook = new OrderBookModel();
    private final DXFeedSubscription<Profile> profileSub = new DXFeedSubscription<>(Profile.class);

    private final DefaultTableModel bidModel = new DefaultTableModel();
    private final DefaultTableModel askModel = new DefaultTableModel();

    public static void main(String[] args) {
        DXEndpoint.getInstance().executor(new SwingExecutor(20)); // configure Swing executor for 50 fps
        DXFeedMarketDepth instance = new DXFeedMarketDepth();
        SwingUtilities.invokeLater(instance::go);
    }

    private DXFeedMarketDepth() {
        DXFeed feed = DXFeed.getInstance();
        orderBook.attach(feed);
        profileSub.attach(feed);
    }

    private void go() {
        setupUI();
        initTableModel(bidTable, bidModel, orderBook.getBuyOrders());
        initTableModel(askTable, askModel, orderBook.getSellOrders());
        initListeners();
        createFrame();
    }

    private void initListeners() {
        profileSub.addEventListener(this::profilesReceived);
        symbolText.addActionListener(this::symbolChanged);
    }

    private void createFrame() {
        JFrame frame = new JFrame("DXFeed Market Depth");
        frame.add(form);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void initTableModel(JTable table, final DefaultTableModel model, final ObservableListModel<Order> book) {
        model.addColumn("EX");
        model.addColumn("MM");
        model.addColumn("Price");
        model.addColumn("Size");
        table.setModel(model);

        book.addListener(change -> rebuildTableModel(model, book));
    }

    private void rebuildTableModel(DefaultTableModel model, List<Order> orders) {
        // update model
        model.setRowCount(0);
        for (Order order : orders) {
            model.addRow(new Object[] {
                order.getExchangeCode(),
                order.getMarketMaker(),
                order.getPrice(),
                order.getSizeAsDouble()
            });
        }
    }

    private void symbolChanged(ActionEvent e) {
        profileSub.clear();
        description.setText("");
        String symbol = symbolText.getText();
        if (symbol.length() > 0) {
            orderBook.setSymbol(symbol);
            profileSub.setSymbols(Collections.singletonList(symbol));
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
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(panel1, gbc);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));

        JLabel bidLabel = new JLabel();
        bidLabel.setText("Bid");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel1.add(bidLabel, gbc);

        JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(spacer1, gbc);

        JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel1.add(spacer2, gbc);

        JLabel askLabel = new JLabel();
        askLabel.setText("Ask");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        panel1.add(askLabel, gbc);

        JScrollPane scrollPane1 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scrollPane1, gbc);

        bidTable = new JTable();
        scrollPane1.setViewportView(bidTable);
        JScrollPane scrollPane2 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scrollPane2, gbc);

        askTable = new JTable();
        scrollPane2.setViewportView(askTable);
        JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(panel2, gbc);
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));

        JLabel symbolLabel = new JLabel();
        symbolLabel.setText("Symbol");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel2.add(symbolLabel, gbc);
        symbolText = new JTextField();
        symbolText.setColumns(10);
        symbolText.setMaximumSize(symbolText.getPreferredSize());
        symbolText.setMinimumSize(symbolText.getPreferredSize());
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(symbolText, gbc);

        JPanel spacer3 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(spacer3, gbc);

        description = new JLabel();
        description.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 3.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel2.add(description, gbc);

        JPanel spacer4 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(spacer4, gbc);
    }
}
