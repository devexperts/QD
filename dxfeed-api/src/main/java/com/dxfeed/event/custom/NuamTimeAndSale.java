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
package com.dxfeed.event.custom;

import com.devexperts.annotation.Experimental;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents an extension of {@link TimeAndSale} for the symbols traded on the Nuam Exchange.
 * It is the new Regional Holding that integrates the Santiago, Lima, and Colombia Stock Exchanges into a single market.
 *
 * <p>It includes the Nuam Exchange internal data, obtained directly from the matching engine such as different IDs.
 *
 * <h3>Properties</h3>
 * <p>
 * {@code NuamTimeAndSale} event has the following properties:
 *
 * <ul>
 * <li>{@link #getMatchId() matchId} - the unique identifier of the match associated with this transaction;</li>
 * <li>{@link #getTradeId() tradeId} - the public unique identifier of the trade
 *     associated with this transaction only within the trading day;</li>
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 * <p>
 * Some nuam time and sale sources provide a consistent view of the set of known nuam time and sales
 * for a given time range when used with {@link DXFeedTimeSeriesSubscription}.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for nuam time and sales, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>Regular subscription via {@link DXFeedSubscription} produces a stream of nuam time and
 * sale events as they happen and their {@link #getEventFlags() eventFlags} are always zero.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing Nuam Time and Sales</h3>
 * <p>
 * Publishing of nuam time and sales events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <h3>Implementation details</h3>
 * <p>
 * This event is implemented on top of QDS record {@code NuamTimeAndSale}.
 */
@Experimental
@XmlRootElement(name = "NuamTimeAndSale")
@XmlType(propOrder = {
    "matchId", "tradeId"
})
public class NuamTimeAndSale extends TimeAndSale {
    private static final long serialVersionUID = 0;

    private long matchId;
    private long tradeId;

    /**
     * Creates new nuam time and sale event with default values.
     */
    public NuamTimeAndSale() {}

    /**
     * Creates new nuam time and sale event with the specified event symbol.
     *
     * @param eventSymbol event symbol.
     */
    public NuamTimeAndSale(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Gets the match ID, which uniquely identifies the match between the buyer
     * and the seller associated with this transaction.
     * This information is available only to participants of the deal and broker.
     *
     * @return the match ID.
     */
    public long getMatchId() {
        return matchId;
    }

    /**
     * Changes the match ID, which uniquely identifies the match between the buyer
     * and the seller associated with this transaction.
     * This information is available only to participants of the deal and broker.
     *
     * @param matchId the match ID to set.
     */
    public void setMatchId(long matchId) {
        this.matchId = matchId;
    }

    /**
     * Gets the trade ID, which uniquely identifies the trade
     * associated with this transaction only within the trading day.
     * Unlike matchId, it can be treated as "public" information and used in client widgets and internal Nuam systems.
     *
     * @return the trade ID.
     */
    public long getTradeId() {
        return tradeId;
    }

    /**
     * Changes the trade ID, which uniquely identifies the trade
     * associated with this transaction only within the trading day.
     * Unlike matchId, it can be treated as "public" information and used in client widgets and internal Nuam systems.
     *
     * @param tradeId the trade ID to set.
     */
    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", matchId=").append(matchId)
            .append(", tradeId=").append(tradeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getFlags() {
        return super.getFlags();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setFlags(int flags) {
        super.setFlags(flags);
    }
}
