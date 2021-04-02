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
package com.devexperts.qd.util;

/**
 * Value class for period of time with support for ISO8601 duration format.
 * @deprecated Use {@link com.devexperts.util.TimePeriod}
 */
public class TimePeriod extends com.devexperts.util.TimePeriod {
    private static final long serialVersionUID = 0;

    /**
     * Returns <code>TimePeriod</code> with <tt>value</tt> milliseconds.
     * @param value value in milliseconds
     * @return <code>TimePeriod</code> with <tt>value</tt> milliseconds.
     * @deprecated Use {@link com.devexperts.util.TimePeriod#valueOf(long)}
     */
    public static TimePeriod valueOf(long value) {
        return new TimePeriod(value);
    }

    /**
     * Returns <code>TimePeriod</code> represented with a given string.
     *
     * Allowable format is ISO8601 duration, but there are some simplifications and modifications available:
     * <ul>
     * <li> Letters are case insensitive.
     * <li> Letters "P" and "T" can be omitted.
     * <li> Letter "S" can be also omitted. In this case last number will be supposed to be seconds.
     * <li> Number of seconds can be fractional. So it is possible to define duration accurate within milliseconds.
     * <li> Every part can be omitted. It is supposed that it's value is zero then.
     * <li> String "inf" recognized as unlimited period.
     * </ul>
     *
     * @param value string representation
     * @throws InvalidFormatException if cannot parse <tt>value</tt>
     * @return <code>TimePeriod</code> represented with a given string.
     * @deprecated Use {@link com.devexperts.util.TimePeriod#valueOf(String)}
     */
    public static TimePeriod valueOf(String value) throws InvalidFormatException {
        try {
            return new TimePeriod(parse(value));
        } catch (com.devexperts.util.InvalidFormatException e) {
            throw new InvalidFormatException(e.getMessage(), e);
        }
    }

    private TimePeriod(long value) {
        super(value);
    }
}
