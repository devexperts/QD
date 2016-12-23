/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.option;

import javax.xml.bind.annotation.XmlRootElement;

import com.devexperts.util.TimeFormat;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.market.MarketEvent;

/**
 * Underlying event is a snapshot of computed values that are available for an option underlying
 * symbol based on the option prices on the market.
 * It represents the most recent information that is available about the corresponding values on
 * the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Underlying} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getVolatility() volatility} - 30-day implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getFrontVolatility() frontVolatility} - front month implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getBackVolatility() backVolatility} - 3back month implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getPutCallRatio() putCallRatio} - ratio of put traded volume to call traded volume for a day.
 * </ul>
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Underlying}.
 */
@XmlRootElement(name = "Underlying")
public class Underlying extends MarketEvent implements LastingEvent<String> {
	private static final long serialVersionUID = 0;

	private double volatility = Double.NaN;
	private double frontVolatility = Double.NaN;
	private double backVolatility = Double.NaN;
	private double putCallRatio = Double.NaN;

	/**
	 * Creates new underlying event with default values.
	 */
	public Underlying() {}

	/**
	 * Creates new underlying event with the specified event symbol.
	 * @param eventSymbol event symbol.
	 */
	public Underlying(String eventSymbol) {
		super(eventSymbol);
	}

	/**
	 * Returns 30-day implied volatility for this underlying based on VIX methodology.
	 * @return 30-day implied volatility for this underlying based on VIX methodology.
	 */
	public double getVolatility() {
		return volatility;
	}

	/**
	 * Changes 30-day implied volatility for this underlying based on VIX methodology.
	 * @param volatility 30-day implied volatility for this underlying based on VIX methodology.
	 */
	public void setVolatility(double volatility) {
		this.volatility = volatility;
	}

	/**
	 * Returns front month implied volatility for this underlying based on VIX methodology.
	 * @return front month implied volatility for this underlying based on VIX methodology.
	 */
	public double getFrontVolatility() {
		return frontVolatility;
	}

	/**
	 * Changes front month implied volatility for this underlying based on VIX methodology.
	 * @param frontVolatility front month implied volatility for this underlying based on VIX methodology.
	 */
	public void setFrontVolatility(double frontVolatility) {
		this.frontVolatility = frontVolatility;
	}

	/**
	 * Returns back month implied volatility for this underlying based on VIX methodology.
	 * @return back month implied volatility for this underlying based on VIX methodology.
	 */
	public double getBackVolatility() {
		return backVolatility;
	}

	/**
	 * Changes back month implied volatility for this underlying based on VIX methodology.
	 * @param backVolatility back month implied volatility for this underlying based on VIX methodology.
	 */
	public void setBackVolatility(double backVolatility) {
		this.backVolatility = backVolatility;
	}

	/**
	 * Returns ratio of put traded volume to call traded volume for a day.
	 * @return ratio of put traded volume to call traded volume for a day.
	 */
	public double getPutCallRatio() {
		return putCallRatio;
	}

	/**
	 * Changes ratio of put traded volume to call traded volume for a day.
	 * @param putCallRatio ratio of put traded volume to call traded volume for a day.
	 */
	public void setPutCallRatio(double putCallRatio) {
		this.putCallRatio = putCallRatio;
	}

	/**
	 * Returns string representation of this underlying event.
	 * @return string representation of this underlying event.
	 */
	@Override
	public String toString() {
		return "Underlying{" + baseFieldsToString() + '}';
	}

	// ==================== protected access for inherited classes ====================

	protected String baseFieldsToString() {
        return getEventSymbol() +
	        ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
	        ", volatility=" + volatility +
            ", frontVolatility=" + frontVolatility +
            ", backVolatility=" + backVolatility +
            ", putCallRatio=" + putCallRatio;
    }
}
