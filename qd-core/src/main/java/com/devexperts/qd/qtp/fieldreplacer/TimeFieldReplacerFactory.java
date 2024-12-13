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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * This factory creates an implementation of {@link FieldReplacer} that provides an ability to replace
 * {@link SerialFieldType#TIME_SECONDS time} and {@link SerialFieldType#TIME_MILLIS milisseconds time} fields
 * according to the specified strategy.
 *
 * <h3>Specification</h3>
 * Specification of this {@link FieldReplacer field replacer} should be in the following format:
 * {@code "time:<recordFilter>:<configuration>"}, where {@code recordFilter} is a {@link RecordOnlyFilter}
 * and {@code configuration} is a configuration of replacing strategy.
 *
 * <p>For all {@link RecordCursor record cursors} which are accepted by {@code recordFilter}
 * all fields with the serial type {@link SerialFieldType#TIME_SECONDS TIME_SECONDS} or
 * {@link SerialFieldType#TIME_MILLIS TIME_MILLIS} (or created from them by {@link SerialFieldType#withName(String)}
 * method) are replaced with the new time according to the specified strategy.
 *
 * <h3>Replacing strategies</h3>
 * TimeFieldReplacer supports several strategies:
 *
 * <ul>
 *     <li> <b>current time</b>: replaces time with current. See {@link System#currentTimeMillis()}.
 *          Configuration format: {@code "current"};</li>
 *     <li> <b>increase/decrease time</b>: increases or decreases time on specified delta.
 *          Configuration format: {@code "+<time>"} or {@code "-<time>"};</li>
 *     <li> <b>specified time</b>: replaces time with specified.
 *          Configuration format: {@code "<time>"}.</li>
 * </ul>
 *
 * <p>All times should be in format according to {@link TimePeriod#valueOf(String)},
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
 */
@ServiceProvider
public class TimeFieldReplacerFactory implements FieldReplacer.Factory {

    private static final String PREFIX = "time" + FieldReplacer.DELIMITER;

    private enum StrategyType {
        CURRENT, INCREASE, DECREASE, SPECIFIED
    }

    @Override
    public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme) {
        if (!fieldReplacerSpec.startsWith(PREFIX))
            return null;

        String[] fieldReplacerSpecParts = fieldReplacerSpec.split(FieldReplacer.DELIMITER);
        if (fieldReplacerSpecParts.length != 3) {
            throw new IllegalArgumentException("TimeFieldReplacer specification should be in " +
                PREFIX + "<recordFilter>:<configuration> format: " + fieldReplacerSpec);
        }

        // Parse record filter
        RecordOnlyFilter recordFilter = RecordOnlyFilter.valueOf(fieldReplacerSpecParts[1], dataScheme);
        // Parse configuration
        String configuration = fieldReplacerSpecParts[2];
        StrategyType strategyType;
        if (configuration.equalsIgnoreCase("current")) {
            strategyType = StrategyType.CURRENT;
        } else if (configuration.startsWith("+")) {
            strategyType = StrategyType.INCREASE;
        } else if (configuration.startsWith("-")) {
            strategyType = StrategyType.DECREASE;
        } else {
            strategyType = StrategyType.SPECIFIED;
        }
        if (strategyType == StrategyType.INCREASE || strategyType == StrategyType.DECREASE)
            configuration = configuration.substring(1);
        int timeSeconds = 0;
        if (strategyType != StrategyType.CURRENT) {
            TimePeriod timeConfiguration = TimePeriod.valueOf(configuration);
            if (timeConfiguration.getSeconds() * 1_000L != timeConfiguration.getTime()) {
                throw new IllegalArgumentException("Time should be presented with seconds precision: " + configuration);
            }
            timeSeconds = timeConfiguration.getSeconds();
        }

        // Create all consumers
        @SuppressWarnings("unchecked")
        Consumer<RecordCursor>[] consumers = new Consumer[dataScheme.getRecordCount()];
        boolean targetFound = false;
        for (int rid = 0; rid < dataScheme.getRecordCount(); rid++) {
            DataRecord record = dataScheme.getRecord(rid);
            if (!recordFilter.acceptRecord(record)) {
                consumers[rid] = null;
                continue;
            }

            List<Consumer<RecordCursor>> ridConsumers = new ArrayList<>();
            for (int fid = 0; fid < record.getIntFieldCount(); fid++) {
                DataIntField field = record.getIntField(fid);
                if (field.getSerialType().getId() == SerialFieldType.TIME_SECONDS.getId() ||
                    field.getSerialType().getId() == SerialFieldType.TIME_MILLIS.getId())
                {
                    targetFound = true;
                    ridConsumers.add(createConsumer(field, strategyType, timeSeconds));
                }
            }
            consumers[rid] = FieldReplacersCache.createComposite(ridConsumers);
        }
        if (!targetFound)
            throw new IllegalArgumentException("Cannot find any target fields: " + fieldReplacerSpec);

        return new DefaultFieldReplacer(dataScheme, consumers);
    }

    private Consumer<RecordCursor> createConsumer(DataIntField field, StrategyType strategyType, int timeSeconds) {
        int fid = field.getIndex();
        if (field.getSerialType().getId() == SerialFieldType.TIME_SECONDS.getId()) {
            switch (strategyType) {
                case CURRENT:
                    return c -> c.setInt(fid, (int) Math.floorDiv(System.currentTimeMillis(), 1_000L));
                case INCREASE:
                    return c -> c.setInt(fid, c.getInt(fid) + timeSeconds);
                case DECREASE:
                    return c -> c.setInt(fid, c.getInt(fid) - timeSeconds);
                case SPECIFIED:
                    return c -> c.setInt(fid, timeSeconds);
                default:
                    throw new IllegalArgumentException("Unknown time replacement strategy " + strategyType);
            }
        } else {
            switch (strategyType) {
                case CURRENT:
                    return c -> c.setLong(fid, System.currentTimeMillis());
                case INCREASE:
                    return c -> c.setLong(fid, c.getLong(fid) + timeSeconds * 1_000L);
                case DECREASE:
                    return c -> c.setLong(fid, c.getLong(fid) - timeSeconds * 1_000L);
                case SPECIFIED:
                    return c -> c.setLong(fid, timeSeconds * 1_000L);
                default:
                    throw new IllegalArgumentException("Unknown time replacement strategy " + strategyType);
            }
        }
    }
}
