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
package com.devexperts.qd.qtp.fieldreplacer;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeUtil;
import com.devexperts.util.Timing;

import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * This factory creates an implementation of {@link FieldReplacer} that provides an ability to replace
 * {@link SerialFieldType#DATE date} fields according to the specified strategy.
 *
 * <h3>Specification</h3>
 * Specification of this {@link FieldReplacer field replacer} should be in the following format:
 * {@code "date:<recordFilter>:<target>:<configuration>[:<timezone>]"}, where {@code recordFilter} is a
 * {@link RecordOnlyFilter}, {@code target} is a name of the date field, {@code configuration}
 * is a configuration of the replacing strategy, and {@code timezone} is a {@link TimeZone time zone}.
 *
 * <p>For all {@link RecordCursor record cursors} which are accepted by {@code recordFilter}
 * all fields with serial type {@link SerialFieldType#DATE} (or created by {@link SerialFieldType#withName(String)}
 * method) are replaced with the new date according to the specified strategy.
 *
 * <h3>Replacing strategies</h3>
 * DateFieldReplacer supports several strategies:
 *
 * <ul>
 *     <li> <b>current date</b>: replaces date with the current one. Configuration format: {@code "current"};</li>
 *     <li> <b>previous date</b>: replaces date with the yesterday date. Configuration format: {@code "previous"};</li>
 *     <li> <b>increase/decrease day</b>: increases or decreases date with the specified delta.
 *          Configuration format: {@code "+<number>"} or {@code "-<number>"};</li>
 *     <li> <b>specified date</b>: replaces date with the specified one.
 *          Configuration format: {@code "0"} (N/A date) or {@code "yyyyMMdd"}.</li>
 * </ul>
 *
 * <p>Current and previous date strategies allow to specify {@code timezone} to calculate day boundaries.
 *
 * <h3>Sample usage</h3>
 * The following code reads data from {@code example.qds} and shifts the date to three days ago in "DayId"
 * fields in all records.
 * <pre><tt>
 *     DXEndpoint endpoint = ...
 *     endpoint.connect("example.qds[fieldReplacer=date:*:DayId:-3");
 * </tt></pre>
 *
 * @see FieldReplacer
 */
//TODO Move DateFieldReplacerFactory to dxfeed-impl and add support for Schedule
@ServiceProvider
public class DateFieldReplacerFactory implements FieldReplacer.Factory {

    private static final String PREFIX = "date" + FieldReplacer.DELIMITER;

    private enum StrategyType {
        CURRENT, PREVIOUS, INCREASE, DECREASE, SPECIFIED
    }

    @Override
    public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme) {
        if (!fieldReplacerSpec.startsWith(PREFIX))
            return null;

        String[] fieldReplacerSpecParts = fieldReplacerSpec.split(FieldReplacer.DELIMITER, 5);
        if (fieldReplacerSpecParts.length != 4 && fieldReplacerSpecParts.length != 5) {
            throw new IllegalArgumentException("DateFieldReplacer specification should be in " +
                PREFIX + "<recordFilter>:<target>:<strategy>[:<timezone>] format: " + fieldReplacerSpec);
        }

        // Parse record filter
        RecordOnlyFilter recordFilter = RecordOnlyFilter.valueOf(fieldReplacerSpecParts[1], dataScheme);
        // Parse target
        String targetName = fieldReplacerSpecParts[2];
        // Parse strategy
        String strategy = fieldReplacerSpecParts[3];
        StrategyType strategyType;
        if (strategy.equalsIgnoreCase("current")) {
            strategyType = StrategyType.CURRENT;
        } else if (strategy.equalsIgnoreCase("previous")) {
            strategyType = StrategyType.PREVIOUS;
        } else if (strategy.startsWith("+")) {
            strategyType = StrategyType.INCREASE;
        } else if (strategy.startsWith("-")) {
            strategyType = StrategyType.DECREASE;
        } else {
            strategyType = StrategyType.SPECIFIED;
        }
        if (strategyType == StrategyType.INCREASE || strategyType == StrategyType.DECREASE)
            strategy = strategy.substring(1);

        Timing timing = fieldReplacerSpecParts.length > 4 ?
            new Timing(TimeUtil.getTimeZone(fieldReplacerSpecParts[4])) : Timing.LOCAL;

        int days = 0;
        try {
            if (strategyType == StrategyType.INCREASE || strategyType == StrategyType.DECREASE) {
                days = Integer.parseInt(strategy);
            } else if (strategyType == StrategyType.SPECIFIED) {
                int ymd = Integer.parseInt(strategy);
                days = (ymd == 0) ? 0 : DayUtil.getDayIdByYearMonthDay(ymd);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec +
                ": cannot parse value '" + strategy + "'", e);
        }

        // Create all consumers
        @SuppressWarnings("unchecked")
        Consumer<RecordCursor>[] consumers = new Consumer[dataScheme.getRecordCount()];
        boolean targetFound = false;
        for (int rid = 0; rid < dataScheme.getRecordCount(); rid++) {
            DataRecord dataRecord = dataScheme.getRecord(rid);
            if (!recordFilter.acceptRecord(dataRecord)) {
                consumers[rid] = null;
                continue;
            }

            DataField field = dataRecord.findFieldByName(targetName);
            if (field == null)
                continue;

            if (field.getSerialType().getId() != SerialFieldType.DATE.getId()) {
                throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ":" +
                    " record " + dataRecord.getName() +
                    " target field " + targetName + " is not a DATE field");
            }

            consumers[rid] = createOneFieldConsumer(field, strategyType, timing, days);
            targetFound = true;
        }
        if (!targetFound)
            throw new IllegalArgumentException("Cannot find any target fields: " + fieldReplacerSpec);

        return new DefaultFieldReplacer(dataScheme, consumers);
    }

    private Consumer<RecordCursor> createOneFieldConsumer(DataField field, StrategyType strategyType,
        Timing timing, int days)
    {
        int fid = field.getIndex();
        switch (strategyType) {
            case CURRENT:
                return new CurrentDateReplacer(fid, 0, timing);
            case PREVIOUS:
                return new CurrentDateReplacer(fid, -1, timing);
            case INCREASE:
                return c -> c.setInt(fid, c.getInt(fid) + days);
            case DECREASE:
                return c -> c.setInt(fid, c.getInt(fid) - days);
            case SPECIFIED:
                return c -> c.setInt(fid, days);
            default:
                throw new IllegalArgumentException("Unknown date replacement strategy " + strategyType);
        }
    }

    private static class CurrentDateReplacer implements Consumer<RecordCursor> {

        private final int fid;
        private final int delta;
        private final Timing timing;

        // Current day field can be concurrently modified.
        // Ignore data race here because it will be eventually consistent - no synchronization is required.
        private Timing.Day currentDay;

        public CurrentDateReplacer(int fid, int delta, Timing timing) {
            this.fid = fid;
            this.delta = delta;
            this.timing = timing;
            this.currentDay = getCurrentDay(System.currentTimeMillis());
        }

        protected Timing.Day getCurrentDay(long now) {
            return timing.getByTime(now);
        }

        @Override
        public void accept(RecordCursor cursor) {
            long now = System.currentTimeMillis();
            Timing.Day day = this.currentDay; // atomic read
            if (now < day.day_start || now > day.day_end) {
                day = getCurrentDay(now);
                this.currentDay = day; // atomic write, ignore data race here
            }
            cursor.setInt(fid, day.day_id + delta);
        }
    }
}
