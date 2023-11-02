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
 * Represents an extension of {@link Order} introducing analytic information, e.g. adding to this order iceberg related
 * information ({@link #icebergPeakSize}, {@link #icebergHiddenSize}, {@link #icebergExecutedSize}).
 * The collection of analytic order events of a symbol represents the most recent analytic information
 * that is available about orders on the market at any given moment of time.
 *
 * <p> Analytic order is similar to a regular {@link Order}. In addition this event has few additional properties:
 * <ul>
 *     <li>{@link #icebergPeakSize} - the size of the peak, i.e. the visible part of the iceberg,
 *                              that is being continually refilled until the order is fully traded or cancelled;
 *     <li>{@link #icebergHiddenSize} - the prediction of current hidden size of the iceberg, as inferred by the model;
 *     <li>{@link #icebergExecutedSize} - the executed size of the iceberg order. For {@link IcebergType#SYNTHETIC} type
 *                             represents total executed size of all orders сomposing current iceberg;
 *     <li>{@link #getIcebergType()} - type of the iceberg, either native (exchange-managed) or synthetic (managed outside of the exchange).
 * </ul>
 *
 * <p> Like regular orders, analytic order events arrive from
 * multiple sources for the same market symbol and are distinguished by their
 * {@link #getIndex index}.
 * It is unique across all the sources of depth information for the symbol.
 * The event with {@link #getSizeAsDouble() sizeAsDouble} either {@code 0} or {@link Double#NaN NaN}
 * is a signal to remove previously received order for the corresponding index.
 * The method {@link #hasSize() hasSize} is a convenient method to test for size presence.
 *
 * <h3>Properties</h3>
 * <p>
 * {@code Analytic} event has the following properties:
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
 * <li>{@link #getIcebergPeakSize() icebergPeakSize} - iceberg peak size of this analytic order;
 * <li>{@link #getIcebergHiddenSize() icebergHiddenSize} - iceberg hidden size of this analytic order;
 * <li>{@link #getIcebergExecutedSize() icebergExecutedSize} - iceberg executed size of this analytic order;
 * <li>{@link #getIcebergType() icebergType} - iceberg type of this analytic order.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 * <p>
 * Analytic order event sources provide a consistent view of the analytic order book. Their updates
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
 * <p>
 * This event is implemented on top of QDS records {@code AnalyticOrder#<source-id>},
 * where {@code <source-id>} is up to 4 ASCII characters with a mnemonic for the source like "GLBX".
 */
@XmlRootElement(name = "AnalyticOrder")
@XmlType(propOrder = {"icebergPeakSize", "icebergHiddenSize", "icebergExecutedSize", "icebergType"})
public class AnalyticOrder extends Order {
    private static final long serialVersionUID = 0;

    // ========================= private static =========================

    /*
     * Analytic flags property has several significant bits that are packed into an integer in the following way:
     *      31...2       1    0
     * +--------------+-------+-------+
     * |              |   IcebergType |
     * +--------------+-------+-------+
     */

    // TYPE values are taken from Type enum.
    static final int ICEBERG_TYPE_MASK = 3;
    static final int ICEBERG_TYPE_SHIFT = 0;

    // ========================= instance =========================

    private double icebergPeakSize = Double.NaN;
    private double icebergHiddenSize = Double.NaN;
    private double icebergExecutedSize = Double.NaN;
    private int icebergFlags;

    /**
     * Creates new analytic order with default values.
     */
    public AnalyticOrder() {
    }

    /**
     * Creates new analytic order with the specified event symbol.
     *
     * @param eventSymbol event symbol.
     */
    public AnalyticOrder(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns iceberg peak size of this analytic order.
     *
     * @return iceberg peak size of this analytic order.
     */
    public double getIcebergPeakSize() {
        return icebergPeakSize;
    }

    /**
     * Changes iceberg peak size of this analytic order.
     *
     * @param icebergPeakSize iceberg peak size of this analytic order.
     */
    public void setIcebergPeakSize(double icebergPeakSize) {
        this.icebergPeakSize = icebergPeakSize;
    }

    /**
     * Returns iceberg hidden size of this analytic order.
     *
     * @return iceberg hidden size of this analytic order.
     */
    public double getIcebergHiddenSize() {
        return icebergHiddenSize;
    }

    /**
     * Changes iceberg hidden size of this analytic order.
     *
     * @param icebergHiddenSize iceberg hidden size of this analytic order.
     */
    public void setIcebergHiddenSize(double icebergHiddenSize) {
        this.icebergHiddenSize = icebergHiddenSize;
    }

    /**
     * Returns iceberg executed size of this analytic order.
     *
     * @return iceberg executed size of this analytic order.
     */

    public double getIcebergExecutedSize() {
        return icebergExecutedSize;
    }

    /**
     * Changes iceberg executed size of this analytic order.
     *
     * @param icebergExecutedSize iceberg executed size of this analytic order.
     */
    public void setIcebergExecutedSize(double icebergExecutedSize) {
        this.icebergExecutedSize = icebergExecutedSize;
    }

    /**
     * Returns iceberg type of this analytic order.
     *
     * @return iceberg type of this analytic order.
     */
    public IcebergType getIcebergType() {
        return IcebergType.valueOf(Util.getBits(icebergFlags, ICEBERG_TYPE_MASK, ICEBERG_TYPE_SHIFT));
    }

    /**
     * Changes iceberg type of this analytic order.
     *
     * @param icebergType iceberg type of this analytic order.
     */
    public void setIcebergType(IcebergType icebergType) {
        icebergFlags = Util.setBits(icebergFlags, ICEBERG_TYPE_MASK, ICEBERG_TYPE_SHIFT, icebergType.getCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", icebergPeakSize=").append(icebergPeakSize)
            .append(", icebergHiddenSize=").append(icebergHiddenSize)
            .append(", icebergExecutedSize=").append(icebergExecutedSize)
            .append(", icebergType=").append(getIcebergType());
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns implementation-specific flags relevant only for iceberg related part of analytic order.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @return iceberg flags.
     */
    int getIcebergFlags() {
        return icebergFlags;
    }

    /**
     * Changes implementation-specific flags relevant only for iceberg related part of analytic order.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     *
     * @param icebergFlags iceberg flags.
     */
    void setIcebergFlags(int icebergFlags) {
        this.icebergFlags = icebergFlags;
    }
}
