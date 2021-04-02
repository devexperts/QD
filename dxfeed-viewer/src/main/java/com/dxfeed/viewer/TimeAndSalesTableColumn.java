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
package com.dxfeed.viewer;

import com.dxfeed.event.market.TimeAndSale;

import java.util.TimeZone;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import static com.dxfeed.viewer.TimeAndSalesCellSupport.exchangeValue;
import static com.dxfeed.viewer.TimeAndSalesCellSupport.priceValue;
import static com.dxfeed.viewer.TimeAndSalesCellSupport.sizeValue;
import static com.dxfeed.viewer.TimeAndSalesCellSupport.textValue;
import static com.dxfeed.viewer.TimeAndSalesCellSupport.timeValue;

@SuppressWarnings({"UnusedDeclaration"})
enum TimeAndSalesTableColumn implements EventTableColumn<TimeAndSale> {

    TIME("Time", 150) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return timeValue(timeAndSale.getTime(), isUpdated, !timeAndSale.isValidTick(), timeZone);
        }
    },
    ID("ID", 150) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(Long.toString(timeAndSale.getIndex()), SwingUtilities.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    EXCHANGE("X", 1) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return exchangeValue(timeAndSale.getExchangeCode(), isUpdated, !timeAndSale.isValidTick());
        }
    },
    BID("Bid", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return priceValue(timeAndSale.getBidPrice(), isUpdated, !timeAndSale.isValidTick() || timeAndSale.getBidPrice() > timeAndSale.getAskPrice() || timeAndSale.getPrice() > timeAndSale.getAskPrice());
        }
    },
    ASK("Ask", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return priceValue(timeAndSale.getAskPrice(), isUpdated, !timeAndSale.isValidTick() || timeAndSale.getBidPrice() > timeAndSale.getAskPrice() || timeAndSale.getPrice() < timeAndSale.getBidPrice());
        }
    },
    PRICE("Price", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return priceValue(timeAndSale.getPrice(), isUpdated, !timeAndSale.isValidTick() || timeAndSale.getPrice() < timeAndSale.getBidPrice() || timeAndSale.getPrice() > timeAndSale.getAskPrice());
        }
    },
    SIZE("Size", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return sizeValue(timeAndSale.getSizeAsDouble(), isUpdated, !timeAndSale.isValidTick());
        }
    },
    SALE_CONDITIONS("Conditions", 30) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.getExchangeSaleConditions(), SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    SALE_CONDITIONS_DESCRIPTION("Conditions Description", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(SaleConditions.getSaleConditionsDescription(timeAndSale.getEventSymbol(), timeAndSale.getExchangeSaleConditions()), SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    AGGRESSOR_SIDE("Aggressor", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.getAggressorSide().toString(), SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    CANCEL("Cancel", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.isCancel() ? "Y" : "N", SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    CORRECTION("Correction", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.isCorrection() ? "Y" : "N", SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    ETH("ETH", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.isExtendedTradingHours() ? "Y" : "N", SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    SPREAD("Spread", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.isSpreadLeg() ? "Y" : "N", SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },
    VALID("Valid Tick", 50) {
        @Override
        public ViewerCellValue getValue(TimeAndSale timeAndSale, boolean isUpdated, boolean isDisabled, int tag, int scheme, TimeZone timeZone) {
            return textValue(timeAndSale.isValidTick() ? "Y" : "N", SwingConstants.CENTER, isUpdated, !timeAndSale.isValidTick());
        }
    },

    ;

    final String caption;
    final int preferredWidth;

    TimeAndSalesTableColumn(String caption, int preferredWidth) {
        this.caption = caption;
        this.preferredWidth = preferredWidth;
    }

    @Override
    public String getCaption() {
        return caption;
    }

    @Override
    public int getPreferredWidth() {
        return preferredWidth;
    }
}
