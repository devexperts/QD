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
import com.dxfeed.event.market.impl.MarketMakerMapping;

import java.io.IOException;

public class MDRMarketMaker extends MDREvent {
    private int exchange;
    private int id;
    private int bidPrice;
    private int bidSize;
    private int askPrice;
    private int askSize;

    public boolean pendingWrite;

    public long getKey() {
        return ((long) exchange << 32) | (id & 0xFFFFFFFFL);
    }

    public void setKey(long key) {
        exchange = (int) (key >>> 32);
        id = (int) key;
    }

    @Override
    public void init(long startTime) {
        eventTime = startTime / 1000 * 1000;
        exchange = 0;
        id = 0;
        bidPrice = 0;
        bidSize = 0;
        askPrice = 0;
        askSize = 0;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRMarketMaker event = (MDRMarketMaker) newEvent;
        return 0 == (
            exchange - event.exchange |
            id - event.id |
            bidPrice - event.bidPrice |
            bidSize - event.bidSize |
            askPrice - event.askPrice |
            askSize - event.askSize);
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        MDRMarketMaker event = (MDRMarketMaker) newEvent;
        return exchange == event.exchange && id == event.id;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        MarketMakerMapping mapping = cursor.getRecord().getMapping(MarketMakerMapping.class);
        mapping.setMMExchange(cursor, (char) exchange);
        mapping.setMMID(cursor, id);
        mapping.setMMBidPriceDecimal(cursor, bidPrice);
        mapping.setMMBidSize(cursor, bidSize);
        mapping.setMMAskPriceDecimal(cursor, askPrice);
        mapping.setMMAskSize(cursor, askSize);
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        MarketMakerMapping mapping = cursor.getRecord().getMapping(MarketMakerMapping.class);
        exchange = mapping.getMMExchange(cursor);
        id = mapping.getMMID(cursor);
        bidPrice = mapping.getMMBidPriceDecimal(cursor);
        bidSize = mapping.getMMBidSize(cursor);
        askPrice = mapping.getMMAskPriceDecimal(cursor);
        askSize = mapping.getMMAskSize(cursor);
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRMarketMaker event = (MDRMarketMaker) source;
        eventTime = event.eventTime;
        exchange = event.exchange;
        id = event.id;
        bidPrice = event.bidPrice;
        bidSize = event.bidSize;
        askPrice = event.askPrice;
        askSize = event.askSize;
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        int flag = in.readUnsignedByte();
        exchange += readDeltaFlagged(in, flag, 0x80);
        id += readDeltaFlagged(in, flag, 0x40);
        bidPrice += readDeltaFlagged(in, flag, 0x20);
        bidSize += readDeltaFlagged(in, flag, 0x10);
        askPrice += readDeltaFlagged(in, flag, 0x08);
        askSize += readDeltaFlagged(in, flag, 0x04);
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRMarketMaker event = (MDRMarketMaker) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        int flagPosition = out.getPosition();
        out.writeByte(0);
        int flag =
            writeDeltaFlagged(out, exchange, event.exchange, 0x80) |
            writeDeltaFlagged(out, id, event.id, 0x40) |
            writeDeltaFlagged(out, bidPrice, event.bidPrice, 0x20) |
            writeDeltaFlagged(out, bidSize, event.bidSize, 0x10) |
            writeDeltaFlagged(out, askPrice, event.askPrice, 0x08) |
            writeDeltaFlagged(out, askSize, event.askSize, 0x04);
        out.getBuffer()[flagPosition] = (byte) flag;
        setFrom(event);
    }

    @Override
    public Object getExtractorConflationKey(String symbol) {
        return new ConflationKey(symbol, getKey());
    }

    private static class ConflationKey {
        private final String symbol;
        private final long key;

        private ConflationKey(String symbol, long key) {
            this.symbol = symbol;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ConflationKey))
                return false;
            ConflationKey that = (ConflationKey) o;
            return key == that.key && symbol.equals(that.symbol);
        }

        @Override
        public int hashCode() {
            return 31 * symbol.hashCode() + (int) (key ^ (key >>> 32));
        }
    }
}
