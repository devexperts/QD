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

/**
 * Name(s) for endpoints.
 */
public class OptionName extends OptionString {
    private static final char[] badChars = {'=', '.'};

    private String[] names;

    public OptionName(String defaultValue) {
        super('n', "name" + suffix(defaultValue), crateArgumentName(defaultValue), createDescription(defaultValue));
        this.names = defaultValue.split("/");
    }

    private static String suffix(String defaultValue) {
        return defaultValue.indexOf('/') >= 0 ? "s" : "";
    }

    private static String crateArgumentName(String defaultValue) {
        int n = defaultValue.split("/").length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0)
                sb.append('/');
            sb.append("name");
            if (n > 1)
                sb.append(i + 1);
        }
        return sb.toString();
    }

    private static String createDescription(String defaultValue) {
        String s = suffix(defaultValue);
        return "Define name" + s + " for QD endpoint" + s + " ('" + defaultValue + "' by default).";
    }

    @Override
    public int parse(int pos, String[] args) throws OptionParseException {
        pos = super.parse(pos, args);
        for (char c : badChars) {
            if (value.indexOf(c) != -1) {
                throw new OptionParseException(this + " option must not contain characters " + listBadChars());
            }
        }
        String[] parsed = value.split("/");
        if (parsed.length != names.length) {
            if (names.length == 1)
                throw new OptionParseException(this + " option must contain one name");
            else
                throw new OptionParseException(this + " option must contain " + names.length + " names, separated by '/'");
        }
        for (int i = 0; i < parsed.length; i++)
            for (int j = i + 1; j < parsed.length; j++)
                if (names[i].equals(names[j]))
                    throw new OptionParseException(this + " option must contain different names");
        names = parsed;
        return pos;
    }

    public String getName() {
        return names[0];
    }

    public String getName(int index) {
        return names[index];
    }

    private static String listBadChars() {
        StringBuilder sb = new StringBuilder();
        sb.append('\'').append(badChars[0]).append('\'');
        if (badChars.length == 1) {
            return sb.toString();
        }
        for (int i = 1; i < badChars.length - 1; i++) {
            sb.append(", '").append(badChars[i]).append('\'');
        }
        sb.append(" and '").append(badChars[badChars.length - 1]).append('\'');
        return sb.toString();
    }

}
