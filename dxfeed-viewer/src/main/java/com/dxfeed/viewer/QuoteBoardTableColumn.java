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

import java.util.TimeZone;
import javax.swing.SwingConstants;

import static com.dxfeed.viewer.QuoteBoardCellSupport.State;
import static com.dxfeed.viewer.QuoteBoardCellSupport.boolValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.dayIdValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.exchangeValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.priceValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.sizeValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.textValue;
import static com.dxfeed.viewer.QuoteBoardCellSupport.timeValue;

@SuppressWarnings({"UnusedDeclaration"})
enum QuoteBoardTableColumn {
    SYMBOL("Symbol", 70) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            double netChange = quoteTableRow.lastPrice - quoteTableRow.prevClosePrice;
            State state = QuoteBoardTableRow.stateFor(netChange);
            if (state == State.NOT_AVAILABLE &&
                (!Double.isNaN(quoteTableRow.lastPrice) ||
                !Double.isNaN(quoteTableRow.bidPrice) ||
                !Double.isNaN(quoteTableRow.askPrice)))
                state = State.COMMON;

            return textValue(quoteTableRow.symbol, state, true, SwingConstants.LEFT);
        }
    },
    DESCRIPTION("Description", 150) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            String description;
            State state;
            if (quoteTableRow.description == null) {
                description = ViewerCellValue.NA;
                state = State.NOT_AVAILABLE;
            } else {
                description = quoteTableRow.description;
                state = State.INFO;
            }
            return textValue(description, state, false, SwingConstants.LEFT);
        }
    },
    LAST("Last", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.lastPrice, quoteTableRow.lastState, quoteTableRow.lastUpdateTime);
        }
    },
    LAST_SIZE("LSize", 10) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return sizeValue(quoteTableRow.lastSize, quoteTableRow.lastUpdateTime);
        }
    },
    LAST_EXCHANGE("LX", 1) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return exchangeValue(quoteTableRow.lastExchange, quoteTableRow.lastUpdateTime);
        }
    },
    CHANGE("NetChg", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            double netChange = quoteTableRow.lastPrice - quoteTableRow.prevClosePrice;
            String netChangeStr;
            if (Double.isNaN(netChange)) {
                netChangeStr = ViewerCellValue.NA;
            } else {
                netChangeStr = ViewerCellValue.formatPrice(Math.floor(netChange * 1e10 + 0.5) / 1e10);
                if (netChange > 0)
                    netChangeStr = "+" + netChangeStr;
            }
            return textValue(netChangeStr, QuoteBoardTableRow.stateFor(netChange), Math.max(quoteTableRow.lastUpdateTime, quoteTableRow.prevCloseUpdateTime), SwingConstants.RIGHT);
        }
    },
    BID("Bid", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.bidPrice, quoteTableRow.bidState, quoteTableRow.bidUpdateTime);
        }
    },
    BID_SIZE("BSize", 10) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return sizeValue(quoteTableRow.bidSize, quoteTableRow.bidUpdateTime);
        }
    },
    BID_EXCHANGE("BX", 1) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return exchangeValue(quoteTableRow.bidExchange, quoteTableRow.bidUpdateTime);
        }
    },
    ASK("Ask", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.askPrice, quoteTableRow.askState, quoteTableRow.askUpdateTime);
        }
    },
    ASK_SIZE("ASize", 10) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return sizeValue(quoteTableRow.askSize, quoteTableRow.askUpdateTime);
        }
    },
    ASK_EXCHANGE("AX", 1) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return exchangeValue(quoteTableRow.askExchange, quoteTableRow.askUpdateTime);
        }
    },
    VOLUME("Volume", 50) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return sizeValue(quoteTableRow.volume, quoteTableRow.volumeUpdateTime);
        }
    },
    OPEN("Open", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.openPrice, State.COMMON, quoteTableRow.openUpdateTime);
        }
    },
    HIGH("High", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.highPrice, State.COMMON, quoteTableRow.highUpdateTime);
        }
    },
    LOW("Low", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.lowPrice, State.COMMON, quoteTableRow.lowUpdateTime);
        }
    },
    CLOSE("Close", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.closePrice, State.COMMON, quoteTableRow.closeUpdateTime);
        }
    },
    CLOSE_DATE("Close Day", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return dayIdValue(quoteTableRow.dayId, quoteTableRow.closeUpdateTime);
        }
    },
    PREV_CLOSE("Prev.Close", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.prevClosePrice, State.COMMON, quoteTableRow.prevCloseUpdateTime);
        }
    },
    PREV_CLOSE_DAY("Prev.Close Day", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return dayIdValue(quoteTableRow.prevDayId, quoteTableRow.closeUpdateTime);
        }
    },
    OPEN_INTEREST("Open Interest", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return sizeValue(quoteTableRow.openInterest, quoteTableRow.openInterestTime);
        }
    },
    HALTED("Halted", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return boolValue(quoteTableRow.tradingHalted, quoteTableRow.closeUpdateTime);
        }
    },
    HALT_REASON("Halt Reason", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return textValue(quoteTableRow.haltStatusReason, State.COMMON, false, SwingConstants.LEFT);
        }
    },
    HALT_START_TIME("Halt Start", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return timeValue(quoteTableRow.haltStartTime, quoteTableRow.closeUpdateTime, timeZone);
        }
    },
    HALT_END_TIME("Halt End", 30) {
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return timeValue(quoteTableRow.haltEndTime, quoteTableRow.closeUpdateTime, timeZone);
        }
    },
    LOW_LIMIT_PRICE("Low Limit Price", 30) {
        @Override
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.lowLimitPrice, State.COMMON, 0);
        }
    },
    HIGH_LIMIT_PRICE("High Limit Price", 30) {
        @Override
        public ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone) {
            return priceValue(quoteTableRow.highLimitPrice, State.COMMON, 0);
        }
    }

    ;

    final String caption;
    final int preferredWidth;

    QuoteBoardTableColumn(String caption, int preferredWidth) {
        this.caption = caption;
        this.preferredWidth = preferredWidth;
    }

    public abstract ViewerCellValue getValue(QuoteBoardTableRow quoteTableRow, TimeZone timeZone);
}
