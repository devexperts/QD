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
package com.devexperts.mars.common;

/**
 * The officially recognized statuses of MARS node.<br> <tt>Immutable</tt> (implements the design pattern).
 */
public final class MARSStatus implements Comparable {

    public static final MARSStatus OK = new MARSStatus("Ok", "OK", (byte) 4);
    public static final MARSStatus WARNING = new MARSStatus("Warning", "WARNG", (byte) 2);
    public static final MARSStatus ERROR = new MARSStatus("Error", "ERROR", (byte) 1);
    public static final MARSStatus REMOVED = new MARSStatus("Removed", "RMVED", (byte) 5);
    public static final MARSStatus UNDEFINED = new MARSStatus("<undefined>", "UNDEF", (byte) 3);

    public static final String[] STATUS_NAMES = {MARSStatus.UNDEFINED.toString(),
        MARSStatus.OK.toString(), MARSStatus.WARNING.toString(), MARSStatus.ERROR.toString()};

    private final String name;
    private final String shortName;
    private final byte order;

    private MARSStatus(String name, String shortName, byte order) {
        this.name = name;
        this.shortName = shortName;
        this.order = order;
    }

    /**
     * Returns name of this status that can be used as a prefix for corresponding value.<br> E.g.: <tt>"Ok"</tt>,
     * <tt>"Warning"</tt>, <tt>"Error"</tt>, <tt>"Removed"</tt>, and <tt>"&lt;undefined&gt;"</tt>.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the same as {@link MARSStatus#getName()}
     */
    public String toString() {
        return name;
    }

    /**
     * Returns the string representation of the {@link com.devexperts.mars.common.MARSStatus} argument.
     *
     * <p><b>Note:</b> <code>null</code> argument is considered as {@link com.devexperts.mars.common.MARSStatus#UNDEFINED}
     *
     * @param marsStatus {@link com.devexperts.mars.common.MARSStatus} to get <tt>String</tt> by
     * @return if the argument is <code>null</code>, then a string for {@link com.devexperts.mars.common.MARSStatus#UNDEFINED};
     *         otherwise, the value of {@link com.devexperts.mars.common.MARSStatus#toString() marsStatus.toString()} is
     *         returned.
     */
    public static String toString(MARSStatus marsStatus) {
        return (marsStatus == null) ? UNDEFINED.toString() : marsStatus.toString();
    }

    /**
     * Fixed-length uppercase name for the corresponding status.<br> E.g.: <tt>"OK"</tt>, <tt>"WARNG"</tt>,
     * <tt>"ERROR"</tt>, <tt>"RMVED"</tt>, and <tt>"UNDEF"</tt>.
     *
     * @return Fixed-length uppercase name for the corresponding status.
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Returns status embedded in the specified string by matching it's prefix with known statuses. Returns {@link
     * #UNDEFINED} if no matching status was found.
     */
    public static MARSStatus find(String status) {
        if (status == null)
            return UNDEFINED;
        if (status.regionMatches(true, 0, OK.name, 0, OK.name.length()))
            return OK;
        if (status.regionMatches(true, 0, WARNING.name, 0, WARNING.name.length()))
            return WARNING;
        if (status.regionMatches(true, 0, ERROR.name, 0, ERROR.name.length()))
            return ERROR;
        if (status.regionMatches(true, 0, REMOVED.name, 0, REMOVED.name.length()))
            return REMOVED;
        return UNDEFINED;
    }

    public int compareTo(Object o) {
        if (o == this) {
            return 0;
        }
        MARSStatus marsStatus = (MARSStatus) o;
        if (this.order < marsStatus.order) {
            return 1;
        } else if (this.order > marsStatus.order) {
            return -1;
        } else { // this.order == marsStatus.order
            return 0;
        }
    }
}
