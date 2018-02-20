/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.fieldreplacer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.TimePeriod;

/**
 * This implementation of {@link FieldReplacer} provides an ability to replace {@link SerialFieldType#TIME time} fields
 * according to specified strategy.
 *
 * <h3>Specification</h3>
 * Specification of this {@link FieldReplacer field replacer} should be in the following format:
 * {@code "time:<record_filter>:<configuration>"}, where {@code record_filter} is a {@link RecordOnlyFilter}
 * and {@code configuration} is a configuration of replacing strategy.
 *
 * <p>For all {@link RecordCursor record cursors} which are accepted by {@code record_filter}
 * all fields with serial type {@link SerialFieldType#TIME TIME}
 * (or created from it by {@link SerialFieldType#withName(String)} method)
 * are replaced with new time according to specified strategy.
 *
 * <p><b>NOTE: TimeFieldReplacer does not change millis and nano parts of time, it works with seconds precision only!</b>
 *
 * <h3>Replacing strategies</h3>
 * TimeFieldReplacer supports several strategies:
 *
 * <ul>
 *     <li> <b>current time</b>: replaces time with current. See {@link System#currentTimeMillis()}.
 *          Configuration format: {@code "current"};</li>
 *     <li> <b>increase/decrese time</b>: increases or decreases time on specified delta.
 *          Configuration format: {@code "+<time>"} or {@code "-<time>"};</li>
 *     <li> <b>specified time</b>: replaces time with specified.
 *          Configuration format: {@code "<time>"}.</li>
 * </ul>
 *
 * All times should be in format according to {@link TimePeriod#valueOf(String)},
 * but with no more than seconds precision.
 *
 * <h3>Sample usage</h3>
 * The following code reads data from {@code example.qds} and changes the time
 * of two hours and three minutes ago in all records.
 * <pre><tt>
 *     DXEndpoint endpoint = ...
 *     endpoint.connect("example.qds[fieldReplacer=time:*:-2h3m");
 * </tt></pre>
 *
 * @see FieldReplacer
 * @see FieldReplacerUtil
 */
public class TimeFieldReplacer implements FieldReplacer {

    private final RecordOnlyFilter recordFilter;
    private final StrategyType strategyType;
    private final int timeSeconds;

    private TimeFieldReplacer(RecordOnlyFilter recordFilter, StrategyType strategyType, int timeSeconds) {
        this.recordFilter = recordFilter;
        this.strategyType = strategyType;
        this.timeSeconds = timeSeconds;
    }

    @Override
    public Consumer<RecordCursor> createFieldReplacer(DataRecord dataRecord) {
        if (!recordFilter.acceptRecord(dataRecord))
            return null;
        // Create list of indexes with SerialFieldType.TIME serial type
        List<Integer> timeIndexesList = new ArrayList<>();
        for (int i = 0; i < dataRecord.getIntFieldCount(); i++) {
            DataIntField field = dataRecord.getIntField(i);
            if (field.getSerialType().getId() == SerialFieldType.TIME.getId())
                timeIndexesList.add(i);
        }
        // Do not create consumer if no time fields found
        if (timeIndexesList.isEmpty())
            return null;
        // Copy indexes to array
        final int[] timeIndexes = new int[timeIndexesList.size()];
        for (int i = 0; i < timeIndexesList.size(); i++) {
            timeIndexes[i] = timeIndexesList.get(i);
        }
        // Create consumer
        return cur -> {
            for (int index : timeIndexes) {
                cur.setInt(index, updateTime(cur.getInt(index)));
            }
        };
    }

    private int updateTime(int currentTimeSeconds) {
        switch (strategyType) {
        case CURRENT:
            return (int) Math.floorDiv(System.currentTimeMillis(), 1_000);
        case INCREASE:
            return currentTimeSeconds + timeSeconds;
        case DECREASE:
            return currentTimeSeconds - timeSeconds;
        case SPECIFIED:
            return timeSeconds;
        default:
            throw new IllegalStateException("Unknown time updating strategy: " + strategyType);
        }
    }

    private enum StrategyType {
        CURRENT, INCREASE, DECREASE, SPECIFIED
    }

    @ServiceProvider
    public static class Factory implements FieldReplacer.Factory {

        private static final String DELIMETER = ":";
        private static final String TIME_PREFIX = "time" + DELIMETER;

        @Override
        public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme) {
            if (!fieldReplacerSpec.startsWith(TIME_PREFIX))
                return null;
            String[] fieldReplacerSpecParts = fieldReplacerSpec.split(DELIMETER);
            if (fieldReplacerSpecParts.length != 3) {
                throw new IllegalArgumentException("TimeFieldReplacer specification should be in " +
                    TIME_PREFIX + ":<record_filter>:<configuration> format: " + fieldReplacerSpec);
            }
            // Parse record filter
            RecordOnlyFilter recordFilter = RecordOnlyFilter.valueOf(fieldReplacerSpecParts[1], dataScheme);
            // Parse configuration
            String configuration = fieldReplacerSpecParts[2];
            StrategyType strategyType;
            if (configuration.toLowerCase().equals("current"))
                strategyType = StrategyType.CURRENT;
            else if (configuration.startsWith("+"))
                strategyType = StrategyType.INCREASE;
            else if (configuration.startsWith("-"))
                strategyType = StrategyType.DECREASE;
            else
                strategyType = StrategyType.SPECIFIED;
            if (strategyType == StrategyType.INCREASE || strategyType == StrategyType.DECREASE)
                configuration = configuration.substring(1);
            int timeSeconds = 0;
            if (strategyType != StrategyType.CURRENT) {
                TimePeriod timeConfiguration = TimePeriod.valueOf(configuration);
                if (timeConfiguration.getSeconds() * 1_000 != timeConfiguration.getTime())
                    throw new IllegalArgumentException("Time should be presented with seconds precision: " + configuration);
                timeSeconds = timeConfiguration.getSeconds();
            }
            // Create TimeFieldReplacer
            return new TimeFieldReplacer(recordFilter, strategyType, timeSeconds);
        }
    }
}
