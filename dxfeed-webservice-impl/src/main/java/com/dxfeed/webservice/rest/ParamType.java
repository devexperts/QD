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
package com.dxfeed.webservice.rest;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

public enum ParamType {
    STRING("Any string value."),
    TIME("Date and time in full ISO8601 format \"YYYY-MM-DD'T'HH:MM:SS[.SSS]Z\", or a shorter versions of it like \"YYYYMMDD-HHMMSSZ\"."),
    PERIOD("Time interval in full ISO801 format \"'PT'XXX('S'|'M'|'H')\" or a short version of it without prefix, like \"3s\"."),
    LIST("A list of strings. Multiple values can be specified by using repeated parameters or by giving a comma-separated list " +
        "of values to a parameter using a name with an additional \"s\" at the end. " +
        "A standard x-www-form-urlencoded approach using a name with an additional \"s[]\" at the end is also supported. " +
        "For example, to specify multiple events, use multiple \"event\" parameters, " +
        "or use \"events\" parameter with a comma-separated list, " +
        "or use multiple \"events[]\" parameters.");

    public final String description;

    ParamType(String description) {
        this.description = description;
    }

    public static ParamType forClass(Class<?> c) {
        if (c == String.class)
            return STRING;
        if (c == Date.class)
            return TIME;
        if (c == TimePeriod.class)
            return PERIOD;
        if (c == List.class)
            return LIST;
        throw new IllegalArgumentException(c.toString());
    }

    public Object getValue(String name, HttpServletRequest req) {
        String value = req.getParameter(name);
        switch (this) {
        case STRING:
            return value;
        case TIME:
            return value == null || value.isEmpty() ? null : TimeFormat.DEFAULT.parse(value);
        case PERIOD:
            return value == null || value.isEmpty() ? null : TimePeriod.valueOf(value);
        case LIST:
            String[] valuesPlain = req.getParameterValues(name);
            String[] valuesArr = req.getParameterValues(name + "s[]");
            String commaSeparated = req.getParameter(name + "s");
            List<String> result = new ArrayList<>();
            if (valuesPlain != null)
                addNonEmpty(valuesPlain, result);
            if (valuesArr != null)
                addNonEmpty(valuesArr, result);
            if (commaSeparated != null && !commaSeparated.isEmpty())
                commaSplit(commaSeparated, result);
            return result;
        default:
            throw new AssertionError();
        }
    }

    private void addNonEmpty(String[] a, List<String> result) {
        for (String s : a) {
            if (s != null && !s.isEmpty())
                result.add(s);
        }
    }

    private void commaSplit(String s, List<String> result) {
        int level = 0;
        int prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '{':
                level++;
                break;
            case '}':
                if (level > 0)
                    level--;
                break;
            case ',':
                if (level == 0) {
                    result.add(s.substring(prev, i));
                    prev = i + 1;
                }
                break;
            }
        }
        result.add(s.substring(prev));
    }

}
