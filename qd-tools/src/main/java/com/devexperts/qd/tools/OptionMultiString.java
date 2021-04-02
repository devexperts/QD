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

import java.util.ArrayList;
import java.util.List;

public class OptionMultiString extends Option {
    private final String argument_name;
    private final List<String> values = new ArrayList<String>();

    public OptionMultiString(char short_name, String full_name, String argument_name, String description) {
        super(short_name, full_name, description);
        this.argument_name = argument_name;
    }

    public List<String> getValues() {
        return values;
    }

    public boolean isSet() {
        return !values.isEmpty();
    }

    public int parse(int i, String[] args) throws OptionParseException {
        if (i >= args.length - 1)
            throw new OptionParseException(this + " must be followed by a string argument.");
        values.add(args[++i]);
        return i;
    }

    public String toString() {
        return super.toString() + " " + argument_name;
    }
}
