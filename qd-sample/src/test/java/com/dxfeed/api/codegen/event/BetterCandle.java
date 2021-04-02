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
package com.dxfeed.api.codegen.event;

import com.dxfeed.annotation.EventFieldMapping;
import com.dxfeed.annotation.EventFieldType;
import com.dxfeed.annotation.EventTypeMapping;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Testing explicit type inference and inheritance from {@link Candle} and {@link LastingEvent}.
 */
@EventTypeMapping(recordName = "Candle")
public class BetterCandle extends Candle {
    private int betterDate;
    private long optTime;
    private String shortString;
    private double betterDecimalDouble;
    private long betterDecimalLong;
    private float betterFloat;
    private int betterInt;
    private boolean betterBool;
    private byte betterByte;
    private short betterShort;

    public BetterCandle() {
    }

    public BetterCandle(CandleSymbol eventSymbol) {
        super(eventSymbol);
    }

    @EventFieldMapping(fieldName = "BestInt", type = EventFieldType.DATE)
    public int getBetterDate() {
        return betterDate;
    }

    public void setBetterDate(int betterDate) {
        this.betterDate = betterDate;
    }

    @EventFieldMapping(type = EventFieldType.TIME, optional = true)
    public long getOptTime() {
        return optTime;
    }

    public void setOptTime(long optTime) {
        this.optTime = optTime;
    }

    @EventFieldMapping(type = EventFieldType.SHORT_STRING)
    public String getShortString() {
        return shortString;
    }

    public void setShortString(String shortString) {
        this.shortString = shortString;
    }

    @EventFieldMapping(type = EventFieldType.DECIMAL)
    public double getBetterDecimalDouble() {
        return betterDecimalDouble;
    }

    public void setBetterDecimalDouble(double betterDecimalDouble) {
        this.betterDecimalDouble = betterDecimalDouble;
    }

    @EventFieldMapping(type = EventFieldType.DECIMAL)
    public long getBetterDecimalLong() {
        return betterDecimalLong;
    }

    public void setBetterDecimalLong(long betterDecimalLong) {
        this.betterDecimalLong = betterDecimalLong;
    }

    @EventFieldMapping(type = EventFieldType.DECIMAL)
    public float getBetterFloat() {
        return betterFloat;
    }

    public void setBetterFloat(float betterFloat) {
        this.betterFloat = betterFloat;
    }

    @EventFieldMapping(type = EventFieldType.INT)
    public int getBetterInt() {
        return betterInt;
    }

    public void setBetterInt(int betterInt) {
        this.betterInt = betterInt;
    }

    @EventFieldMapping(type = EventFieldType.DECIMAL)
    public boolean isBetterBool() {
        return betterBool;
    }

    public void setBetterBool(boolean betterBool) {
        this.betterBool = betterBool;
    }


    @EventFieldMapping(type = EventFieldType.INT)
    public byte getBetterByte() {
        return betterByte;
    }

    public void setBetterByte(byte betterByte) {
        this.betterByte = betterByte;
    }

    @EventFieldMapping(type = EventFieldType.DECIMAL)
    public short getBetterShort() {
        return betterShort;
    }

    public void setBetterShort(short betterShort) {
        this.betterShort = betterShort;
    }

    @EventFieldMapping(type = EventFieldType.TRANSIENT)
    public LocalDateTime getDate() {
        if (optTime == 0)
            return LocalDate.ofEpochDay(betterDate).atStartOfDay();
        return LocalDateTime.ofEpochSecond(optTime, 0, ZoneOffset.UTC);
    }

    public void setDate(LocalDateTime date) {
        this.betterDate = (int) date.toLocalDate().toEpochDay();
        this.optTime = date.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    // test crash on setter without getter
    public void setSmth(int smth) {
        // do nothing
    }
}
