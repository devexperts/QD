/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.market;

import java.util.Collection;
import javax.xml.bind.annotation.XmlRootElement;

import com.dxfeed.api.DXPublisher;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

/**
 * Spread order event is a snapshot for a full available market depth for all spreads
 * on a given underlying symbol. The collection of spread order events of a symbol
 * represents the most recent information that is available about spread orders on
 * the market at any given moment of time.
 *
 * <p> Spread order is similar to a regular {@link Order}, but it has a
 * {@link #getSpreadSymbol() spreadSymbol} property that contains the symbol
 * of the actual spread that is being represented by spread order object.
 * {@link #getEventSymbol() eventSymbol} property contains the underlying symbol
 * that was used in subscription.
 *
 * <p> Like regular orders, spread order events arrive from
 * multiple sources for the same market symbol and are distinguished by their
 * {@link #getIndex index}.
 * It is unique across all the sources of depth information for the symbol.
 * The event with {@link #getSize() size} of {@code 0} is a signal to remove
 * previously received order for the corresponding index.
 *
 * <h3>Properties</h3>
 *
 * {@code SpreadOrder} event has the following properties:
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
 * <li>{@link #getSize() size} - size of this order;
 * <li>{@link #getCount() count} - number of individual orders in this aggregate order;
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of this order;
 * <li>{@link #getOrderSide() orderSide} - side of this order;
 * <li>{@link #getScope() scope} - scope of this order;
 * <li>{@link #getSpreadSymbol() spreadSymbol} - spread symbol of this event.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some spread order event sources provide a consistent view of the price-level or detailed order book. Their updates
 * may incorporate multiple changes to price levels or to individual orders that have to be processed at the same time.
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
 *
 * A snapshot has to be published in the <em>descending</em> order of {@link #getIndex() index}, starting with
 * an event with the largest index and marking it with {@link #SNAPSHOT_BEGIN} bit in {@link #getEventFlags() eventFlags},
 * and finishing the snapshot with an event that has zero 32 least significant bits of index.
 * {@link #SNAPSHOT_END} bit in {@link #getEventFlags() eventFlags} is optional during publishing.
 * It will be properly set on receiving end anyway.
 *
 * <h3>Limitations</h3>
 *
 * This event type cannot be used with {@link com.dxfeed.api.DXFeed#getLastEvent DXFeed.getLastEvent} method.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code SpreadOrder#<source-id>},
 * where {@code <source-id>} is up to 3 ASCII characters with a mnemonic for the source like "ISE".
 */
@XmlRootElement(name = "SpreadOrder")
public class SpreadOrder extends OrderBase {
	private static final long serialVersionUID = 0;

	private String spreadSymbol;

	/**
	 * Creates new spread order with default values.
	 */
	public SpreadOrder() {}

	/**
	 * Creates new order with the specified underlying event symbol.
	 * @param eventSymbol underlying event symbol.
	 */
	public SpreadOrder(String eventSymbol) {
		super(eventSymbol);
	}

	/**
	 * Returns spread symbol of this event.
	 * @return spread symbol of this event.
	 */
	public String getSpreadSymbol() {
		return spreadSymbol;
	}

	/**
	 * Changes spread symbol of this event.
	 * @param spreadSymbol spread symbol of this event.
	 */
	public void setSpreadSymbol(String spreadSymbol) {
		this.spreadSymbol = spreadSymbol;
	}

	/**
	 * Returns string representation of this spread order event.
	 * @return string representation of this spread order event.
	 */
	@Override
	public String toString() {
		return "SpreadOrder{" + baseFieldsToString() +
			", spreadSymbol='" + spreadSymbol + "'" +
			"}";
	}
}
