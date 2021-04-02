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

public class OptionEnum extends OptionString {
    protected final String default_value;

    public OptionEnum(char short_name, String full_name, String description, String default_value, String... values) {
        super(short_name, full_name, "<s>", makeDescription(description, default_value, values));
        this.default_value = default_value;
    }

    public String getValueOrDefault() {
        return isSet() ? getValue() : default_value;
    }

    protected static String makeDescription(String description, String default_value, String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(", ");
            if (i == values.length - 1)
                sb.append(" or ");
            sb.append("'");
            sb.append(values[i]);
            sb.append("'");
            if (values[i].equals(default_value)) {
                sb.append(" (default)");
            }
        }
        return description.replace("{values}", sb.toString());
    }
}
