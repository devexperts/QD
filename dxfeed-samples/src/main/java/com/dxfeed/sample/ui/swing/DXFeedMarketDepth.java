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
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.market.OrderBookModel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
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

public class DXFeedMarketDepth {

    static class BookTableModel extends DefaultTableModel {

        BookTableModel() {
            super();
            addColumn("EX");
            addColumn("MM");
            addColumn("Price");
            addColumn("Size");
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @SuppressWarnings("unchecked")
        void updateOrders(List<Order> orders) {
            // update model
            Vector data = getDataVector();
            data.clear();
            for (Order order : orders) {
                Vector row = new Vector(4);
                row.add(order.getExchangeCode());
                row.add(order.getMarketMaker());
                row.add(order.getPrice());
                row.add(order.getSizeAsDouble());
                data.add(row);
            }
            fireTableDataChanged();
        }
    }

    private JTextField symbolText;
    private JTable bidTable;
    private JTable askTable;
    private JPanel form;
    private JLabel description;

    private final OrderBookModel orderBook = new OrderBookModel();
    private final DXFeedSubscription<Profile> profileSub = new DXFeedSubscription<>(Profile.class);

    private final BookTableModel bidModel = new BookTableModel();
    private final BookTableModel askModel = new BookTableModel();

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

    private void initTableModel(JTable table, final BookTableModel model, final ObservableListModel<Order> book) {
        table.setModel(model);
        book.addListener(change -> model.updateOrders(book));
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
