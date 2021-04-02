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

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataScheme;
import com.devexperts.util.InvalidFormatException;

import java.util.StringTokenizer;

public class OptionFields extends OptionString {
    private String[] suffixes;

    public OptionFields() {
        super('f', "fields", "<fields>", "Process only fields that end with a string from a given comma-separated list.");
    }

    @Override
    public int parse(int i, String[] args) throws OptionParseException {
        i = super.parse(i, args);
        if (isSet()) {
            StringTokenizer st = new StringTokenizer(getValue(), ",");
            int n = st.countTokens();
            suffixes = new String[n];
            for (int j = 0; j < n; j++) {
                suffixes[j] = st.nextToken();
            }
        }
        return i;
    }

    public RecordFields[] createRecordFields(DataScheme scheme, boolean force) throws InvalidFormatException {
        if (!force && !isSet())
            return null;
        int n = scheme.getRecordCount();
        RecordFields[] rfs = new RecordFields[n];
        boolean empty = true;
        for (int i = 0; i < n; i++) {
            rfs[i] = new RecordFields(scheme.getRecord(i), this);
            if (!rfs[i].isEmpty())
                empty = false;
        }
        if (empty)
            throw new InvalidFormatException(this + " option value does not match any fields");
        return rfs;
    }

    public boolean matches(DataField f) {
        return matches(f.getName()) || matches(f.getPropertyName());
    }

    private boolean matches(String name) {
        if (suffixes == null)
            return true;
        for (int i = 0; i < suffixes.length; i++) {
            if (name.endsWith(suffixes[i]))
                return true;
        }
        return false;
    }
}
