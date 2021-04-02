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
package com.devexperts.qd.tools;

import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.qtp.QDEndpoint;

import java.io.IOException;

public class OptionFile extends OptionString {
    private String spec = ""; // empty spec by default (CompositeFilters.valueOf throws NPE).

    public OptionFile() {
        super('f', "file", "[<filter>@]<name>", "File and filter to read storage on startup.");
    }

    public FeedFileHandler initFile(QDEndpoint endpoint, boolean storeEverything) {
        if (!isSet())
            return null;
        QDFilter filter = CompositeFilters.valueOf(spec, endpoint.getScheme());
        QDTicker ticker = endpoint.getTicker();
        if (ticker != null) {
            ticker.setStoreEverything(storeEverything);
            ticker.setStoreEverythingFilter(filter);
        }
        QDHistory history = endpoint.getHistory();
        if (history != null) {
            history.setStoreEverything(storeEverything);
            history.setStoreEverythingFilter(filter);
        }

        FeedFileHandler handler = new FeedFileHandler(endpoint, getValue(), filter);
        try {
            handler.readFile();
        } catch (IOException e) {
            throw new BadToolParametersException("Couldn't initialize '--file' option." , e);
        }
        return handler;
    }

    @Override
    public int parse(int i, String[] args) throws OptionParseException {
        i = super.parse(i, args);
        int pos = value.indexOf('@'); // todo: take into account escaping in filter, lastIndexOf maybe?
        if (pos >= 0) {
            spec = value.substring(0, pos);
            value = value.substring(pos + 1);
        }
        return i;
    }
}
