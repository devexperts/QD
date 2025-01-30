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
package com.dxfeed.event.market;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;
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
 *
 * {@code NuamTimeAndSale} event has the following properties:
 *
 * <ul>
 * <li>{@link #getActorId() actorId} - actor ID for the user sending the request;</li>
 * <li>{@link #getParticipantId() participantId} - the ID of the participant the actor belongs to;</li>
 * <li>{@link #getOrderId() orderId} - the ID of the order associated with this transaction;</li>
 * <li>{@link #getClientOrderId() clientOrderId} - a client-generated order ID referred to as ClOrdID in FIX;</li>
 * <li>{@link #getTradeId() tradeId} - the unique identifier of the trade associated with this transaction;</li>
 * <li>{@link #getCustomerAccount() customerAccount} - the account to use for this transaction;</li>
 * <li>{@link #getCustomerInfo() customerInfo} - is a free text field filled in by the client
 *     entering the transaction.</li>
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
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
 *
 * Publishing of nuam time and sales events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code NuamTimeAndSale}.
 */
@XmlRootElement(name = "NuamTimeAndSale")
@XmlType(propOrder = {
    "actorId", "participantId", "orderId", "clientOrderId", "tradeId", "customerAccount", "customerInfo"
})
public class NuamTimeAndSale extends TimeAndSale {
    private static final long serialVersionUID = 0;

    private int actorId;
    private int participantId;
    private long orderId;
    private String clientOrderId;
    private long tradeId;
    private String customerAccount;
    private String customerInfo;

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
     * Gets the actor ID for the user sending the request.
     *
     * @return the actor ID.
     */
    public int getActorId() {
        return actorId;
    }

    /**
     * Changes the actor ID for the user sending the request.
     *
     * @param actorId the actor ID to set.
     */
    public void setActorId(int actorId) {
        this.actorId = actorId;
    }

    /**
     * Gets the participant ID, which is the identifier of the participant the actor belongs to.
     *
     * @return the participant ID.
     */
    public int getParticipantId() {
        return participantId;
    }

    /**
     * Changes the participant ID, which is the identifier of the participant the actor belongs to.
     *
     * @param participantId the participant ID to set.
     */
    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    /**
     * Gets the order ID associated with this transaction.
     *
     * @return the order ID.
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Changes the order ID associated with this transaction.
     *
     * @param orderId the order ID to set.
     */
    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    /**
     * Gets the client-generated order ID, referred to as ClOrdID in FIX.
     *
     * @return the client order ID.
     */
    public String getClientOrderId() {
        return clientOrderId;
    }

    /**
     * Changes the client-generated order ID, referred to as ClOrdID in FIX.
     *
     * @param clientOrderId the client order ID to set.
     */
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    /**
     * Gets the trade ID, which uniquely identifies the trade associated with this transaction.
     *
     * @return the trade ID.
     */
    public long getTradeId() {
        return tradeId;
    }

    /**
     * Changes the trade ID, which uniquely identifies the trade associated with this transaction.
     *
     * @param tradeId the trade ID to set.
     */
    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }

    /**
     * Gets the customer account used for this transaction.
     *
     * @return the customer account.
     */
    public String getCustomerAccount() {
        return customerAccount;
    }

    /**
     * Changes the customer account used for this transaction.
     *
     * @param customerAccount the customer account to set.
     */
    public void setCustomerAccount(String customerAccount) {
        this.customerAccount = customerAccount;
    }

    /**
     * Gets additional information provided by the customer for this transaction.
     *
     * @return the customer information.
     */
    public String getCustomerInfo() {
        return customerInfo;
    }

    /**
     * Changes additional information provided by the customer for this transaction.
     *
     * @param customerInfo the customer information to set.
     */
    public void setCustomerInfo(String customerInfo) {
        this.customerInfo = customerInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", actorId=").append(actorId)
            .append(", participantId").append(participantId)
            .append(", orderId=").append(orderId)
            .append(", clientOrderId='").append(clientOrderId).append("'")
            .append(", tradeId=").append(tradeId)
            .append(", customerAccount='").append(customerAccount).append("'")
            .append(", customerInfo='").append(customerInfo).append("'");
    }
}
