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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.util.TimeMarkUtil;
import com.devexperts.qd.util.TimeSequenceUtil;

/**
 * Storage mode of {@link RecordBuffer} and {@link RecordCursor} classes.
 */
public final class RecordMode {

    //================= private static constants =================

    private static final RecordMode[] FLAG_TO_MODE = new RecordMode[F.N];

    private static RecordMode get(int f) {
        RecordMode mode = FLAG_TO_MODE[f];
        if (mode != null)
            return mode;
        return getSync(f);
    }

    private static synchronized RecordMode getSync(int f) {
        RecordMode mode = FLAG_TO_MODE[f];
        if (mode != null)
            return mode;
        return FLAG_TO_MODE[f] = new RecordMode(f);
    }

    //================= public static constants =================

    /**
     * Store all integer and objects field values.
     */
    public static final RecordMode DATA = get(F.DATA);

    /**
     * Story only first two integer field values (time) for records
     * that have {@link DataRecord#hasTime() time} and no object field values.
     */
    public static final RecordMode HISTORY_SUBSCRIPTION = get(F.HISTORY_TIME);

    /**
     * Store no integer and no object field values.
     */
    public static final RecordMode SUBSCRIPTION = get(0);

    /**
     * Store all integer and objects field values with an additional int <b>event flags</b> for each data record.
     */
    public static final RecordMode FLAGGED_DATA = get(F.DATA | F.EVENT_FLAGS);

    /**
     * Store all integer and objects field values with an additional int <b>time mark</b> for each data record.
     */
    public static final RecordMode MARKED_DATA = get(F.DATA | F.TIME_MARK);

    /**
     * Store all integer and objects field values with an additional long <b>event time</b> for each data record.
     */
    public static final RecordMode TIMESTAMPED_DATA = get(F.DATA | F.EVENT_TIME_SEQUENCE);

    //================= public static methods =================

    /**
     * Returns record mode that is used for <b>added subscription</b> of the specified contract.
     * Note, that remove subscription always use {@link #SUBSCRIPTION SUBSCRIPTION} mode.
     * @param contract the contract.
     * @return record mode.
     */
    public static RecordMode addedSubscriptionFor(QDContract contract) {
        return contract == QDContract.HISTORY ? HISTORY_SUBSCRIPTION : SUBSCRIPTION;
    }

    //================= instance fields =================

    final int f;
    final boolean data;
    final boolean historyTime;

    final int eventFlagsOfs;
    final int timeMarkOfs;
    final int eventTimeSequenceOfs; // NOTE: TickerMatrix and HistoryBuffer rely that this == "-2" for TIMESTAMPED_DATA
    final int linkOfs;
    final int attachmentOfs;

    final int extraIntCount;
    final int extraObjCount;
    final int intBufOffset;
    final int objBufOffset;

    RecordMode(int f) {
        this.f = f;
        this.data = (f & F.DATA) == F.DATA;
        this.historyTime = (f & F.HISTORY_TIME) == F.HISTORY_TIME;

        int intOfs = 0;
        this.eventFlagsOfs = (f & F.EVENT_FLAGS) != 0 ? (intOfs -= 1) : 0;
        this.timeMarkOfs = (f & F.TIME_MARK) != 0 ? (intOfs -= 1) : 0;
        this.eventTimeSequenceOfs = (f & F.EVENT_TIME_SEQUENCE) != 0 ? (intOfs -= 2) : 0;
        this.linkOfs = (f & F.LINK) != 0 ? (intOfs -= 1) : 0;
        this.extraIntCount = -intOfs;

        int objOfs = 0;
        this.attachmentOfs = (f & F.ATTACHMENT) != 0 ? (objOfs -= 1) : 0;
        this.extraObjCount = -objOfs;

        this.intBufOffset = RecordBuffer.INT_FIELDS + extraIntCount;
        this.objBufOffset = RecordBuffer.OBJ_FIELDS + extraObjCount;
    }

    /**
     * Returns {@code true} when this mode is the same or represents a subset of information from the other mode.
     * For example, {@link #SUBSCRIPTION} is a subset of all other modes.
     */
    public boolean isSubsetOf(RecordMode mode) {
        return (f & mode.f) == f;
    }

    /**
     * Returns {@code true} when this mode stores all record's integer and object field values.
     */
    public boolean hasData() {
        return data;
    }

    /**
     * Returns {@code true} when this mode keeps additional event flags with each data record.
     * @see RecordCursor#hasEventFlags()
     * @see RecordCursor#getEventFlags()
     * @see RecordCursor#setEventFlags(int)
     */
    public boolean hasEventFlags() {
        return eventFlagsOfs != 0;
    }

    /**
     * Returns mode with an additional event flags with each data record.
     * @see RecordCursor#hasEventFlags()
     * @see RecordCursor#getEventFlags()
     * @see RecordCursor#setEventFlags(int)
     * @return mode with an additional vent flags with each data record.
     */
    public RecordMode withEventFlags() {
        return get(f | F.EVENT_FLAGS);
    }

