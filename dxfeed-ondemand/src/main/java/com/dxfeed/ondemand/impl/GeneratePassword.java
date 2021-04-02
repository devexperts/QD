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
package com.dxfeed.ondemand.impl;

import com.devexperts.util.TimePeriod;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates on-demand replay service password for a known secret string.
 */
public class GeneratePassword {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("usage: GeneratePassword <user> <contract> <secret> <timeout>");
            return;
        }
        String user = args[0];
        String contract = args[1];
        String secret = args[2];
        String timeout = args[3];

        Map<String,String> configuration = new HashMap<String, String>();
        configuration.put(MarketDataToken.TOKEN_CONTRACT, contract);
        configuration.put(MarketDataToken.TOKEN_SECRET, secret);
        configuration.put(MarketDataToken.TOKEN_TIMEOUT, "" + TimePeriod.valueOf(timeout).getTime());
        MarketDataToken token = new MarketDataToken(configuration, user);
        System.out.println("[user=" + user + ",password=" + token.toTokenPassword() + "]");
    }
}
