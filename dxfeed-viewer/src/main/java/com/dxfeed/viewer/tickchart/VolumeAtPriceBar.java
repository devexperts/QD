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

public class VolumeAtPriceBar {
    private double buySize;
    private double sellSize;
    private double undefinedSize;

    public double getBuySize() {
        return buySize;
    }

    public double getSellSize() {
        return sellSize;
    }

    public double getUndefinedSize() {
        return undefinedSize;
    }

    public double getMaxSize() {
        return Math.max(buySize + undefinedSize, sellSize);
    }

    public void add(TimeAndSale timeAndSale) {
        switch (timeAndSale.getAggressorSide()) {
        case BUY:
            buySize += timeAndSale.getSizeAsDouble();
            break;
        case SELL:
            sellSize += timeAndSale.getSizeAsDouble();
            break;
        case UNDEFINED:
            undefinedSize += timeAndSale.getSizeAsDouble();
            break;
        default:
            throw new IllegalStateException(timeAndSale.toString());
        }
    }
}
