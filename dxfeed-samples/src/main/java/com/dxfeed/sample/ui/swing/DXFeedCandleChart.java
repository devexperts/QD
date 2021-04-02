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

import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.Profile;
import com.dxfeed.model.TimeSeriesEventModel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class DXFeedCandleChart {
    private JPanel form;
    private JTextField symbolText;
    private JLabel description;
    private JComponent chart;
    private JComboBox<String> periodComboBox;

    private final TimeSeriesEventModel<Candle> candles = new TimeSeriesEventModel<>(Candle.class);
    private final DXFeedSubscription<Profile> profileSub = new DXFeedSubscription<>(Profile.class);

    public static void main(String[] args) {
        DXEndpoint.getInstance(DXEndpoint.Role.FEED).executor(new SwingExecutor(20)); // configure Swing executor for 50 fps
        DXFeedCandleChart instance = new DXFeedCandleChart();
        SwingUtilities.invokeLater(instance::go);
    }

    private DXFeedCandleChart() {
        DXFeed feed = DXFeed.getInstance();
        candles.attach(feed);
        profileSub.attach(feed);
    }

    private void go() {
        setupUI();
        initListeners();
        createFrame();
    }

    private void initListeners() {
        candles.getEventsList().addListener(change -> chart.repaint());
        profileSub.addEventListener(this::profilesReceived);
        symbolText.addActionListener(this::symbolPeriodChanged);
        periodComboBox.addActionListener(this::symbolPeriodChanged);
    }

    private void createFrame() {
        JFrame frame = new JFrame("DXFeed Candle Chart");
        frame.add(form);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void symbolPeriodChanged(ActionEvent e) {
        candles.clear();
        profileSub.clear();
        description.setText("");
        String symbol = symbolText.getText();
        String periodStr = (String) periodComboBox.getSelectedItem();
        if (symbol.length() > 0) {
            CandlePeriod candlePeriod = CandlePeriod.parse(periodStr);
            candles.setSymbol(CandleSymbol.valueOf(symbol, candlePeriod));
            long range = candlePeriod.equals(CandlePeriod.DAY) ? 365 * TimeUtil.DAY : 5 * TimeUtil.DAY;
            candles.setFromTime(System.currentTimeMillis() - range);
            profileSub.setSymbols(Collections.singletonList(symbol));
        }
        chart.repaint();
    }

    double minY;
    double maxY;

    private void initCandleRange() {
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        for (Candle candle : candles.getEventsList()) {
            minY = Math.min(minY, candle.getLow());
            maxY = Math.max(maxY, candle.getHigh());
        }
    }

    private static final int PAD = 5;

    private double getX(int index) {
        int n = candles.getEventsList().size();
        return n <= 1 ? chart.getWidth() / 2.0 :
            PAD + index * (chart.getWidth() - 2 * PAD) / (n - 1);
    }

    private double getY(double value) {
        return minY == maxY ? chart.getHeight() / 2.0 :
            PAD + (maxY - value) * (chart.getHeight() - 2 * PAD) / (maxY - minY);
    }

    private void paintChart(Graphics g1) {
        initCandleRange();
        Graphics2D g = (Graphics2D) g1;
        g.setColor(chart.getBackground());
        g.fillRect(0, 0, chart.getWidth(), chart.getHeight());
        int index = 0;
        for (Candle candle : candles.getEventsList()) {
            boolean up = candle.getClose() >= candle.getOpen();
            g.setColor(up ? Color.green : Color.red);
            double x = getX(index++);
            double open = getY(candle.getOpen());
            double close = getY(candle.getClose());
            double low = getY(candle.getLow());
            double high = getY(candle.getHigh());
            if (up) {
                g.fill(new Rectangle2D.Double(x - 1, close, 2, open - close));
                g.draw(new Line2D.Double(x, low, x, open));
                g.draw(new Line2D.Double(x, close, x, high));
            } else {
                g.draw(new Rectangle2D.Double(x - 1, open, 2, close - open));
                g.draw(new Line2D.Double(x, low, x, close));
                g.draw(new Line2D.Double(x, open, x, high));
            }
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
        gbc.weightx = 1.0;
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
        gbc.gridx = 6;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(description, gbc);

        JLabel symbolLabel = new JLabel();
        symbolLabel.setHorizontalAlignment(SwingConstants.LEFT);
        symbolLabel.setText("Symbol");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(symbolLabel, gbc);

        periodComboBox = new JComboBox<>(new String[] {"1D", "15m"});
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(periodComboBox, gbc);

        JPanel spacer3 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(spacer3, gbc);

        chart = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                paintChart(g);
            }
        };
        chart.setPreferredSize(new Dimension(500, 300));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        form.add(chart, gbc);
        chart.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));
    }
}
