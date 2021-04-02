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
package com.devexperts.qd.qtp;

import com.devexperts.qd.QDContract;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Description of a single channel.
 * The format is: {@code [<filter>'&']<contract>['@'<aggregationPeriod>]['['<key>'='<value>','...']']}.
 * Filter is assumed to include anything by default, and default aggregation period is set
 * with {@code aggregationPeriod}.
 */
public class ChannelDescription {
    final String filterStr;
    final QDContract contract;
    final TimePeriod aggregationPeriod;
    final List<String> properties = new ArrayList<>();
    final String channel;

    public ChannelDescription(String channel) {
        this.channel = channel;
        channel = QDConfig.parseProperties(channel, properties);
        String[] timeSplit = QDConfig.splitParenthesisedStringAt(channel, '@');
        String filterContract = timeSplit.length == 1 ? channel : timeSplit[0];
        aggregationPeriod = timeSplit.length == 1 ? null : TimePeriod.valueOf(timeSplit[1]);
        int i = filterContract.lastIndexOf('&');
        filterStr = i < 0 ? null : filterContract.substring(0, i);
        String contractStr = i < 0 ? filterContract : filterContract.substring(i + 1);
        try {
            contract = QDContract.valueOf(contractStr.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            throw new InvalidFormatException("Invalid contract name: " + contractStr, e);
        }
    }

    @Override
    public String toString() {
        return channel;
    }
}
