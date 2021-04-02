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
import com.dxfeed.event.market.Quote;

/**
 * Testing implicit type inference.
 */
@EventTypeMapping(recordName = "Quote")
public class BetterQuote extends Quote {
    private char betterChar;
    private byte betterByte;
    private short betterShort;
    private int betterInt;
    private long betterLong;
    private double betterDouble;
    private float betterFloat;
    private String betterString;
    private boolean betterBool;

    public BetterQuote() {
    }

    public BetterQuote(String eventSymbol) {
        super(eventSymbol);
    }

    // should not be processed
    public int getDummy() {
        return 0;
    }

    // should not be processed
    public void setDummy2() {
    }

    public char getBetterChar() {
        return betterChar;
    }

    public void setBetterChar(char betterChar) {
        this.betterChar = betterChar;
    }

    public byte getBetterByte() {
        return betterByte;
    }

    public void setBetterByte(byte betterByte) {
        this.betterByte = betterByte;
    }

    public short getBetterShort() {
        return betterShort;
    }

    public void setBetterShort(short betterShort) {
        this.betterShort = betterShort;
    }

    public int getBetterInt() {
        return betterInt;
    }

    public void setBetterInt(int betterInt) {
        this.betterInt = betterInt;
    }

    @EventFieldMapping(type = EventFieldType.LONG)
    public long getBetterLong() {
        return betterLong;
    }

    public void setBetterLong(long betterLong) {
        this.betterLong = betterLong;
    }

    public double getBetterDouble() {
        return betterDouble;
    }

    public void setBetterDouble(double betterDouble) {
        this.betterDouble = betterDouble;
    }

    public float getBetterFloat() {
        return betterFloat;
    }

    public void setBetterFloat(float betterFloat) {
        this.betterFloat = betterFloat;
    }

    public String getBetterString() {
        return betterString;
    }

    public void setBetterString(String betterString) {
        this.betterString = betterString;
    }

    public boolean isBetterBool() {
        return betterBool;
    }

    public void setBetterBool(boolean betterBool) {
        this.betterBool = betterBool;
    }
}
