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
import com.dxfeed.event.candle.impl.TradeHistoryMapping;
import com.dxfeed.event.market.impl.TimeAndSaleMapping;

import java.io.IOException;

public class MDRTradeHistory extends MDREvent {
    private int time;
    private int sequence;
    private int exchange;
    private int price;
    private int size;
    private int bid;
    private int ask;

    @Override
    public void init(long startTime) {
        long seconds = startTime/ 1000;
        eventTime = seconds * 1000;
        time = (int) seconds;
        sequence = 0;
        exchange = 0;
        price = 0;
        size = 0;
        bid = 0;
        ask = 0;
    }

    public char getExchange() {
        return (char) exchange;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRTradeHistory event = (MDRTradeHistory) newEvent;
        return 0 == (
            time - event.time |
            sequence - event.sequence |
            exchange - event.exchange |
            price - event.price |
            size - event.size |
            bid - event.bid |
            ask - event.ask);
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        MDRTradeHistory event = (MDRTradeHistory) newEvent;
        return time == event.time && sequence == event.sequence;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        if (cursor.getRecord().getMapping(TradeHistoryMapping.class) != null) {
            getIntoTradeHistory(cursor, cursor.getRecord().getMapping(TradeHistoryMapping.class));
        } else {
            getIntoTimeAndSale(cursor, cursor.getRecord().getMapping(TimeAndSaleMapping.class));
        }
    }

    private void getIntoTradeHistory(RecordCursor cursor, TradeHistoryMapping mapping) {
        mapping.setTimeSeconds(cursor, time);
        mapping.setSequence(cursor, sequence);
        mapping.setExchange(cursor, (char) exchange);
        mapping.setPriceDecimal(cursor, price);
        mapping.setSize(cursor, size);
        mapping.setBidDecimal(cursor, bid);
        mapping.setAskDecimal(cursor, ask);
    }

    private void getIntoTimeAndSale(RecordCursor cursor, TimeAndSaleMapping mapping) {
        mapping.setTimeSeconds(cursor, time);
        mapping.setSequence(cursor, sequence);
        mapping.setExchange(cursor, (char) exchange);
        mapping.setPriceDecimal(cursor, price);
        mapping.setSize(cursor, size);
        mapping.setBidPriceDecimal(cursor, bid);
        mapping.setAskPriceDecimal(cursor, ask);
        mapping.setFlags(cursor, 4); // valid tick
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        DataRecord record = cursor.getRecord();
        if (record.getMapping(TradeHistoryMapping.class) != null) {
            setFromTradeHistory(cursor, cursor.getRecord().getMapping(TradeHistoryMapping.class));
        } else {
            setFromTimeAndSale(cursor, cursor.getRecord().getMapping(TimeAndSaleMapping.class));
        }
    }

    private void setFromTradeHistory(RecordCursor cursor, TradeHistoryMapping mapping) {
        time = mapping.getTimeSeconds(cursor);
        sequence = mapping.getSequence(cursor);
        exchange = mapping.getExchange(cursor);
        price = mapping.getPriceDecimal(cursor);
        size = mapping.getSize(cursor);
        bid = mapping.getBidDecimal(cursor);
        ask = mapping.getAskDecimal(cursor);
    }

    private void setFromTimeAndSale(RecordCursor cursor, TimeAndSaleMapping mapping) {
        time = mapping.getTimeSeconds(cursor);
        sequence = mapping.getSequence(cursor);
        exchange = mapping.getExchange(cursor);
        price = mapping.getPriceDecimal(cursor);
        size = mapping.getSize(cursor);
        bid = mapping.getBidPriceDecimal(cursor);
        ask = mapping.getAskPriceDecimal(cursor);
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRTradeHistory event = (MDRTradeHistory) source;
        eventTime = event.eventTime;
        time = event.time;
        sequence = event.sequence;
        exchange = event.exchange;
        price = event.price;
        size = event.size;
        bid = event.bid;
        ask = event.ask;
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        int flag = in.readUnsignedByte();
        time += readDeltaFlagged(in, flag, 0x80);
        sequence += readDeltaFlagged(in, flag, 0x40);
        exchange += readDeltaFlagged(in, flag, 0x20);
        price += readDeltaFlagged(in, flag, 0x10);
        size += readDeltaFlagged(in, flag, 0x08);
        bid += readDeltaFlagged(in, flag, 0x04);
        ask += readDeltaFlagged(in, flag, 0x02);
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRTradeHistory event = (MDRTradeHistory) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        int flagPosition = out.getPosition();
        out.writeByte(0);
        int flag =
            writeDeltaFlagged(out, time, event.time, 0x80) |
            writeDeltaFlagged(out, sequence, event.sequence, 0x40) |
            writeDeltaFlagged(out, exchange, event.exchange, 0x20) |
            writeDeltaFlagged(out, price, event.price, 0x10) |
            writeDeltaFlagged(out, size, event.size, 0x08) |
            writeDeltaFlagged(out, bid, event.bid, 0x04) |
            writeDeltaFlagged(out, ask, event.ask, 0x02);
        out.getBuffer()[flagPosition] = (byte) flag;
        setFrom(event);
    }
}