    /**
     * Returns {@code true} when this mode keeps additional integer time mark with each data record.
     * @see RecordCursor#hasTimeMark()
     * @see RecordCursor#getTimeMark()
     * @see RecordCursor#setTimeMark(int)
     * @see TimeMarkUtil
     */
    public boolean hasTimeMark() {
        return timeMarkOfs != 0;
    }

    /**
     * Returns mode with an additional integer time mark with each data record.
     * @see RecordCursor#hasTimeMark()
     * @see RecordCursor#getTimeMark()
     * @see RecordCursor#setTimeMark(int)
     * @see TimeMarkUtil
     * @return mode with an additional integer time mark with each data record.
     */
    public RecordMode withTimeMark() {
        return get(f | F.TIME_MARK);
    }

    /**
     * Returns {@code true} when this mode keeps additional event time sequence with each data record.
     * @see RecordCursor#hasEventTimeSequence()
     * @see RecordCursor#getEventTimeSequence()
     * @see RecordCursor#setEventTimeSequence(long)
     * @see TimeSequenceUtil
     */
    public boolean hasEventTimeSequence() {
        return eventTimeSequenceOfs != 0;
    }

    /**
     * Returns mode with an additional event time sequence with each data record.
     * @see RecordCursor#hasEventTimeSequence()
     * @see RecordCursor#getEventTimeSequence()
     * @see RecordCursor#setEventTimeSequence(long)
     * @see TimeSequenceUtil
     * @return mode with an additional event time sequence with each data record.
     */
    public RecordMode withEventTimeSequence() {
        return get(f | F.EVENT_TIME_SEQUENCE);
    }

    /**
     * Returns {@code true} when this mode keeps additional link to other record with each data record.
     * @see RecordCursor#hasLink()
     * @see RecordCursor#setLinkTo(long)
     * @see RecordCursor#isUnlinked()
     * @see RecordBuffer#unlinkFrom(long)
     */
    public boolean hasLink() {
        return linkOfs != 0;
    }

    /**
     * Returns mode with additional link to other record with each data record.
     * @see RecordCursor#hasLink()
     * @see RecordCursor#setLinkTo(long)
     * @see RecordCursor#isUnlinked()
     * @see RecordBuffer#unlinkFrom(long)
     * @return mode with additional link to other record with each data record.
     */
    public RecordMode withLink() {
        return get(f | F.LINK);
    }

    /**
     * Returns {@code true} when this mode keeps additional object attachment with each data record.
     * @see RecordCursor#hasAttachment()
     * @see RecordCursor#getAttachment()
     * @see RecordCursor#setAttachment(Object)
     */
    public boolean hasAttachment() {
        return attachmentOfs != 0;
    }

    /**
     * Returns mode with additional object attachment in each data record.
     * @see RecordCursor#hasAttachment()
     * @see RecordCursor#getAttachment()
     * @see RecordCursor#setAttachment(Object)
     * @return mode with additional object attachment in each data record.
     */
    public RecordMode withAttachment() {
        return get(f | F.ATTACHMENT);
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (data)
            sb.append("DATA");
        else {
            if (historyTime)
                sb.append("HISTORY_");
            sb.append("SUBSCRIPTION");
        }
        if (eventFlagsOfs != 0)
            sb.append(",EVENT_FLAGS");
        if (timeMarkOfs != 0)
            sb.append(",MARK");
        if (eventTimeSequenceOfs != 0)
            sb.append(",TIMESTAMP");
        if (linkOfs != 0)
            sb.append(",LINK");
        if (attachmentOfs != 0)
            sb.append(",ATTACHMENT");
        return sb.toString();
    }

    //================= internal access methods =================

    int intFieldCount(DataRecord record) {
        if (data)
            return record.getIntFieldCount();
        if (historyTime)
            return record.hasTime() ? 2 : 0;
        return 0;
    }

    int objFieldCount(DataRecord record) {
        if (data)
            return record.getObjFieldCount();
        return 0;
    }

    boolean differentIntFieldCount(DataRecord oldRecord, DataRecord newRecord) {
        return intFieldCount(oldRecord) != intFieldCount(newRecord);
    }

    boolean differentObjFieldCount(DataRecord oldRecord, DataRecord newRecord) {
        return objFieldCount(oldRecord) != objFieldCount(newRecord);
    }

    //================= private static helpers =================

    private static class F {
        static final int HISTORY_TIME = 0x01;
        static final int DATA_ONLY_BIT = 0x02; // invalid by itself
        static final int DATA = DATA_ONLY_BIT | HISTORY_TIME; // data is a superset of history time
        static final int EVENT_FLAGS = 0x04;
        static final int TIME_MARK = 0x08;
        static final int EVENT_TIME_SEQUENCE = 0x10;
        static final int LINK = 0x20;
        static final int ATTACHMENT = 0x40;

        static final int N = 0x80; // max flags
    }
}
