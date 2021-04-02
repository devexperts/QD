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

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlCharAdapter extends XmlAdapter<String, Character> {
    @Override
    public Character unmarshal(String v) throws Exception {
        return v.isEmpty() ? '\0' : v.charAt(0);
    }

    @Override
    public String marshal(Character v) throws Exception {
        return v == '\0' ? "" : v.toString();
    }
}
