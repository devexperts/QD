/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

import java.util.Collection;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents an extension of {@link Order} for the symbols traded on the OTC Markets. It includes the OTC Markets
 * specific quote data, such as {@link #getQuoteAccessPayment() Quote Access Payment (QAP) value},
 * {@link #getOtcMarketsPriceType() Quote Price Type}, {@link #isOpen() Open/Closed status},
 * and {@link #isUnsolicited() Unsolicited quote indicator}. The original event (OTC Quote)  published by the exchange
 * is presented in the form of two separate {@code OTCMarketsOrder} events (bid and ask side).
 * Note that the description of the fields (e.g. QAP) uses the original "quote" term.
 * <p>
 * For more information about original fields, QAP, Quote Flags and Extended Quote Flags,
 * see <a href="https://downloads.dxfeed.com/specifications/OTC_Markets_Multicast_Data_Feeds.pdf">OTC Markets Multicast Data Feed</a>.
 * <p>
 * For more information about display requirements,
 * see <a href="https://downloads.dxfeed.com/specifications/OTC_Markets_Data_Display_Requirements.pdf">OTC Markets Display Requirements</a>.
 *
 * <p> Like regular orders, OTC Markets order events arrive from
 * multiple sources for the same market symbol and are distinguished by their {@link #getIndex index}.
 * It is unique across all the sources of depth information for the symbol.
 * The event with {@link #getSizeAsDouble() sizeAsDouble} either {@code 0} or {@link Double#NaN NaN}
 * is a signal to remove previously received order for the corresponding index.
 * The method {@link #hasSize() hasSize} is a convenient method to test for size presence.
 *
 * <h3>Quote Flags</h3>
 *
 * Quote Flags from OTC Markets are mapped to the access methods of this class as follows:
 * <ul>
 *     <li>Update Side - {@link #getOrderSide() orderSide}
 *     <li>State - {@link #isOpen() open}
 *     <li>Ask/Bid Unsolicited - {@link #isUnsolicited() unsolicited}
 *     <li>Ask/Bid Priced, Aks/Bid OW/BW - {@link #getOtcMarketsPriceType() otcMarketsPriceType}
 * </ul>
 *
 * <h3>Extended Quote Flags</h3>
 *
 * Extended Quote Flags from OTC Markets are mapped to the access methods of this class as follows:
 * <ul>
 *     <li>QuoteSaturatedFlag - {@link #isSaturated() saturated}
 *     <li>Bid/Offer AutoExFlag - {@link #isAutoExecution() autoExecution}
 *     <li>NMSConditionalQuoteFlag - {@link #isNmsConditional() nmsConditional}
 * </ul>
 *
 * <h3>Properties</h3>
 *
 * {@code OTCMarketsOrder} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - underlying symbol of this event;
 * <li>{@link #getSource() source} - source of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this order;
 * <li>{@link #getTime() time} - time of this order;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of this order;
 * <li>{@link #getSequence() sequence} - sequence of this order;
 * <li>{@link #getPrice() price} - price of this order;
 * <li>{@link #getSize() size} - size of this order as integer number (rounded toward zero);
 * <li>{@link #getSizeAsDouble() sizeAsDouble} - size of this order as floating number with fractions;
 * <li>{@link #getExecutedSize() executedSize} - executed size of this order;
 * <li>{@link #getCount() count} - number of individual orders in this aggregate order;
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of this order;
 * <li>{@link #getOrderSide() orderSide} - side of this order;
 * <li>{@link #getScope() scope} - scope of this order;
 * <li>{@link #getMarketMaker() marketMaker} - market maker or other aggregate identifier of this order;
 * <li>{@link #getQuoteAccessPayment() QAP} - Quote Access Payment of this OTC Markets order event;
 * <li>{@link #isOpen() open} - whether this OTC Markets order event is open;
 * <li>{@link #isUnsolicited() unsolicited} - whether this OTC Markets order event is unsolicited;
 * <li>{@link #getOtcMarketsPriceType() otcMarketsPriceType} - OTC Markets price type of this OTC Markets order.
 * <li>{@link #isSaturated() saturated} - whether this OTC Markets order event should NOT be considered for the inside price.
 * <li>{@link #isAutoExecution() autoExecution} - whether this OTC Markets order is in 'AutoEx' mode.
 * <li>{@link #isNmsConditional() nmsConditional} - whether this OTC Markets order represents a NMS conditional.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * OTC Markets order event sources provide a consistent view of the order book. Their updates
 * may incorporate multiple changes to individual orders that have to be processed at the same time.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 *
 * <p> See <a href="OrderBase.html#eventFlagsSection">Event Flags</a> section of {@link OrderBase}
 * class documentation for details.
 *
 * <p>{@link IndexedEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of events.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing order books</h3>
 *
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
 *
 * This event type cannot be used with {@link DXFeed#getLastEvent DXFeed.getLastEvent} method.
 *
 * <h3><a name="fobSection">Full Order Book Support</a></h3>
 *
 * Some feeds provide support for "Full Order Book" (FOB) where additional fields will be available:
 * <ul>
 * <li>{@link #getAction() action} - event business meaning (see {@link OrderAction} for more details)</li>
 * <li>{@link #getActionTime() actionTime} - time of the last action</li>
 * <li>{@link #getOrderId() orderId} - ID of this order</li>
 * <li>{@link #getAuxOrderId() auxOrderId} - additional ID for this order</li>
 * <li>{@link #getTradeId() tradeId} - trade (order execution) ID</li>
 * <li>{@link #getTradePrice() tradePrice} - price of the trade</li>
 * <li>{@link #getTradeSize() tradeSize} - size of the trade</li>
 * </ul>
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code OtcMarketsOrder#<source-id>},
 * where {@code <source-id>} is up to 4 ASCII characters with a mnemonic for the source like "pink".
 */
@XmlRootElement(name = "OtcMarketsOrder")
@XmlType(propOrder = {
    "quoteAccessPayment", "open", "unsolicited", "otcMarketsPriceType", "saturated", "autoExecution", "nmsConditional"
})
public class OtcMarketsOrder extends Order {
    private static final long serialVersionUID = 0;

    // ========================= private static =========================

    /*
     * OTC Markets flags property has several significant bits that are packed into an integer in the following way:
     *   31..7          6                5             4          3       2          1          0
     * +-------+-----------------+---------------+-----------+--------+-------+-------------+------+
     * |       | NMS Conditional | AutoExecution | Saturated | OTC Price Type | Unsolicited | Open |
     * +-------+-----------------+---------------+-----------+--------+-------+-------------+------+
     * |                Extended Quote Flags                 |             Quote Flags             |
     * +-----------------------------------------------------+-------------------------------------+
     */

    static final int NMS_CONDITIONAL = 1 << 6;
    static final int AUTO_EXECUTION = 1 << 5;
    static final int SATURATED = 1 << 4;

    // OTC_PRICE_TYPE values are taken from OtcMarketsPriceType enum.
    static final int OTC_PRICE_TYPE_MASK = 3;
    static final int OTC_PRICE_TYPE_SHIFT = 2;

    static final int UNSOLICITED = 1 << 1;
    static final int OPEN = 1;

    // ========================= instance =========================

    private int quoteAccessPayment;
    private int otcMarketsFlags;

    /**
     * Creates new OTC Markets order event with default values.
     */
    public OtcMarketsOrder() {
    }

    /**
     * Creates new OTC Markets order event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public OtcMarketsOrder(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns Quote Access Payment (QAP) of this OTC Markets order.
     * QAP functionality allows participants to dynamically set access fees or rebates,
     * in real-time and on a per-security basis through OTC Dealer or OTC FIX connections.
     * Positive integers (1 to 30) indicate a rebate, and negative integers (-1 to -30) indicate an access fee.
     * 0 indicates no rebate or access fee.
     * @return QAP of this OTC Markets order.
     */
    public int getQuoteAccessPayment() {
        return quoteAccessPayment;
    }

    /**
     * Changes Quote Access Payment (QAP) of this OTC Markets order.
     * @param quoteAccessPayment QAP of this OTC Markets order.
     */
    public void setQuoteAccessPayment(int quoteAccessPayment) {
        this.quoteAccessPayment = quoteAccessPayment;
    }

    /**
     * Returns whether this event is available for business within the operating hours of the OTC Link system.
     * All quotes will be closed at the start of the trading day and will remain closed until the traders open theirs.
     * @return {@code true} if this event is available for business within the operating hours of the OTC Link system.
     */
    public boolean isOpen() {
        return (otcMarketsFlags & OPEN) != 0;
    }

    /**
     * Changes whether this event is available for business within the operating hours of the OTC Link system.
     * @param open {@code true} if this event is available for business within the operating hours of the OTC Link system.
     */
    public void setOpen(boolean open) {
        otcMarketsFlags = open ? otcMarketsFlags | OPEN : otcMarketsFlags & ~OPEN;
    }

    /**
     * Returns whether this event is unsolicited.
     * @return {@code true} if this event is unsolicited.
     */
    public boolean isUnsolicited() {
        return (otcMarketsFlags & UNSOLICITED) != 0;
    }

    /**
     * Changes whether this event is unsolicited.
     * @param unsolicited {@code true} if this event is unsolicited.
     */
    public void setUnsolicited(boolean unsolicited) {
        otcMarketsFlags = unsolicited ? otcMarketsFlags | UNSOLICITED : otcMarketsFlags & ~UNSOLICITED;
    }

    /**
     * Returns OTC Markets price type of this OTC Markets order events.
     * @return OTC Markets price type of this OTC Markets order events.
     */
    public OtcMarketsPriceType getOtcMarketsPriceType() {
        return OtcMarketsPriceType.valueOf(Util.getBits(otcMarketsFlags, OTC_PRICE_TYPE_MASK, OTC_PRICE_TYPE_SHIFT));
    }

    /**
     * Changes OTC Markets price type of this OTC Markets order events.
     * @param otcPriceType OTC Markets price type of this OTC Markets order events.
     */
    public void setOtcMarketsPriceType(OtcMarketsPriceType otcPriceType) {
        otcMarketsFlags = Util.setBits(otcMarketsFlags, OTC_PRICE_TYPE_MASK, OTC_PRICE_TYPE_SHIFT, otcPriceType.getCode());
    }

    /**
     * Returns whether this event should NOT be considered for the inside price.
     * @return {@code true} if this event should NOT be considered for the inside price.
     */
    public boolean isSaturated() {
        return (otcMarketsFlags & SATURATED) != 0;
    }

    /**
     * Changes whether this event should NOT be considered for the inside price.
     * @param saturated {@code true} if this event should NOT be considered for the inside price.
     */
    public void setSaturated(boolean saturated) {
        otcMarketsFlags = saturated ? otcMarketsFlags | SATURATED : otcMarketsFlags & ~SATURATED;
    }

    /**
     * Returns whether this event is in 'AutoEx' mode.
     * If this event is in 'AutoEx' mode then a response to an OTC Link trade message will be immediate.
     * @return {@code true} if this event is in 'AutoEx' mode.
     */
    public boolean isAutoExecution() {
        return (otcMarketsFlags & AUTO_EXECUTION) != 0;
    }

    /**
     * Changes whether this event is in 'AutoEx' mode.
     * @param autoExecution {@code true} if this event is in 'AutoEx' mode.
     */
    public void setAutoExecution(boolean autoExecution) {
        otcMarketsFlags = autoExecution ? otcMarketsFlags | AUTO_EXECUTION : otcMarketsFlags & ~AUTO_EXECUTION;
    }

    /**
     * Returns whether this event represents a NMS conditional.
     * This flag indicates the displayed {@link #getSizeAsDouble() size}
     * is a round lot at least two times greater than the minimum round lot size in the security
     * and a trade message relating to the event cannot be sent or filled for less than the displayed size.
     * @return {@code true} if this event represents a NMS conditional.
     */
    public boolean isNmsConditional() {
        return (otcMarketsFlags & NMS_CONDITIONAL) != 0;
    }

    /**
     * Changes whether this event represents a NMS conditional.
     * @param nmsConditional {@code true} if this event represents a NMS conditional.
     */
    public void setNmsConditional(boolean nmsConditional) {
        otcMarketsFlags = nmsConditional ? otcMarketsFlags | NMS_CONDITIONAL : otcMarketsFlags & ~NMS_CONDITIONAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", QAP=").append(quoteAccessPayment)
            .append(", open=").append(isOpen())
            .append(", unsolicited=").append(isUnsolicited())
            .append(", priceType=").append(getOtcMarketsPriceType())
            .append(", saturated=").append(isSaturated())
            .append(", autoEx=").append(isAutoExecution())
            .append(", NMS=").append(isNmsConditional());
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns implementation-specific flags relevant only for OTC Markets (Quote Flags + Extended Quote Flags).
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @return OTC Markets flags.
     */
    int getOtcMarketsFlags() {
        return otcMarketsFlags;
    }

    /**
     * Changes implementation-specific flags relevant only for OTC Markets (Quote Flags + Extended Quote Flags).
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @param otcMarketsFlags OTC Markets flags.
     */
    void setOtcMarketsFlags(int otcMarketsFlags) {
        this.otcMarketsFlags = otcMarketsFlags;
    }
}
