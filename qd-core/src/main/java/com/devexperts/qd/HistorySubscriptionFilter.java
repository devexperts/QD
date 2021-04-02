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
package com.devexperts.qd;

/**
 * Filter for history subscription to avoid memory overload with history.
 * It can control on what minimum time subscription can be made and what maximal
 * number of records can be kept. It is invoked on any incoming subscription
 * through {@link QDAgent} and outgoing subscription through {@link QDDistributor}
 * and the corresponding subscription is trimmed, so that it does not exceed minimum.
 * This trimming is performed only initially (on subscription).
 * After successful subscription it stays forever. Record count is enforced
 * only any incoming data. If record count exceeds limit, then earlier data
 * (with earliest time) is discarded.
 */
public interface HistorySubscriptionFilter {
    public long getMinHistoryTime(DataRecord record, int cipher, String symbol);
    public int getMaxRecordCount(DataRecord record, int cipher, String symbol);
}
