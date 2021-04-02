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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * The convenient shortcut to a node in the {@link MARS} instance with utility methods.
 */
public class MARSNode {
    public static final String MARS_ROOT_PROPERTY = "mars.root";
    public static final String MARS_ADDRESS_PROPERTY = "mars.address";

    private final MARSEventFactory marsEventFactory = MARSEventFactory.getInstance();

    /**
     * Returns default singleton root node for this JVM. This instance is automatically
     * configured from system properties {@link #MARS_ROOT_PROPERTY} and {@link #MARS_ADDRESS_PROPERTY}.
     * It is created on first use.
     * This method is a shortcut to {@link MARSEndpoint}.{@link MARSEndpoint#getInstance() getInstance}().{@link MARSEndpoint#getRoot() getRoot}().
     */
    public static MARSNode getRoot() {
        return MARSEndpoint.getInstance().getRoot();
    }

    /**
     * Sets new default singleton root node for this JVM.
     *
     * @deprecated Use "-Dmars.root=&lt;name&gt;" in your JVM options
     */
    public static void setRoot(MARSNode root) {
        MARSEndpoint.getInstance().setRoot(root);
    }

    private static long last_unique_time;

    /**
     * Generates new unique name useful to distinguish conflicting nodes.
     */
    public static String generateUniqueName() {
        long time = System.currentTimeMillis();
        synchronized (MARSNode.class) {
            last_unique_time = time = Math.max(last_unique_time + 1, time);
        }
        return new SimpleDateFormat("yyMMdd-HHmmss:SSS").format(new Date(time));
    }

    // ========== Fields ==========

    private final MARS mars;
    private final String name;

    private String nameValueCache;

    /**
     * Creates new node for specified {@link com.devexperts.mars.common.MARS} instance and name.
     *
     * @param mars {@link com.devexperts.mars.common.MARS} instance
     * @param name node name
     */
    public MARSNode(MARS mars, String name) {
        if (mars == null)
            throw new NullPointerException("MARS is null.");
        if (name == null)
            throw new NullPointerException("Node name is null.");
        if (name.endsWith(MARSEvent.PARAM_TYPE_CATEGORY) || name.endsWith(MARSEvent.PARAM_TYPE_DESCRIPTION)
            || name.endsWith(MARSEvent.PARAM_TYPE_STATUS) || name.endsWith(MARSEvent.PARAM_TYPE_VALUE))
            throw new IllegalArgumentException("Node name ends with reserved suffix: " + name);
        this.mars = mars;
        this.name = name;
    }

    /**
     * Returns subnode for specified subname applied to this node.
     */
    public MARSNode subNode(String subname) {
        return subNode(subname, null);
    }

    /**
     * Returns subnode for specified subname applied to this node with specified description.
     */
    public MARSNode subNode(String subname, String description) {
        return subNode(subname, null, description);
    }

    /**
     * Returns subnode for specified subname applied to this node with specified category and description.
     */
    public MARSNode subNode(String subname, String category, String description) {
        if (subname == null)
            throw new NullPointerException("Node subname is null.");
        MARSNode subnode = new MARSNode(mars, name.isEmpty() ? subname : subname.isEmpty() ? name : name + "." + subname);
        subnode.setCategory(category);
        subnode.setDescription(description);
        return subnode;
    }

    /**
     * Returns {@link MARS} instance of this node.
     */
    public MARS getMars() {
        return mars;
    }

    /**
     * Returns name of this node.
     */
    public String getName() {
        return name;
    }

    private String getInternal(String suffix) {
        return mars.getValue(name + "." + suffix);
    }

    private void setInternal(String suffix, String value) {
        setInternal(suffix, value, 0);
    }

    private void setInternal(String suffix, String value, long timestamp) {
        if (value != null)
            mars.setValue(name + "." + suffix, value, timestamp);
    }

    /**
     * Returns category of this node or <tt>null</tt> if unspecified.
     */
    public String getCategory() {
        return getInternal(MARSConstants.CATEGORY);
    }

    /**
     * Sets new category of this node; does nothing if <tt>null</tt> is specified.
     */
    public void setCategory(String category) {
        setInternal(MARSConstants.CATEGORY, category);
    }

    /**
     * Returns description of this node or <tt>null</tt> if unspecified.
     */
    public String getDescription() {
        return getInternal(MARSConstants.DESCRIPTION);
    }

    /**
     * Sets new description of this node; does nothing if <tt>null</tt> is specified.
     */
    public void setDescription(String description) {
        setInternal(MARSConstants.DESCRIPTION, description);
    }

    /**
     * Returns status of this node or <tt>null</tt> if unspecified.
     */
    public String getStatus() {
        return getInternal(MARSConstants.STATUS);
    }

