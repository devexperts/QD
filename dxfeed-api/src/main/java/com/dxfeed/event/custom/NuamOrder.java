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
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.impl.EventUtil;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderAction;
import com.dxfeed.event.market.OrderBase;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

import java.util.Collection;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents an extension of {@link Order} for the symbols traded on the Nuam Exchange.
 * It is the new Regional Holding that integrates the Santiago, Lima, and Colombia Stock Exchanges into a single market.
 * <p>
 * It includes the Nuam Exchange internal data, obtained directly from the matching engine such
 * as additional internal ids, customer additional info, extended matching options,
 * info about triggers, the structure of quantity, etc., see properties for details.
 * <p>
 *
 * <h3>Nuam Flags</h3>
 * <p>
 * Nuam Flags from Nuam exchange are mapped to the access methods of this class as follows:
 * <ul>
 *     <li>OrderType - {@link #getOrderType() orderType}</li>
 *     <li>TimeInForceType - {@link #getTimeInForce() timeInForceType}</li>
 * </ul>
 *
 * <h3>Properties</h3>
 * <p>
 * {@code NuamOrder} event has the following properties:
 *
 * <ul>
 *     <li>{@link #getEventSymbol() eventSymbol} - underlying symbol of this event;</li>
 *     <li>{@link #getSource() source} - source of this event;</li>
 *     <li>{@link #getEventFlags() eventFlags} - transactional event flags;</li>
 *     <li>{@link #getIndex() index} - unique per-symbol index of this order;</li>
 *     <li>{@link #getTime() time} - time of this order;</li>
 *     <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of this order;</li>
 *     <li>{@link #getSequence() sequence} - sequence of this order;</li>
 *     <li>{@link #getPrice() price} - price of this order;</li>
 *     <li>{@link #getSize() size} - size of this order as integer number (rounded toward zero);</li>
 *     <li>{@link #getSizeAsDouble() sizeAsDouble} - size of this order as floating number with fractions;</li>
 *     <li>{@link #getExecutedSize() executedSize} - executed size of this order;</li>
 *     <li>{@link #getCount() count} - number of individual orders in this aggregate order;</li>
 *     <li>{@link #getExchangeCode() exchangeCode} - exchange code of this order;</li>
 *     <li>{@link #getOrderSide() orderSide} - side of this order;</li>
 *     <li>{@link #getScope() scope} - scope of this order;</li>
 *     <li>{@link #getMarketMaker() marketMaker} - market maker or other aggregate identifier of this order;</li>
 *     <li>{@link #getActorId() actorId} - actor identifier for the user sending the request;</li>
 *     <li>{@link #getParticipantId() participantId} - the ID of the participant the actor belongs to;</li>
 *     <li>{@link #getSubmitterId() submitterId} - the actor submitting the message that changed the order;</li>
 *     <li>{@link #getOnBehalfOfSubmitterId() onBehalfSubmitterId} - actor submitting an order message
 *         on behalf of someone else;</li>
 *     <li>{@link #getClientOrderId() clientOrderId} - a client generated order identity;</li>
 *     <li>{@link #getCustomerAccount() customerAccount} - the account to use for this order;</li>
 *     <li>{@link #getCustomerInfo() customerInfo} - is a free text field filled in by the client
 *         entering the transaction;</li>
 *     <li>{@link #getExchangeInfo() exchangeInfo} - refers to exchange-specific information
 *         that can be included with an order. Please note that it may contain unprintable characters;</li>
 *     <li>{@link #getTimeInForce() timeInForce} - is used for refers to the duration an order remains active
 *         in the order book if it isn't fully executed, essentially defining how long the order is valid;</li>
 *     <li>{@link #getTimeInForceData() timeInForceData} - is used for the time validities
 *         that requires additional information;</li>
 *     <li>{@link #getTriggerOrderBookId() triggerOrderBookId} - the order book the order will trigger on;</li>
 *     <li>{@link #getTriggerPrice() triggerPrice} - the trigger price of the order;</li>
 *     <li>{@link #getTriggerSessionType() triggerSessionType} - the trigger session type is used to specify which
 *         session type to trigger on;</li>
 *     <li>{@link #getOrderType() orderType} - order type of the order: Limit, Market, Best-Order, etc;</li>
 *     <li>{@link #getOrderQuantity() orderQuantity} - the initial quantity of the order when it was entered but can
 *         be changed later if the order is amended;</li>
 *     <li>{@link #getDisplayQuantity() displayQuantity} - the actual display quantity of a reserve order at the end of
 *         the transaction;</li>
 *     <li>{@link #getRefreshQuantity() refreshQuantity} - the initial display quantity of a reserve order;</li>
 *     <li>{@link #getLeavesQuantity() leavesQuantity} - the remaining/open quantity of the order;</li>
 *     <li>{@link #getMatchedQuantity() matchedQuantity} - the matched quantity within the transaction resulting
 *         in the order being updated.</li>
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Nuam order event sources provide a consistent view of the order book. Their updates
 * may incorporate multiple changes to individual orders that have to be processed at the same time.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 *
 * <p>See <a href="OrderBase.html#eventFlagsSection">Event Flags</a> section of {@link OrderBase}
 * class documentation for details.
 *
 * <p>{@link IndexedEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of events.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing order books</h3>
 * <p>
 * When publishing an order event with {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents}
 * method, least significant 32 bits of order {@link #getIndex() index} must be in a range of from 0 to
 * {@link Integer#MAX_VALUE} inclusive.
 * Use {@link #setSource(OrderSource) setSource} method after {@link #setIndex(long) setIndex} to properly
 * include source identifier into the index.
 * <p>
 * A snapshot has to be published in the <em>descending</em> order of {@link #getIndex() index}, starting with
 * an event with the largest index and marking it with {@link #SNAPSHOT_BEGIN} bit in {@link #getEventFlags() eventFlags},
 * and finishing the snapshot with an event that has zero 32 least significant bits of index.
 * {@link #SNAPSHOT_END} bit in {@link #getEventFlags() eventFlags} is optional during publishing.
 * It will be properly set on receiving end anyway.
 *
 * <h3>Limitations</h3>
 * <p>
 * This event type cannot be used with {@link DXFeed#getLastEvent DXFeed.getLastEvent} method.
 *
 * <h3 id="fobSection">Full Order Book Support</h3>
 * <p>
 * Some feeds provide support for "Full Order Book" (FOB) where additional fields will be available:
 * <ul>
 *     <li>{@link #getAction() action} - event business meaning (see {@link OrderAction} for more details)</li>
 *     <li>{@link #getActionTime() actionTime} - time of the last action</li>
 *     <li>{@link #getOrderId() orderId} - ID of this order</li>
 *     <li>{@link #getAuxOrderId() auxOrderId} - additional ID for this order</li>
 *     <li>{@link #getTradeId() tradeId} - trade (order execution) ID</li>
 *     <li>{@link #getTradePrice() tradePrice} - price of the trade</li>
 *     <li>{@link #getTradeSize() tradeSize} - size of the trade</li>
 * </ul>
 *
 * <h3>Implementation details</h3>
 * <p>
 * This event is implemented on top of QDS records {@code NuamOrder#<source-id>},
 * where {@code <source-id>} is up to 4 ASCII characters with a mnemonic for the source like "nuam" for PLB
 * and "NUAM" for the FOB/FOD.
 */
@Experimental
@XmlRootElement(name = "NuamOrder")
@XmlType(propOrder = {
    "actorId", "participantId", "submitterId", "onBehalfOfSubmitterId", "clientOrderId",
    "customerAccount", "customerInfo", "exchangeInfo", "timeInForce", "timeInForceData", "triggerOrderBookId",
    "triggerPrice", "triggerSessionType", "orderType", "orderQuantity", "displayQuantity",
    "refreshQuantity", "leavesQuantity", "matchedQuantity"
})
public class NuamOrder extends Order {
    private static final long serialVersionUID = 0;

    // ========================= private static =========================

    /*
     * Nuam Market flags property has several significant bits that are packed into an integer in the following way:
     *   31..8            7..4               3..0
     * +--------+--------------------+-----------------+
     * |        | Time in Force Type | Nuam Order Type |
     * +--------+--------------------+-----------------+
     */

    // NUAM_ORDER_TYPE values are taken from NuamOrderType enum.
    static final int NUAM_ORDER_TYPE_MASK = 15;
    static final int NUAM_ORDER_TYPE_SHIFT = 0;

    // NUAM_TIME_IN_FORCE_TYPE values are taken from NuamTimeInForceType enum.
    static final int NUAM_TIME_IN_FORCE_TYPE_MASK = 15;
    static final int NUAM_TIME_IN_FORCE_TYPE_SHIFT = 4;

    // ========================= instance =========================
    private int actorId;
    private int participantId;
    private int submitterId;
    private int onBehalfOfSubmitterId;
    private String clientOrderId;
    private String customerAccount;
    private String customerInfo;
    private String exchangeInfo;
    private int timeInForceData;
    private int triggerOrderBookId;
    private double triggerPrice = Double.NaN;
    private int triggerSessionType;
    private double orderQuantity = Double.NaN;
    private double displayQuantity = Double.NaN;
    private double refreshQuantity = Double.NaN;
    private double leavesQuantity = Double.NaN;
    private double matchedQuantity = Double.NaN;
    private int nuamFlags;

    /**
     * Creates new Nuam order event with default values.
     */
    public NuamOrder() {
    }

    /**
     * Creates new Nuam order event with the specified event symbol.
     *
     * @param eventSymbol event symbol.
     */
    public NuamOrder(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns the actor owning the order, which is the actor entering the order initially or the appointed owner
     * in the case the order was entered on behalf of someone else.
     * The owning actor never changes during the order's lifetime.
     *
     * @return actorId of this Nuam order.
     */
    public int getActorId() {
        return actorId;
    }

    /**
     * Changes actor ID owning the order.
     *
     * @param actorId of this Nuam order.
     */
    public void setActorId(int actorId) {
        this.actorId = actorId;
    }

    /**
     * Returns the participant owning the order.
     *
     * @return participantId of this Nuam order.
     */
    public int getParticipantId() {
        return participantId;
    }

    /**
     * Changes participant owning the order.
     *
     * @param participantId of this Nuam order.
     */
    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    /**
     * Returns the actor submitting the message that changed the order. This can be the same as actor ID
     * if entering order on own account or the same as {@link #getOnBehalfOfSubmitterId() onBehalfSubmitterId}
     * if not entered by the owner. The submitter ID is the last entered a message that operated on the order.
     *
     * @return submitterId of this Nuam order.
     */
    public int getSubmitterId() {
        return submitterId;
    }

    /**
     * Changes submitter ID of this Nuam order.
     *
     * @param submitterId of this Nuam order.
     */
    public void setSubmitterId(int submitterId) {
        this.submitterId = submitterId;
    }

    /**
     * Returns the identifier of an actor submitting an order message on behalf of someone else.
     * When first set the onBehalfSubmitterId never changes.
     *
     * @return onBehalfSubmitterId of this Nuam order.
     */
    public int getOnBehalfOfSubmitterId() {
        return onBehalfOfSubmitterId;
    }

    /**
     * Changes the identifier of an actor submitting an order message on behalf of someone else.
     *
     * @param onBehalfOfSubmitterId of this Nuam order.
     */
    public void setOnBehalfOfSubmitterId(int onBehalfOfSubmitterId) {
        this.onBehalfOfSubmitterId = onBehalfOfSubmitterId;
    }

    /**
     * Returns a client generated order identity, referred to as ClOrdID in FIX protocol.
     *
     * @return clientOrderId of this Nuam order.
     */
    public String getClientOrderId() {
        return clientOrderId;
    }

    /**
     * Changes a client generated order identity.
     *
     * @param clientOrderId of this Nuam order.
     */
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    /**
     * Returns the account to use for this order (called exClient in the external API).
     *
     * @return customerAccount of this Nuam order.
     */
    public String getCustomerAccount() {
        return customerAccount;
    }

    /**
     * Changes the account to use for this order.
     *
     * @param customerAccount of this Nuam order.
     */
    public void setCustomerAccount(String customerAccount) {
        this.customerAccount = customerAccount;
    }

    /**
     * Returns a free text field filled in by the client entering the transaction.
     *
     * @return customerInfo of this Nuam order.
     */
    public String getCustomerInfo() {
        return customerInfo;
    }

    /**
     * Changes customer information of this Nuam order.
     *
     * @param customerInfo of this Nuam order.
     */
    public void setCustomerInfo(String customerInfo) {
        this.customerInfo = customerInfo;
    }

    /**
     * Returns exchange-specific information that can be included with an order.
     * Please note that it may contain unprintable characters.
     *
     * @return exchangeInfo of this Nuam order.
     */
    public String getExchangeInfo() {
        return exchangeInfo;
    }

    /**
     * Changes exchange-specific information of this Nuam order.
     *
     * @param exchangeInfo of this Nuam order.
     */
    public void setExchangeInfo(String exchangeInfo) {
        this.exchangeInfo = exchangeInfo;
    }

    /**
     * Returns the type of the validity period for the order, that is, the amount of time
     * an order will remain in the order book if not fully matched.
     * See {@link NuamTimeInForceType} for details.
     *
     * @return timeInForce of this Nuam order.
     */
    public NuamTimeInForceType getTimeInForce() {
        return NuamTimeInForceType.valueOf(
            EventUtil.getBits(nuamFlags, NUAM_TIME_IN_FORCE_TYPE_MASK, NUAM_TIME_IN_FORCE_TYPE_SHIFT));
    }

    /**
     * Changes the type of the validity period for the order.
     *
     * @param timeInForceType of this Nuam order.
     */
    public void setTimeInForce(NuamTimeInForceType timeInForceType) {
        nuamFlags = EventUtil.setBits(
            nuamFlags, NUAM_TIME_IN_FORCE_TYPE_MASK, NUAM_TIME_IN_FORCE_TYPE_SHIFT, timeInForceType.getCode());
    }

    /**
     * Returns additional information required to define the validity period of the order
     * The interpretation of the returned data depends on the {@link #getTimeInForce() Time in Force} type:
     * <ul>
     * <li>{@link NuamTimeInForceType#GOOD_TIL_SESSION Good-Til-Session} - the Session Type Number for the session
     *     after which the order will expire.</li>
     * <li>{@link NuamTimeInForceType#NUMBER_OF_DAYS Number-Of-Days} - the number of days after which the order
     *     will expire.</li>
     *</ul>
     *
     * @return timeInForceData of this Nuam order.
     */
    public int getTimeInForceData() {
        return timeInForceData;
    }

    /**
     * Changes the additional information required to define the validity period of the order.
     *
     * @param timeInForceData of this Nuam order.
     */
    public void setTimeInForceData(int timeInForceData) {
        this.timeInForceData = timeInForceData;
    }

    /**
     * Returns the order book ID the order will trigger on. Only applicable for trigger on price orders.
     * For example, it is possible to enter a trigger order that triggers on prices from one order book,
     * triggerOrderBookId, but when it is triggered it will be stored in another order book, orderBookId.
     * For example, you may want to trigger on prices in the most liquid future order book (the closest expiration),
     * but want to match in another future order book.
     *
     * @return triggerOrderBookId of this Nuam order.
     */
    public int getTriggerOrderBookId() {
        return triggerOrderBookId;
    }

    /**
     * Changes the order book ID the order will trigger on.
     *
     * @param triggerOrderBookId of this Nuam order.
     */
    public void setTriggerOrderBookId(int triggerOrderBookId) {
        this.triggerOrderBookId = triggerOrderBookId;
    }

    /**
     * Returns trigger price of the Nuam order.
     *
     * @return triggerPrice of this Nuam order.
     */
    public double getTriggerPrice() {
        return triggerPrice;
    }

    /**
     * Changes trigger price of this Nuam order.
     *
     * @param triggerPrice of this Nuam order.
     */
    public void setTriggerPrice(double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    /**
     * Returns trigger session type, which is used to specify which session type to trigger on.
     *
     * @return triggerSessionType of this Nuam order.
     */
    public int getTriggerSessionType() {
        return triggerSessionType;
    }

    /**
     * Changes trigger session type, which is used to specify which session type to trigger on.
     *
     * @param triggerSessionType of this Nuam order.
     */
    public void setTriggerSessionType(int triggerSessionType) {
        this.triggerSessionType = triggerSessionType;
    }

    /**
     * Returns the type of the order. See {@link NuamOrderType} for details.
     *
     * @return orderType of this Nuam Order
     */
    public NuamOrderType getOrderType() {
        return NuamOrderType.valueOf(EventUtil.getBits(nuamFlags, NUAM_ORDER_TYPE_MASK, NUAM_ORDER_TYPE_SHIFT));
    }

    /**
     * Changes the type of the order. See {@link NuamOrderType} for details.
     *
     * @param orderType of this Nuam order.
     */
    public void setOrderType(NuamOrderType orderType) {
        nuamFlags = EventUtil.setBits(nuamFlags, NUAM_ORDER_TYPE_MASK, NUAM_ORDER_TYPE_SHIFT, orderType.getCode());
    }

    /**
     * Returns the actual full order quantity.
     * Note, that the order quantity is not changed when an order is matched.
     *
     * @return orderQuantity of this Nuam order.
     */
    public double getOrderQuantity() {
        return orderQuantity;
    }

    /**
     * Changes the actual full order quantity.
     *
     * @param orderQuantity of this Nuam order.
     */
    public void setOrderQuantity(double orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

    /**
     * Returns the actual display quantity of a reserve order at the end of the transaction.
     * Set to {@link Double#NaN} if not a reserve order.
     *
     * @return displayQuantity of this Nuam order.
     */
    public double getDisplayQuantity() {
        return displayQuantity;
    }

    /**
     * Changes the actual display quantity of a reserve order at the end of the transaction.
     *
     * @param displayQuantity of this Nuam order.
     */
    public void setDisplayQuantity(double displayQuantity) {
        this.displayQuantity = displayQuantity;
    }

    /**
     * Returns the initial display quantity of a reserve order.
     * Set to {@link Double#NaN} if not a reserve order.
     *
     * @return refreshQuantity of this Nuam order.
     */
    public double getRefreshQuantity() {
        return refreshQuantity;
    }

    /**
     * Changes the initial display quantity of a reserve order.
     *
     * @param refreshQuantity of this Nuam order.
     */
    public void setRefreshQuantity(double refreshQuantity) {
        this.refreshQuantity = refreshQuantity;
    }

    /**
     * Returns the remaining/open quantity of the order.
     *
     * @return leavesQuantity of this Nuam order.
     */
    public double getLeavesQuantity() {
        return leavesQuantity;
    }

    /**
     * Changes the remaining/open quantity of the order.
     *
     * @param leavesQuantity of this Nuam order.
     */
    public void setLeavesQuantity(double leavesQuantity) {
        this.leavesQuantity = leavesQuantity;
    }

    /**
     * Returns the matched quantity within the transaction resulting in the order being updated.
     *
     * @return matchedQuantity of this Nuam order.
     */
    public double getMatchedQuantity() {
        return matchedQuantity;
    }

    /**
     * Changes the matched quantity within the transaction resulting in the order being updated.
     *
     * @param matchedQuantity of this Nuam order.
     */
    public void setMatchedQuantity(double matchedQuantity) {
        this.matchedQuantity = matchedQuantity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", actorId=").append(actorId)
            .append(", participantId=").append(participantId)
            .append(", submitterId=").append(submitterId)
            .append(", onBehalfOfSubmitterId=").append(onBehalfOfSubmitterId)
            .append(", clientOrderId='").append(clientOrderId).append("'")
            .append(", customerAccount='").append(customerAccount).append("'")
            .append(", customerInfo='").append(customerInfo).append("'")
            .append(", timeInForce=").append(getTimeInForce())
            .append(", timeInForceData=").append(timeInForceData)
            .append(", triggerOrderBookId=").append(triggerOrderBookId)
            .append(", triggerPrice=").append(triggerPrice)
            .append(", triggerSessionType=").append(triggerSessionType)
            .append(", orderType=").append(getOrderType())
            .append(", orderQuantity=").append(orderQuantity)
            .append(", displayQuantity=").append(displayQuantity)
            .append(", refreshQuantity=").append(refreshQuantity)
            .append(", leavesQuantity=").append(leavesQuantity)
            .append(", matchedQuantity=").append(matchedQuantity);
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns implementation-specific flags relevant only for Nuam Exchange.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @return NUAM flags.
     */
    int getNuamFlags() {
        return nuamFlags;
    }

    /**
     * Changes implementation-specific flags relevant only for Nuam Exchange.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @param nuamFlags NUAM flags.
     */
    void setNuamFlags(int nuamFlags) {
        this.nuamFlags = nuamFlags;
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
