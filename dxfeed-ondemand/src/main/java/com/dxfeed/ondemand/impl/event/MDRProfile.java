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
package com.dxfeed.ondemand.impl.event;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.dxfeed.event.market.impl.ProfileMapping;

import java.io.IOException;

public class MDRProfile extends MDREvent {
    private int beta;
    private int eps;
    private int divFreq;
    private int exDivAmount;
    private int exDivDate;
    private int _52HighPrice;
    private int _52LowPrice;
    private long shares;
    private int isIndex; // TODO: remove isIndex field
    private String description;

    private void setDescription(String s) {
        if (!(s == null ? description == null : s.equals(description)))
            description = s;
    }

    @Override
    public void init(long startTime) {
        eventTime = startTime / 1000 * 1000;
        beta = 0;
        eps = 0;
        divFreq = 0;
        exDivAmount = 0;
        exDivDate = 0;
        _52HighPrice = 0;
        _52LowPrice = 0;
        shares = 0;
        isIndex = 0;
        description = null;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRProfile event = (MDRProfile) newEvent;
        return 0 == (
            beta - event.beta |
            eps - event.eps |
            divFreq - event.divFreq |
            exDivAmount - event.exDivAmount |
            exDivDate - event.exDivDate |
            _52HighPrice - event._52HighPrice |
            _52LowPrice - event._52LowPrice |
            shares - event.shares |
            isIndex - event.isIndex) &&
            (description == null ? event.description == null : description.equals(event.description));
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        return true;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        ProfileMapping mapping = cursor.getRecord().getMapping(ProfileMapping.class);
        mapping.setBetaDecimal(cursor, beta);
        mapping.setEpsDecimal(cursor, eps);
        mapping.setDivFreq(cursor, divFreq);
        mapping.setExdDivAmountDecimal(cursor, exDivAmount);
        mapping.setExdDivDate(cursor, exDivDate);
        mapping.set52HighPriceDecimal(cursor, _52HighPrice);
        mapping.set52LowPriceDecimal(cursor, _52LowPrice);
        mapping.setShares(cursor, shares);
        mapping.setDescription(cursor, description);
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        ProfileMapping mapping = cursor.getRecord().getMapping(ProfileMapping.class);
        beta = mapping.getBetaDecimal(cursor);
        eps = mapping.getEpsDecimal(cursor);
        divFreq = mapping.getDivFreq(cursor);
        exDivAmount = mapping.getExdDivAmountDecimal(cursor);
        exDivDate = mapping.getExdDivDate(cursor);
        _52HighPrice = mapping.get52HighPriceDecimal(cursor);
        _52LowPrice = mapping.get52LowPriceDecimal(cursor);
        shares = mapping.getShares(cursor);
        if (shares < 0)
            shares += 1000L << 32; // correct overflow (speculatively); to be removed eventually
        shares = shares / 1000 * 1000; // round value to the supported precision
        setDescription(mapping.getDescription(cursor));
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRProfile event = (MDRProfile) source;
        eventTime = event.eventTime;
        beta = event.beta;
        eps = event.eps;
        divFreq = event.divFreq;
        exDivAmount = event.exDivAmount;
        exDivDate = event.exDivDate;
        _52HighPrice = event._52HighPrice;
        _52LowPrice = event._52LowPrice;
        shares = event.shares;
        isIndex = event.isIndex;
        setDescription(event.description);
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        beta += in.readCompactInt();
        eps += in.readCompactInt();
        divFreq += in.readCompactInt();
        exDivAmount += in.readCompactInt();
        exDivDate += in.readCompactInt();
        _52HighPrice += in.readCompactInt();
        _52LowPrice += in.readCompactInt();
        shares += in.readCompactLong() * 1000;
        isIndex += in.readCompactInt();
        setDescription(in.readUTFString());
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRProfile event = (MDRProfile) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        out.writeCompactInt(event.beta - beta);
        out.writeCompactInt(event.eps - eps);
        out.writeCompactInt(event.divFreq - divFreq);
        out.writeCompactInt(event.exDivAmount - exDivAmount);
        out.writeCompactInt(event.exDivDate - exDivDate);
        out.writeCompactInt(event._52HighPrice - _52HighPrice);
        out.writeCompactInt(event._52LowPrice - _52LowPrice);
        out.writeCompactLong(event.shares / 1000 - shares / 1000);
        out.writeCompactInt(event.isIndex - isIndex);
        out.writeUTFString(event.description);
        setFrom(event);
    }
}
