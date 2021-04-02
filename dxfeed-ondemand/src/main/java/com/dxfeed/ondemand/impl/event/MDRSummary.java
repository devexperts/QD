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
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.dxfeed.event.market.impl.FundamentalMapping;
import com.dxfeed.event.market.impl.SummaryMapping;

import java.io.IOException;

public class MDRSummary extends MDREvent {
    private int highPrice;
    private int lowPrice;
    private int openPrice;
    private int closePrice; // this is a prevDayClosePrice actually
    private int openInterest;

    @Override
    public void init(long startTime) {
        eventTime = startTime / 1000 * 1000;
        highPrice = 0;
        lowPrice = 0;
        openPrice = 0;
        closePrice = 0;
        openInterest = 0;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRSummary event = (MDRSummary) newEvent;
        return 0 == (
            highPrice - event.highPrice |
            lowPrice - event.lowPrice |
            openPrice - event.openPrice |
            closePrice - event.closePrice |
            openInterest - event.openInterest);
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        return true;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        DataRecord record = cursor.getRecord();
        if (record.getMapping(FundamentalMapping.class) != null) {
            getIntoFundamental(cursor, record.getMapping(FundamentalMapping.class));
        } else {
            getIntoSummary(cursor, record.getMapping(SummaryMapping.class));
        }
    }

    private void getIntoFundamental(RecordCursor cursor, FundamentalMapping mapping) {
        mapping.setHighPriceDecimal(cursor, highPrice);
        mapping.setLowPriceDecimal(cursor, lowPrice);
        mapping.setOpenPriceDecimal(cursor, openPrice);
        mapping.setClosePriceDecimal(cursor, closePrice);
        mapping.setOpenInterest(cursor, openInterest);
    }

    private void getIntoSummary(RecordCursor cursor, SummaryMapping mapping) {
        mapping.setDayHighPriceDecimal(cursor, highPrice);
        mapping.setDayLowPriceDecimal(cursor, lowPrice);
        mapping.setDayOpenPriceDecimal(cursor, openPrice);
        mapping.setPrevDayClosePriceDecimal(cursor, closePrice);
        mapping.setOpenInterest(cursor, openInterest);
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        DataRecord record = cursor.getRecord();
        if (record.getMapping(FundamentalMapping.class) != null) {
            setFromFundamental(cursor, record.getMapping(FundamentalMapping.class));
        } else {
            setFromSummary(cursor, record.getMapping(SummaryMapping.class));
        }
    }

    private void setFromFundamental(RecordCursor cursor, FundamentalMapping mapping) {
        highPrice = mapping.getHighPriceDecimal(cursor);
        lowPrice = mapping.getLowPriceDecimal(cursor);
        openPrice = mapping.getOpenPriceDecimal(cursor);
        closePrice = mapping.getClosePriceDecimal(cursor);
        openInterest = mapping.getOpenInterest(cursor);
    }

    private void setFromSummary(RecordCursor cursor, SummaryMapping mapping) {
        highPrice = mapping.getDayHighPriceDecimal(cursor);
        lowPrice = mapping.getDayLowPriceDecimal(cursor);
        openPrice = mapping.getDayOpenPriceDecimal(cursor);
        closePrice = mapping.getPrevDayClosePriceDecimal(cursor);
        openInterest = mapping.getOpenInterest(cursor);
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRSummary event = (MDRSummary) source;
        eventTime = event.eventTime;
        highPrice = event.highPrice;
        lowPrice = event.lowPrice;
        openPrice = event.openPrice;
        closePrice = event.closePrice;
        openInterest = event.openInterest;
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        int flag = in.readUnsignedByte();
        highPrice += readDeltaFlagged(in, flag, 0x80);
        lowPrice += readDeltaFlagged(in, flag, 0x40);
        openPrice += readDeltaFlagged(in, flag, 0x20);
        closePrice += readDeltaFlagged(in, flag, 0x10);
        openInterest += readDeltaFlagged(in, flag, 0x08);
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRSummary event = (MDRSummary) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        int flagPosition = out.getPosition();
        out.writeByte(0);
        int flag =
            writeDeltaFlagged(out, highPrice, event.highPrice, 0x80) |
            writeDeltaFlagged(out, lowPrice, event.lowPrice, 0x40) |
            writeDeltaFlagged(out, openPrice, event.openPrice, 0x20) |
            writeDeltaFlagged(out, closePrice, event.closePrice, 0x10) |
            writeDeltaFlagged(out, openInterest, event.openInterest, 0x08);
        out.getBuffer()[flagPosition] = (byte) flag;
        setFrom(event);
    }
}
