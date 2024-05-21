/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

public class Option {
    private final char shortName;
    private final String fullName;
    private final String description;
    private boolean isSet;
    private String deprecated;

    public Option(char shortName, String fullName, String description) {
        if (shortName == '\0' && fullName == null)
            throw new IllegalArgumentException("Either short or full name must be specified!");
        this.shortName = shortName;
        if (shortName == 'D')
            throw new IllegalArgumentException("-D is reserved for JVM system options");
        this.fullName = fullName;
        this.description = description;
    }

    public char getShortName() {
        return shortName;
    }

    public boolean hasShortName(char name) {
        return shortName != '\0' && shortName == name;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean hasFullName(String name) {
        return fullName != null && fullName.equals(name);
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
        return isSet;
    }

    public int parse(int i, String[] args) throws OptionParseException {
        if (isSet)
            throw new OptionParseException(this + " option is already set");
        isSet = true;
        return i;
    }

    public String toString() {
        boolean hasShort = shortName != '\0';
        boolean hasFull = fullName != null;
        return (hasShort ? "-" + shortName : "  ") + "|" + (hasFull ? "--" + fullName : "");
    }

    public void init() {
        // to be overriden as needed
    }
}
