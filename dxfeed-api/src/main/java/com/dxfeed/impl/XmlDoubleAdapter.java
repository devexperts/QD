/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.impl;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlDoubleAdapter extends XmlAdapter<String, Double> {
    @Override
    public Double unmarshal(String v) {
        return Double.parseDouble(v);
    }

    @Override
    public String marshal(Double v) {
        if (v.longValue() == v) {
            return String.valueOf(v.longValue());
        } else {
            return v.toString();
        }
    }
}
