/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.option.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.util.Decimal;
import com.devexperts.qd.util.MappingUtil;
import com.devexperts.util.TimeUtil;

public class GreeksMapping extends RecordMapping {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final int iTime;
    private final int iSequence;
    private final int iPrice;
    private final int iVolatility;
    private final int iDelta;
    private final int iGamma;
    private final int iTheta;
    private final int iRho;
    private final int iVega;

    public GreeksMapping(DataRecord record) {
        super(record);
        iTime = MappingUtil.findIntField(record, "Time", false);
        iSequence = MappingUtil.findIntField(record, "Sequence", false);
        iPrice = MappingUtil.findIntField(record, "Greeks.Price", true);
        iVolatility = MappingUtil.findIntField(record, "Volatility", true);
        iDelta = MappingUtil.findIntField(record, "Delta", true);
        iGamma = MappingUtil.findIntField(record, "Gamma", true);
        iTheta = MappingUtil.findIntField(record, "Theta", true);
        iRho = MappingUtil.findIntField(record, "Rho", true);
        iVega = MappingUtil.findIntField(record, "Vega", true);
        putNonDefaultPropertyName("Greeks.Price", "Price");
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

    @Deprecated
    public double getGreeksPrice(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iPrice));
    }

    @Deprecated
    public void setGreeksPrice(RecordCursor cursor, double greeksPrice) {
        setInt(cursor, iPrice, Decimal.compose(greeksPrice));
    }

    @Deprecated
    public int getGreeksPriceDecimal(RecordCursor cursor) {
        return getInt(cursor, iPrice);
    }

    @Deprecated
    public void setGreeksPriceDecimal(RecordCursor cursor, int greeksPrice) {
        setInt(cursor, iPrice, greeksPrice);
    }

    public double getPrice(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iPrice));
    }

    public void setPrice(RecordCursor cursor, double price) {
        setInt(cursor, iPrice, Decimal.compose(price));
    }

    public int getPriceDecimal(RecordCursor cursor) {
        return getInt(cursor, iPrice);
    }

    public void setPriceDecimal(RecordCursor cursor, int price) {
        setInt(cursor, iPrice, price);
    }

    public double getVolatility(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iVolatility));
    }

    public void setVolatility(RecordCursor cursor, double volatility) {
        setInt(cursor, iVolatility, Decimal.compose(volatility));
    }

    public int getVolatilityDecimal(RecordCursor cursor) {
        return getInt(cursor, iVolatility);
    }

    public void setVolatilityDecimal(RecordCursor cursor, int volatility) {
        setInt(cursor, iVolatility, volatility);
    }

    public double getDelta(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iDelta));
    }

    public void setDelta(RecordCursor cursor, double delta) {
        setInt(cursor, iDelta, Decimal.compose(delta));
    }

    public int getDeltaDecimal(RecordCursor cursor) {
        return getInt(cursor, iDelta);
    }

    public void setDeltaDecimal(RecordCursor cursor, int delta) {
        setInt(cursor, iDelta, delta);
    }

    public double getGamma(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iGamma));
    }

    public void setGamma(RecordCursor cursor, double gamma) {
        setInt(cursor, iGamma, Decimal.compose(gamma));
    }

    public int getGammaDecimal(RecordCursor cursor) {
        return getInt(cursor, iGamma);
    }

    public void setGammaDecimal(RecordCursor cursor, int gamma) {
        setInt(cursor, iGamma, gamma);
    }

    public double getTheta(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iTheta));
    }

    public void setTheta(RecordCursor cursor, double theta) {
        setInt(cursor, iTheta, Decimal.compose(theta));
    }

    public int getThetaDecimal(RecordCursor cursor) {
        return getInt(cursor, iTheta);
    }

    public void setThetaDecimal(RecordCursor cursor, int theta) {
        setInt(cursor, iTheta, theta);
    }

    public double getRho(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iRho));
    }

    public void setRho(RecordCursor cursor, double rho) {
        setInt(cursor, iRho, Decimal.compose(rho));
    }

    public int getRhoDecimal(RecordCursor cursor) {
        return getInt(cursor, iRho);
    }

    public void setRhoDecimal(RecordCursor cursor, int rho) {
        setInt(cursor, iRho, rho);
    }

    public double getVega(RecordCursor cursor) {
        return Decimal.toDouble(getInt(cursor, iVega));
    }

    public void setVega(RecordCursor cursor, double vega) {
        setInt(cursor, iVega, Decimal.compose(vega));
    }

    public int getVegaDecimal(RecordCursor cursor) {
        return getInt(cursor, iVega);
    }

    public void setVegaDecimal(RecordCursor cursor, int vega) {
        setInt(cursor, iVega, vega);
    }
// END: CODE AUTOMATICALLY GENERATED
}
