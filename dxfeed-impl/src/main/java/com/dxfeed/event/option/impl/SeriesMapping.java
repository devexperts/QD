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
package com.dxfeed.event.option.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.util.Decimal;
import com.devexperts.qd.util.MappingUtil;
import com.devexperts.util.TimeUtil;

public class SeriesMapping extends RecordMapping {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final int iIndex;
    private final int iTime;
    private final int iSequence;
    private final int iExpiration;
    private final int iVolatility;
    private final int iPutCallRatio;
    private final int iForwardPrice;
    private final int iDividend;
    private final int iInterest;

    public SeriesMapping(DataRecord record) {
        super(record);
        iIndex = MappingUtil.findIntField(record, "Index", false);
        iTime = MappingUtil.findIntField(record, "Time", false);
        iSequence = MappingUtil.findIntField(record, "Sequence", false);
        iExpiration = MappingUtil.findIntField(record, "Expiration", true);
        iVolatility = findIntField("Volatility", true);
        iPutCallRatio = findIntField("PutCallRatio", true);
        iForwardPrice = findIntField("ForwardPrice", true);
        iDividend = findIntField("Dividend", false);
        iInterest = findIntField("Interest", false);
    }

    public int getIndex(RecordCursor cursor) {
        if (iIndex < 0)
            return 0;
        return getInt(cursor, iIndex);
    }

    public void setIndex(RecordCursor cursor, int index) {
        if (iIndex < 0)
            return;
        setInt(cursor, iIndex, index);
    }

    public long getTimeMillis(RecordCursor cursor) {
        if (iTime < 0)
            return 0;
        return getInt(cursor, iTime) * 1000L;
    }

    public void setTimeMillis(RecordCursor cursor, long time) {
        if (iTime < 0)
            return;
        setInt(cursor, iTime, TimeUtil.getSecondsFromTime(time));
    }

    public int getTimeSeconds(RecordCursor cursor) {
        if (iTime < 0)
            return 0;
        return getInt(cursor, iTime);
    }

    public void setTimeSeconds(RecordCursor cursor, int time) {
        if (iTime < 0)
            return;
        setInt(cursor, iTime, time);
    }

    public int getSequence(RecordCursor cursor) {
        if (iSequence < 0)
            return 0;
        return getInt(cursor, iSequence);
    }

    public void setSequence(RecordCursor cursor, int sequence) {
        if (iSequence < 0)
            return;
        setInt(cursor, iSequence, sequence);
    }

    public int getExpiration(RecordCursor cursor) {
        return getInt(cursor, iExpiration);
    }

    public void setExpiration(RecordCursor cursor, int expiration) {
        setInt(cursor, iExpiration, expiration);
    }

    public double getVolatility(RecordCursor cursor) {
        return getAsDouble(cursor, iVolatility);
    }

    public void setVolatility(RecordCursor cursor, double volatility) {
        setAsDouble(cursor, iVolatility, volatility);
    }

    public int getVolatilityDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iVolatility);
    }

    public void setVolatilityDecimal(RecordCursor cursor, int volatility) {
        setAsTinyDecimal(cursor, iVolatility, volatility);
    }

    public long getVolatilityWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iVolatility);
    }

    public void setVolatilityWideDecimal(RecordCursor cursor, long volatility) {
        setAsWideDecimal(cursor, iVolatility, volatility);
    }

    public double getPutCallRatio(RecordCursor cursor) {
        return getAsDouble(cursor, iPutCallRatio);
    }

    public void setPutCallRatio(RecordCursor cursor, double putCallRatio) {
        setAsDouble(cursor, iPutCallRatio, putCallRatio);
    }

    public int getPutCallRatioDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iPutCallRatio);
    }

    public void setPutCallRatioDecimal(RecordCursor cursor, int putCallRatio) {
        setAsTinyDecimal(cursor, iPutCallRatio, putCallRatio);
    }

    public long getPutCallRatioWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iPutCallRatio);
    }

    public void setPutCallRatioWideDecimal(RecordCursor cursor, long putCallRatio) {
        setAsWideDecimal(cursor, iPutCallRatio, putCallRatio);
    }

    public double getForwardPrice(RecordCursor cursor) {
        return getAsDouble(cursor, iForwardPrice);
    }

    public void setForwardPrice(RecordCursor cursor, double forwardPrice) {
        setAsDouble(cursor, iForwardPrice, forwardPrice);
    }

    public int getForwardPriceDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iForwardPrice);
    }

    public void setForwardPriceDecimal(RecordCursor cursor, int forwardPrice) {
        setAsTinyDecimal(cursor, iForwardPrice, forwardPrice);
    }

    public long getForwardPriceWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iForwardPrice);
    }

    public void setForwardPriceWideDecimal(RecordCursor cursor, long forwardPrice) {
        setAsWideDecimal(cursor, iForwardPrice, forwardPrice);
    }

    public double getDividend(RecordCursor cursor) {
        if (iDividend < 0)
            return Double.NaN;
        return getAsDouble(cursor, iDividend);
    }

    public void setDividend(RecordCursor cursor, double dividend) {
        if (iDividend < 0)
            return;
        setAsDouble(cursor, iDividend, dividend);
    }

    public int getDividendDecimal(RecordCursor cursor) {
        if (iDividend < 0)
            return 0;
        return getAsTinyDecimal(cursor, iDividend);
    }

    public void setDividendDecimal(RecordCursor cursor, int dividend) {
        if (iDividend < 0)
            return;
        setAsTinyDecimal(cursor, iDividend, dividend);
    }

    public long getDividendWideDecimal(RecordCursor cursor) {
        if (iDividend < 0)
            return 0;
        return getAsWideDecimal(cursor, iDividend);
    }

    public void setDividendWideDecimal(RecordCursor cursor, long dividend) {
        if (iDividend < 0)
            return;
        setAsWideDecimal(cursor, iDividend, dividend);
    }

    public double getInterest(RecordCursor cursor) {
        if (iInterest < 0)
            return Double.NaN;
        return getAsDouble(cursor, iInterest);
    }

    public void setInterest(RecordCursor cursor, double interest) {
        if (iInterest < 0)
            return;
        setAsDouble(cursor, iInterest, interest);
    }

    public int getInterestDecimal(RecordCursor cursor) {
        if (iInterest < 0)
            return 0;
        return getAsTinyDecimal(cursor, iInterest);
    }

    public void setInterestDecimal(RecordCursor cursor, int interest) {
        if (iInterest < 0)
            return;
        setAsTinyDecimal(cursor, iInterest, interest);
    }

    public long getInterestWideDecimal(RecordCursor cursor) {
        if (iInterest < 0)
            return 0;
        return getAsWideDecimal(cursor, iInterest);
    }

    public void setInterestWideDecimal(RecordCursor cursor, long interest) {
        if (iInterest < 0)
            return;
        setAsWideDecimal(cursor, iInterest, interest);
    }
// END: CODE AUTOMATICALLY GENERATED
}
