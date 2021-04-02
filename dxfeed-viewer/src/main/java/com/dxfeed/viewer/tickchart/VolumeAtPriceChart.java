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
package com.dxfeed.viewer.tickchart;

import com.dxfeed.event.market.TimeAndSale;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;

public class VolumeAtPriceChart {
    private Color buyColor;
    private Color sellColor;
    private Color undefinedColor;
    private double maxPrice;
    private double minPrice;
    private int height;
    private int width;
    private int barHeight;
    private double maxSize;
    private double priceStep;
    private ArrayList<VolumeAtPriceBar> volumeAtPrice;

    public VolumeAtPriceChart(double maxPrice, double minPrice, int width, int height, int barHeight, Color buyColor, Color sellColor, Color undefinedColor) {
        this.maxPrice = maxPrice;
        this.minPrice = minPrice;
        this.maxSize = Double.NEGATIVE_INFINITY;

        this.height = height;
        this.width = width;
        this.barHeight = barHeight < 3 ? 3 : barHeight > height ? height : barHeight;
        while (this.barHeight > 3 && height % this.barHeight > 1)
            this.barHeight--;
        int numIntervals = height / this.barHeight;
        this.priceStep = (maxPrice - minPrice) / numIntervals;

        this.volumeAtPrice = new ArrayList<>(numIntervals);
        for (int i = 0; i < numIntervals; i++)
            this.volumeAtPrice.add(null);

        this.buyColor = buyColor;
        this.sellColor = sellColor;
        this.undefinedColor = undefinedColor;
    }

    public void add(TimeAndSale timeAndSale) {
        int i = (int) ((maxPrice - timeAndSale.getPrice()) / priceStep);
        if (i >= volumeAtPrice.size())
            i = volumeAtPrice.size() - 1;

        VolumeAtPriceBar volumeAtPriceBar = volumeAtPrice.get(i);
        if (volumeAtPriceBar == null)
            volumeAtPrice.set(i, volumeAtPriceBar = new VolumeAtPriceBar());

        volumeAtPriceBar.add(timeAndSale);
        if (maxSize < volumeAtPriceBar.getMaxSize())
            maxSize = volumeAtPriceBar.getMaxSize();
    }

    public VolumeAtPriceBar getVolumeAtY(int y) {
        int i = (int) (y / barHeight);
        if (i >= volumeAtPrice.size())
            i = volumeAtPrice.size() - 1;
        return volumeAtPrice.get(i);
    }

    public void paint(Graphics2D g2d, int x, int y) {
        double zoomFactor = width / (2.0 * maxSize);  // draw from center - sell to the left, buy + undefined to the right
        int undefinedBarWidth;
        int barWidth;

        for (int i = 0; i < volumeAtPrice.size(); i++) {
            VolumeAtPriceBar volumeAtPriceBar = volumeAtPrice.get(i);
            undefinedBarWidth = 0;

            if (volumeAtPriceBar != null) {
                if (volumeAtPriceBar.getUndefinedSize() > 0) {
                    g2d.setColor(undefinedColor);
                    undefinedBarWidth = (int) (volumeAtPriceBar.getUndefinedSize() * zoomFactor);
                    g2d.fillRect(x + width / 2, y + i * barHeight, undefinedBarWidth + 1, barHeight - 1);
                }

                if (volumeAtPriceBar.getBuySize() > 0) {
                    g2d.setColor(buyColor);
                    barWidth = (int) (volumeAtPriceBar.getBuySize() * zoomFactor);
                    g2d.fillRect(x + width / 2 + undefinedBarWidth, y + i * barHeight, barWidth + 1, barHeight - 1);
                }

                if (volumeAtPriceBar.getSellSize() > 0) {
                    g2d.setColor(sellColor);
                    barWidth = (int) (volumeAtPriceBar.getSellSize() * zoomFactor);
                    g2d.fillRect(x + width / 2 - barWidth - 1, y + i * barHeight, barWidth + 1, barHeight - 1);
                }
            }
        }
    }
}
