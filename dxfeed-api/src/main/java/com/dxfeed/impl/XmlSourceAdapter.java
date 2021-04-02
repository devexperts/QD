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
package com.dxfeed.impl;

import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.OrderSource;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlSourceAdapter extends XmlAdapter<String, IndexedEventSource> {
    @Override
    public IndexedEventSource unmarshal(String v) throws Exception {
        return OrderSource.valueOf(v);
    }

    @Override
    public String marshal(IndexedEventSource v) throws Exception {
        return v.toString();
    }
}
