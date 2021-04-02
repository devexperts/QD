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
import com.dxfeed.event.market.impl.TradeMapping;

import java.io.IOException;

public class MDRTrade extends MDREvent {
    private int lastTime;
    private int lastExchange;
    private int lastPrice;
    private int lastSize;
    private int lastTick;
    private int lastChange;
    private long volume;

    @Override
    public void init(long startTime) {
        long seconds = startTime/ 1000;
        eventTime = seconds * 1000;
        lastTime = (int) seconds;
        lastExchange = 0;
        lastPrice = 0;
        lastSize = 0;
        lastTick = 0;
        lastChange = 0;
        volume = 0;
    }

    @Override
    public boolean canSkip(MDREvent newEvent) {
        MDRTrade event = (MDRTrade) newEvent;
        return 0 == (
            lastTime - event.lastTime |
            lastExchange - event.lastExchange |
            lastPrice - event.lastPrice |
            lastSize - event.lastSize |
            lastTick - event.lastTick |
            lastChange - event.lastChange |
            volume - event.volume);
    }

    @Override
    public boolean canConflate(MDREvent newEvent) {
        return true;
    }

    @Override
    public void getInto(RecordCursor cursor) {
        cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(eventTime));
        TradeMapping mapping = cursor.getRecord().getMapping(TradeMapping.class);
        mapping.setLastTimeSeconds(cursor, lastTime);
        mapping.setLastExchange(cursor, (char) lastExchange);
        mapping.setLastPriceDecimal(cursor, lastPrice);
        mapping.setLastSize(cursor, lastSize);
        mapping.setLastTick(cursor, lastTick);
        mapping.setLastChangeDecimal(cursor, lastChange);
        mapping.setVolume(cursor, volume);
    }

    @Override
    public void setFrom(RecordCursor cursor) {
        setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        TradeMapping mapping = cursor.getRecord().getMapping(TradeMapping.class);
        lastTime = mapping.getLastTimeSeconds(cursor);
        lastExchange = mapping.getLastExchange(cursor);
        lastPrice = mapping.getLastPriceDecimal(cursor);
        lastSize = mapping.getLastSize(cursor);
        lastTick = mapping.getLastTick(cursor);
        lastChange = mapping.getLastChangeDecimal(cursor);
        volume = mapping.getVolume(cursor);
        if (volume < 0)
            volume += 0x100000000L; // correct overflow (speculatively); to be removed eventually
    }

    @Override
    public void setFrom(MDREvent source) {
        MDRTrade event = (MDRTrade) source;
        eventTime = event.eventTime;
        lastTime = event.lastTime;
        lastExchange = event.lastExchange;
        lastPrice = event.lastPrice;
        lastSize = event.lastSize;
        lastTick = event.lastTick;
        lastChange = event.lastChange;
        volume = event.volume;
    }

    @Override
    public void read(ByteArrayInput in) throws IOException {
        eventTime += in.readCompactLong();
        int flag = in.readUnsignedByte();
        lastTime += readDeltaFlagged(in, flag, 0x80);
        lastExchange += readDeltaFlagged(in, flag, 0x40);
        lastPrice += readDeltaFlagged(in, flag, 0x20);
        lastSize += readDeltaFlagged(in, flag, 0x10);
        lastTick += readDeltaFlagged(in, flag, 0x08);
        lastChange += readDeltaFlagged(in, flag, 0x04);
        volume += (flag & 0x02) == 0 ? lastSize : in.readCompactLong();
    }

    @Override
    public void write(ByteArrayOutput out, MDREvent newEvent) throws IOException {
        MDRTrade event = (MDRTrade) newEvent;
        out.writeCompactLong(event.eventTime - eventTime);
        int flagPosition = out.getPosition();
        out.writeByte(0);
        int flag =
            writeDeltaFlagged(out, lastTime, event.lastTime, 0x80) |
            writeDeltaFlagged(out, lastExchange, event.lastExchange, 0x40) |
            writeDeltaFlagged(out, lastPrice, event.lastPrice, 0x20) |
            writeDeltaFlagged(out, lastSize, event.lastSize, 0x10) |
            writeDeltaFlagged(out, lastTick, event.lastTick, 0x08) |
            writeDeltaFlagged(out, lastChange, event.lastChange, 0x04);
        if (event.volume != volume + event.lastSize) {
            out.writeCompactLong(event.volume - volume);
            flag |= 0x02;
        }
        out.getBuffer()[flagPosition] = (byte) flag;
        setFrom(event);
    }
}
