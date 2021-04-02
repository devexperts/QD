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
package com.dxfeed.ipf.transform;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;

import java.util.Date;

/**
 * A class that describes and provides access to a single Instrument Profile Field.
 */
class FieldReference {
    final String name;
    final InstrumentProfileField ipf;
    final boolean numericField;
    final Class<?> type;

    FieldReference(String name, Class<?> type) {
        this.name = name;
        this.ipf = InstrumentProfileField.find(name);
        this.numericField = ipf != null && ipf.isNumericField();
        this.type = type != null ? type : ipf != null ? ipf.getType() : String.class;
    }

    Object getValue(InstrumentProfile ip) {
        if (type == Boolean.class)
            return Compiler.parseBoolean(getStringValue(ip));
        if (type == Date.class)
            return numericField ? Compiler.getDate(ipf.getNumericField(ip)) : Compiler.parseDate(getStringValue(ip));
        if (type == Double.class)
            return numericField ? Compiler.getDouble(ipf.getNumericField(ip)) : Compiler.parseDouble(getStringValue(ip));
        if (type == String.class)
            return getStringValue(ip);
        throw new IllegalArgumentException("Unknown type");
    }

    void setValue(InstrumentProfile ip, Object value) {
        if (type == Boolean.class) {
            setStringValue(ip, Compiler.formatBoolean(Compiler.toBoolean(value)));
        } else if (type == Date.class) {
            Date date = Compiler.toDate(value);
            if (numericField)
                ipf.setNumericField(ip, Compiler.getDayId(date));
            else
                setStringValue(ip, Compiler.formatDate(date));
        } else if (type == Double.class) {
            Double num = Compiler.toDouble(value);
            if (numericField)
                ipf.setNumericField(ip, num);
            else
                setStringValue(ip, Compiler.formatDouble(num));
        } else if (type == String.class) {
            setStringValue(ip, Compiler.toString(value));
        } else
            throw new IllegalArgumentException("Unknown type");
    }

    private String getStringValue(InstrumentProfile ip) {
        return ipf != null ? ipf.getField(ip) : ip.getField(name);
    }

    private void setStringValue(InstrumentProfile ip, String value) {
        if (ipf != null)
            ipf.setField(ip, value);
        else
            ip.setField(name, value);
    }
}
