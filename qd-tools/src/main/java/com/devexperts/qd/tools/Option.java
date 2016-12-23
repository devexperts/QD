/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

public class Option {
    private final char short_name;
    private final String full_name;
    private final String description;
    private boolean is_set;
    private String deprecated;

    public Option(char short_name, String full_name, String description) {
        this.short_name = short_name;
        if (short_name == 'D')
            throw new IllegalArgumentException("-D is reserved for JVM system options");
        this.full_name = full_name;
        this.description = description;
    }

    public char getShortName() {
        return short_name;
    }

    public String getFullName() {
        return full_name;
    }

    public String getDescription() {
        return description;
    }

    public String getDeprecated() {
        return deprecated;
    }

    protected void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public boolean isSet() {
        return is_set;
    }

    public int parse(int i, String[] args) throws OptionParseException {
        if (is_set)
            throw new OptionParseException(this + " option is already set");
        is_set = true;
        return i;
    }

    public String toString() {
        return "-" + short_name + "|--" + full_name;
    }

    public void init() {
        // to be overriden as needed
    }
}
