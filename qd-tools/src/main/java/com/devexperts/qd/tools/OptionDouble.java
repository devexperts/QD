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

public class OptionDouble extends Option {
    private final String argument_name;
    private final double min_value;
    private final double max_value;

    private double value;

    public OptionDouble(char short_name, String full_name, String argument_name, String description, double min_value, double max_value) {
        this(short_name, full_name, argument_name, description, min_value, max_value, 0);
    }

    public OptionDouble(char short_name, String full_name, String argument_name, String description,
        double min_value, double max_value, double value)
    {
        super(short_name, full_name, description);
        this.argument_name = argument_name;
        this.min_value = min_value;
        this.max_value = max_value;
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public int parse(int i, String[] args) throws OptionParseException {
        if (i >= args.length - 1)
            throw new OptionParseException(this + " must be followed by a numeric argument");
        String s = args[++i];
        try {
            value = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new OptionParseException(this + " must be followed by a numeric argument, invalid number: " + s);
        }
        if (value < min_value || value > max_value)
            throw new OptionParseException(this + " must be from " + min_value + " to " + max_value);
        return super.parse(i, args);
    }

    public String toString() {
        return super.toString() + " " + argument_name;
    }
}