    /**
     * Sets new status of this node; does nothing if <tt>status == null</tt>.<br> <tt>getName()</tt> of
     * <tt>MARSStatus</tt> object is correct way to obtain required status <tt>String</tt>.
     *
     * @deprecated use {@link MARSNode#setStatus(MARSStatus)} or {@link
     *             MARSNode#setStatus(MARSStatus, String)} instead of this method.
     */
    public void setStatus(String status) {
        setInternal(MARSConstants.STATUS, status, (long) 0);
    }

    /**
     * Sets new status of this node; does nothing if <tt>null</tt> is specified.
     */
    public void setStatus(MARSStatus marsStatus) {
        setStatus(marsStatus, 0);
    }

    public void setStatus(MARSStatus marsStatus, long timestamp) {
        if (marsStatus != null) {
            setInternal(MARSConstants.STATUS, marsStatus.getName(), timestamp);
        }
    }

    /**
     * Sets new status of this node; does nothing if <tt>marsStatus == null</tt> is specified.
     */
    public void setStatus(MARSStatus marsStatus, String statusMessage) {
        setStatus(marsStatus, statusMessage, 0);
    }

    public void setStatus(MARSStatus marsStatus, String statusMessage, long timestamp) {
        if (marsStatus != null) {
            setInternal(MARSConstants.STATUS, marsStatus.getName() + " " + statusMessage, timestamp);
        }
    }

    /**
     * Returns unparsed value of this node or <tt>null</tt> if unspecified.
     */
    public String getValue() {
        return getInternal(MARSConstants.VALUE);
    }

    /**
     * Sets new unparsed value of this node; does nothing if <tt>null</tt> is specified.
     *
     * @param value new value
     */
    public void setValue(String value) {
        setValue(value, 0);
    }

    public void setValue(String value, long timestamp) {
        if (value != null) {
            if (nameValueCache == null)
                nameValueCache = name + "." + MARSConstants.VALUE;
            mars.setValue(nameValueCache, value, timestamp);
        }
    }

    /**
     * Returns integer value of this node or <tt>0</tt> if unspecified or unparseable.
     */
    public int getIntValue() {
        String value = getValue();
        if (value != null)
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
            }
        return 0;
    }

    /**
     * Sets new integer value of this node.
     */
    public void setIntValue(int value) {
        setIntValue(value, 0);
    }

    public void setIntValue(int value, long timestamp) {
        setValue(value == 0 ? "0" : Integer.toString(value), timestamp);
    }

    /**
     * Returns double value of this node or {@link Double#NaN} if unspecified or unparseable.
     */
    public double getDoubleValue() {
        String value = getValue();
        if (value != null)
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
            }
        return Double.NaN;
    }

    /**
     * Sets new double value of this node.
     */
    public void setDoubleValue(double value, long timestamp) {
        setValue(value == 0 ? "0" :
            value == (int) value ? Integer.toString((int) value) :
                value == (long) value ? Long.toString((long) value) :
                    Double.toString(value),
            timestamp);
    }

    public void setDoubleValue(double value) {
        setDoubleValue(value, 0);
    }

    private DateFormat time_formatter;

    private DateFormat getTimeFormatter(TimeZone time_zone) {
        if (time_formatter == null)
            time_formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
        time_formatter.setTimeZone(time_zone);
        return time_formatter;
    }

    /**
     * Returns time value of this node or 0 if unspecified or unparseable.
     * Uses system default time zone if value has none.
     */
    public long getTimeValue() {
        return getTimeValue(TimeZone.getDefault());
    }

    /**
     * Returns time value of this node or <tt>0</tt> if unspecified or unparseable.
     *
     * @param time_zone default time zone to be used if value has none.
     */
    public synchronized long getTimeValue(TimeZone time_zone) {
        // NOTE: method is synchronized because date formatters are not thread-safe in general.
        String value = getValue();
        if (value != null)
            try {
                return getTimeFormatter(time_zone).parse(value).getTime();
            } catch (ParseException e) {
            }
        return 0;
    }

    /**
     * Sets new time value of this node using system default time zone.
     */
    public void setTimeValue(long value, long timestamp) {
        setTimeValue(value, TimeZone.getDefault(), timestamp);
    }

    public void setTimeValue(long value) {
        setTimeValue(value, TimeZone.getDefault(), 0);
    }

    /**
     * Sets new time value of this node using specified time zone.<br>
     * <br>
     * <u>NOTE:</u> method is <tt>synchronized</tt> because date formatters are not thread-safe in general.
     */
    public synchronized void setTimeValue(long value, TimeZone timeZone, long timestamp) {
        setValue(getTimeFormatter(timeZone).format(new Date(value)), timestamp);
    }

    public synchronized void setTimeValue(long value, TimeZone timeZone) {
        setTimeValue(value, timeZone, 0);
    }

    public void remove() {
        mars.putEvent(marsEventFactory
            .createMARSEvent(getName() + MARSEvent.PARAM_TYPE_STATUS, MARSStatus.REMOVED.getName()));
    }

    public String toString() {
        return name;
    }
}
