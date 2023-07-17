/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ondemand.impl;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;

import java.io.IOException;
import java.util.Comparator;

public class Key {
    public static final Comparator<Key> COMPARATOR = new Comparator<Key>() {
        public int compare(Key key1, Key key2) {
            int i = key1.getSymbol().compareTo(key2.getSymbol());
            if (i == 0)
                i = key1.getExchange() - key2.getExchange();
            if (i == 0)
                i = key1.getType() - key2.getType();
            return i;
        }
    };

    protected String symbol;
    protected char exchange;
    protected char type;

    // Single key is used for different QD records (e.g. TimeAndSale&X, TradeHistory)
    // This counter is used to correctly handle add/remove subscription
    public transient int subscriptionCount;

    public Key() {}

    public Key(String symbol, char exchange, char type) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.type = type;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public char getExchange() {
        return exchange;
    }

    public void setExchange(char exchange) {
        this.exchange = exchange;
    }

    public char getType() {
        return type;
    }

    public void setType(char type) {
        this.type = type;
    }

    public void readKey(ByteArrayInput in) throws IOException {
        symbol = in.readUTFString();
        exchange = (char) in.readUTFChar();
        type = (char) in.readUTFChar();
    }

    public void writeKey(ByteArrayOutput out) throws IOException {
        out.writeUTFString(symbol);
        out.writeUTFChar(exchange);
        out.writeUTFChar(type);
    }

    public int hashCode() {
        return (symbol.hashCode() * 31 + exchange) * 31 + type;
    }

    public boolean equals(Object obj) {
        Key key = (Key) obj;
        return symbol.equals(key.symbol) && exchange == key.exchange && type == key.type;
    }

    public String toString() {
        return symbol + ':' + (exchange == 0 ? '-' : exchange) + (type == 0 ? '-' : type);
    }
}
