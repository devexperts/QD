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

import com.devexperts.util.TimeFormat;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlTimeAdapter extends XmlAdapter<String,Long> {
    @Override
    public Long unmarshal(String v) throws Exception {
        return TimeFormat.GMT.parse(v).getTime();
    }

    @Override
    public String marshal(Long v) throws Exception {
        return TimeFormat.GMT.asFullIso().format(v);
    }
}
