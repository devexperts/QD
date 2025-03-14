/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.market.MarketEventSymbols;

final class DxLinkSubscriptionFactory {
    private static final String SOURCE_SEPARATOR = "#";
    private static final String EXCHANGE_SEPARATOR = "&";
    private static final String DEFAULT_SOURCE = "DEFAULT";

    public DxLinkSubscription createSubscription(QDContract contract, DataRecord record, int cipher, String symbol,
        long fromTime)
    {
        if (contract != QDContract.HISTORY && fromTime != 0)
            return null; // non-history contract cannot have a non-zero from time
        char exchangeCode = MarketEventSymbols.getExchangeCode(record.getName()); // record may contain an exchange code
        boolean isRegionalRecord = exchangeCode != '\0'; // detect regional record with exchange code
        symbol = decodeSymbol(record, cipher, symbol); // decode qd symbol to the event symbol
        if (isRegionalRecord) {
            if (isWildcardSymbol(symbol))
                return null; // skip all regional records with wildcard symbol
            symbol = MarketEventSymbols.changeExchangeCode(symbol, exchangeCode); // put exchange code to the symbol
        }
        int exchangeIndex = isRegionalRecord ? record.getName().lastIndexOf(EXCHANGE_SEPARATOR) : -1;
        int sourceIndex = exchangeIndex == -1 ? record.getName().lastIndexOf(SOURCE_SEPARATOR) : -1;
        if (sourceIndex != -1 && fromTime != 0)
            return null; // skip all records that have source and non-zero from time
        String eventType = extractEventType(record, Math.max(exchangeIndex, sourceIndex));
        return (contract == QDContract.HISTORY && (fromTime != 0 || isTimeSeriesRecord(record))) ?
            DxLinkSubscription.createTimeSeriesSubscription(eventType, symbol, getEventTimeByQDTime(fromTime)) :
            DxLinkSubscription.createSubscription(eventType, symbol, extractSource(record, sourceIndex));
    }

    private String decodeSymbol(DataRecord record, int cipher, String symbol) {
        return record.getScheme().getCodec().decode(cipher, symbol);
    }

    private String extractEventType(DataRecord record, int separatorIndex) {
        return separatorIndex != -1 ? record.getName().substring(0, separatorIndex) : record.getName();
    }

    private String extractSource(DataRecord record, int sourceIndex) {
        String source = sourceIndex != -1 ? record.getName().substring(sourceIndex + 1) : null;
        return (source == null && isIndexedRecord(record)) ? DEFAULT_SOURCE : source;
    }

    private long getEventTimeByQDTime(long time) {
        return time == Long.MAX_VALUE ? Long.MAX_VALUE : TimeSequenceUtil.getTimeMillisFromTimeSequence(time);
    }

    private boolean isWildcardSymbol(String symbol) {
        return WildcardSymbol.RESERVED_PREFIX.equals(symbol);
    }

    /**
     * Determines whether the specified {@link DataRecord record} is an indexed record.
     * Indexed records are identified by the presence of a <i>time</i> coordinate and
     * the absence of a time-based field type in the first int-field of the record.
     *
     * <p>This is a heuristic-based check and is used as a workaround to distinguish
     * indexed records from time-series records.
     *
     * @param record the record to check.
     * @return {@code true} if the record is an indexed record, {@code false} otherwise.
     */
    private boolean isIndexedRecord(DataRecord record) {
        return record.hasTime() && !isTimeBasedSerialType(record.getIntField(0).getSerialType());
    }

    /**
     * Determines whether the specified {@link DataRecord record} is a time-series record.
     * Time-series records are identified by the presence of a <i>time</i> coordinate and
     * a time-based field type in the first int-field of the record.
     *
     * <p>This is a heuristic-based check and is used as a workaround to distinguish
     * time-series records from indexed records.
     *
     * @param record the record to check.
     * @return {@code true} if the record is a time-series record, {@code false} otherwise.
     */
    private boolean isTimeSeriesRecord(DataRecord record) {
        return record.hasTime() && isTimeBasedSerialType(record.getIntField(0).getSerialType());
    }

    /**
     * Determines whether the specified serial field type represents a time-based type.
     *
     * @param type the serial field type to check.
     * @return {@code true} if the type represents a time-based type, {@code false} otherwise.
     */
    private boolean isTimeBasedSerialType(SerialFieldType type) {
        return type.hasSameSerialTypeAs(SerialFieldType.TIME_SECONDS) ||
            type.hasSameSerialTypeAs(SerialFieldType.TIME_MILLIS) ||
            type.hasSameSerialTypeAs(SerialFieldType.DATE) ||
            type.hasSameSerialTypeAs(SerialFieldType.TIME_NANOS);
    }
}
