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

import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimePeriod;

public class OptionTimePeriod extends Option {
    private final String argument_name;

    private TimePeriod value = TimePeriod.valueOf(0);

    OptionTimePeriod(char short_name, String full_name, String argument_name, String description) {
        super(short_name, full_name, description);
        this.argument_name = argument_name;
    }

    public TimePeriod getValue() {
        return value;
    }

    public void setValue(TimePeriod value) {
        this.value = value;
    }

    @Override
    public int parse(int i, String[] args) throws OptionParseException {
        if (i >= args.length - 1)
            throw new OptionParseException(this + " must be followed by a numeric argument");
        String s = args[++i];
        try {
            value = TimePeriod.valueOf(s);
        } catch (InvalidFormatException e) {
            throw new OptionParseException(this + " must be followed by a numeric argument, invalid number: " + s);
        }
        return super.parse(i, args);
    }

    public String toString() {
        return super.toString() + " " + argument_name;
    }
}
