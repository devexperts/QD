/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.viewer;

import java.util.HashSet;
import java.util.Set;

import com.dxfeed.event.market.*;

import static com.dxfeed.viewer.QuoteBoardCellSupport.State;

class QuoteBoardTableRow {
    final String symbol;

    double lastPrice = Double.NaN;
    long lastSize = Long.MIN_VALUE;
    State lastState = State.NOT_AVAILABLE;
    char lastExchange = Character.MAX_VALUE;
    long lastUpdateTime = Long.MAX_VALUE;

    double bidPrice = Double.NaN;
    long bidSize = Long.MIN_VALUE;
    State bidState = State.NOT_AVAILABLE;
    char bidExchange = Character.MAX_VALUE;
    long bidUpdateTime = Long.MAX_VALUE;

    double askPrice = Double.NaN;
    long askSize = Long.MIN_VALUE;
    State askState = State.NOT_AVAILABLE;
    char askExchange = Character.MAX_VALUE;
    long askUpdateTime = Long.MAX_VALUE;

    double highPrice = Double.NaN;
    double lowPrice = Double.NaN;
    double openPrice = Double.NaN;
    double closePrice = Double.NaN;
    int dayId = Integer.MAX_VALUE;
    PriceType dayCloseType = PriceType.PRELIMINARY;
    double prevClosePrice = Double.NaN;
    int prevDayId = Integer.MAX_VALUE;
    PriceType prevDayCloseType = PriceType.PRELIMINARY;
    long openInterest = Long.MIN_VALUE;

    boolean tradingHalted = false;
    String haltStatusReason;
    long haltStartTime = Long.MAX_VALUE;
    long haltEndTime = Long.MAX_VALUE;
    double lowLimitPrice = Double.NaN;
    double highLimitPrice = Double.NaN;

    long highUpdateTime = Long.MAX_VALUE;
    long lowUpdateTime = Long.MAX_VALUE;
    long openUpdateTime = Long.MAX_VALUE;
    long closeUpdateTime = Long.MAX_VALUE;
    long prevCloseUpdateTime = Long.MAX_VALUE;
    long openInterestTime = Long.MAX_VALUE;

    long volume = Long.MIN_VALUE;
    long volumeUpdateTime = Long.MAX_VALUE;

    String description;

    final Set<Integer> indexes = new HashSet<Integer>(1);

    QuoteBoardTableRow(String symbol) {
        this.symbol = symbol;
    }

    public void updateQuote(Quote quote, long curTime) {
        if (bidPrice != quote.getBidPrice() || bidSize != quote.getBidSize() || bidExchange != quote.getBidExchangeCode()) {
            bidUpdateTime = curTime;
            if (bidPrice != quote.getBidPrice()) {
                bidState = stateFor(quote.getBidPrice(), bidPrice);
                bidPrice = quote.getBidPrice();
            }
            bidSize = quote.getBidSize();
            if (bidSize == 0)
                bidState = State.COMMON;
            bidExchange = quote.getBidExchangeCode();
        }
        if (askPrice != quote.getAskPrice() || askSize != quote.getAskSize() || askExchange != quote.getAskExchangeCode()) {
            askUpdateTime = curTime;
            if (askPrice != quote.getAskPrice()) {
                askState = stateFor(quote.getAskPrice(), askPrice);
                askPrice = quote.getAskPrice();
            }
            askSize = quote.getAskSize();
            if (askSize == 0)
                askState = State.COMMON;
            askExchange = quote.getAskExchangeCode();
        }
    }

    public void updateTrade(Trade trade, long curTime) {
        if (lastPrice != trade.getPrice() || lastSize != trade.getSize() || lastExchange != trade.getExchangeCode()) {
            lastUpdateTime = curTime;
            if (lastPrice != trade.getPrice()) {
                lastState = stateFor(trade.getPrice(), lastPrice);
                lastPrice = trade.getPrice();
            }
            lastSize = trade.getSize();
            lastExchange = trade.getExchangeCode();
        }
        if (volume != trade.getDayVolume()) {
            volumeUpdateTime = curTime;
            volume = trade.getDayVolume();
        }
    }

    public void updateSummary(Summary summary, long curTime) {
        if (highPrice != summary.getDayHighPrice()) {
            highUpdateTime = curTime;
            highPrice = summary.getDayHighPrice();
        }
        if (lowPrice != summary.getDayLowPrice()) {
            lowUpdateTime = curTime;
            lowPrice = summary.getDayLowPrice();
        }
        if (openPrice != summary.getDayOpenPrice()) {
            openUpdateTime = curTime;
            openPrice = summary.getDayOpenPrice();
        }
        if (closePrice != summary.getDayClosePrice()) {
            closeUpdateTime = curTime;
            closePrice = summary.getDayClosePrice();
        }
        if (dayCloseType != summary.getDayClosePriceType()) {
            closeUpdateTime = curTime;
            closePrice = summary.getDayClosePrice();
        }
        if (dayId != summary.getDayId()) {
            closeUpdateTime = curTime;
            dayId = summary.getDayId();
        }
        if (prevClosePrice != summary.getPrevDayClosePrice()) {
            prevCloseUpdateTime = curTime;
            prevClosePrice = summary.getPrevDayClosePrice();
        }
        if (prevDayCloseType != summary.getPrevDayClosePriceType()) {
            prevCloseUpdateTime = curTime;
            prevDayCloseType = summary.getPrevDayClosePriceType();
        }
        if (prevDayId != summary.getPrevDayId()) {
            prevCloseUpdateTime = curTime;
            prevDayId = summary.getPrevDayId();
        }
        if (openInterest != summary.getOpenInterest()) {
            openInterestTime = curTime;
            openInterest = summary.getOpenInterest();
        }
    }

    public void updateProfile(Profile profile) {
        description = profile.getDescription();
        tradingHalted = profile.isTradingHalted();
        haltStatusReason = profile.getStatusReason();
        haltStartTime = profile.getHaltStartTime();
        haltEndTime = profile.getHaltEndTime();
        lowLimitPrice = profile.getLowLimitPrice();
        highLimitPrice = profile.getHighLimitPrice();
    }

    public static QuoteBoardCellSupport.State stateFor(double newValue, double oldValue) {
        QuoteBoardCellSupport.State result = stateFor(newValue - oldValue);
        if (result == State.NOT_AVAILABLE && !Double.isNaN(newValue))
            result = State.FIRST_TIME;
        return result;
    }

    public static QuoteBoardCellSupport.State stateFor(double value) {
        return Double.isNaN(value) ? State.NOT_AVAILABLE :
            value > 0 ? State.INCREASED :
            value < 0 ? State.DECREASED : State.COMMON;
    }
}
