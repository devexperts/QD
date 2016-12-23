/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.viewer.tickchart;

import com.dxfeed.event.market.TimeAndSale;

public class VolumeAtPriceBar {
    private long buySize = 0;
    private long sellSize = 0;
    private long undefinedSize = 0;

    public long getBuySize() {
        return buySize;
    }

    public long getSellSize() {
        return sellSize;
    }

    public long getUndefinedSize() {
        return undefinedSize;
    }

    public long getMaxSize() {
        return buySize + undefinedSize > sellSize? buySize + undefinedSize : sellSize;
    }

    public VolumeAtPriceBar(TimeAndSale timeAndSale) {
        add(timeAndSale);
    }

    public void add(TimeAndSale timeAndSale) {
        switch (timeAndSale.getAggressorSide()) {
        case BUY: this.buySize += timeAndSale.getSize(); break;
        case SELL: this.sellSize += timeAndSale.getSize(); break;
        case UNDEFINED: this.undefinedSize += timeAndSale.getSize(); break;
        default: throw new IllegalStateException(timeAndSale.toString());
        }
    }
}
