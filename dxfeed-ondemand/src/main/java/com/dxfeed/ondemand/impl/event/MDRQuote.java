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
import com.dxfeed.event.market.impl.QuoteMapping;

import java.io.IOException;

public class MDRQuote extends MDREvent {
    private int bidTime;
    private int bidExchange;
    private int bidPrice;
    private int bidSize;
    private int askTime;
    private int askExchange;
    private int askPrice;
    private int askSize;

    @Override
    public void init(long startTime) {
        long seconds = startTime / 1000;
        eventTime = seconds * 1000;
        bidTime = (int) seconds;
        bidExchange = 0;
        bidPrice = 0;
        bidSize = 0;
        askTime = (int) seconds;
        askExchange = 0;
        askPrice = 0;
        askSize = 0;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRQuote event = (MDRQuote) newEvent;
        // Note that bidTime and askTime are not compared by this method intentionally.
        return 0 == (
            bidExchange - event.bidExchange |
            bidPrice - event.bidPrice |
            bidSize - event.bidSize |
            askExchange - event.askExchange |
            askPrice - event.askPrice |
            askSize - event.askSize);
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        return true;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        QuoteMapping mapping = cursor.getRecord().getMapping(QuoteMapping.class);
        mapping.setBidTimeSeconds(cursor, bidTime);
        mapping.setBidExchange(cursor, (char) bidExchange);
        mapping.setBidPriceDecimal(cursor, bidPrice);
        mapping.setBidSize(cursor, bidSize);
        mapping.setAskTimeSeconds(cursor, askTime);
        mapping.setAskExchange(cursor, (char) askExchange);
        mapping.setAskPriceDecimal(cursor, askPrice);
        mapping.setAskSize(cursor, askSize);
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        QuoteMapping mapping = cursor.getRecord().getMapping(QuoteMapping.class);
        bidTime = mapping.getBidTimeSeconds(cursor);
        bidExchange = mapping.getBidExchange(cursor);
        bidPrice = mapping.getBidPriceDecimal(cursor);
        bidSize = mapping.getBidSize(cursor);
        askTime = mapping.getAskTimeSeconds(cursor);
        askExchange = mapping.getAskExchange(cursor);
        askPrice = mapping.getAskPriceDecimal(cursor);
        askSize = mapping.getAskSize(cursor);
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRQuote event = (MDRQuote) source;
        eventTime = event.eventTime;
        bidTime = event.bidTime;
        bidExchange = event.bidExchange;
        bidPrice = event.bidPrice;
        bidSize = event.bidSize;
        askTime = event.askTime;
        askExchange = event.askExchange;
        askPrice = event.askPrice;
        askSize = event.askSize;
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        int flag = in.readUnsignedByte();
        bidTime += readDeltaFlagged(in, flag, 0x80);
        bidExchange += readDeltaFlagged(in, flag, 0x40);
        bidPrice += readDeltaFlagged(in, flag, 0x20);
        bidSize += readDeltaFlagged(in, flag, 0x10);
        askTime += readDeltaFlagged(in, flag, 0x08);
        askExchange += readDeltaFlagged(in, flag, 0x04);
        askPrice += readDeltaFlagged(in, flag, 0x02);
        askSize += readDeltaFlagged(in, flag, 0x01);
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRQuote event = (MDRQuote) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        int flagPosition = out.getPosition();
        out.writeByte(0);
        int flag =
            writeDeltaFlagged(out, bidTime, event.bidTime, 0x80) |
            writeDeltaFlagged(out, bidExchange, event.bidExchange, 0x40) |
            writeDeltaFlagged(out, bidPrice, event.bidPrice, 0x20) |
            writeDeltaFlagged(out, bidSize, event.bidSize, 0x10) |
            writeDeltaFlagged(out, askTime, event.askTime, 0x08) |
            writeDeltaFlagged(out, askExchange, event.askExchange, 0x04) |
            writeDeltaFlagged(out, askPrice, event.askPrice, 0x02) |
            writeDeltaFlagged(out, askSize, event.askSize, 0x01);
        out.getBuffer()[flagPosition] = (byte) flag;
        setFrom(event);
    }
}
