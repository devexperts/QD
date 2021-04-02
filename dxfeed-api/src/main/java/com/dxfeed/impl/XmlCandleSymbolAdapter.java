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

import com.dxfeed.event.candle.CandleSymbol;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlCandleSymbolAdapter extends XmlAdapter<String, CandleSymbol> {
    @Override
    public CandleSymbol unmarshal(String v) throws Exception {
        return CandleSymbol.valueOf(v);
    }

    @Override
    public String marshal(CandleSymbol v) throws Exception {
        return v.toString();
    }
}
