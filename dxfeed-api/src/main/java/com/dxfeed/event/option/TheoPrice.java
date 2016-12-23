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
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.devexperts.util.TimeFormat;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.impl.XmlTimeAdapter;

/**
 * Theo price is a snapshot of the theoretical option price computation that is
 * periodically performed by <a href="http://www.devexperts.com/en/products/price.html">dxPrice</a>
 * model-free computation.
 * It represents the most recent information that is available about the corresponding
 * values at any given moment of time.
 * The values include first and second order derivative of the price curve by price, so that
 * the real-time theoretical option price can be estimated on real-time changes of the underlying
 * price in the vicinity.
 *
 * <h3>Properties</h3>
 *
 * {@code TheoPrice} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getTime() time} - time of the last theo price computation;
 * <li>{@link #getUnderlyingPrice() underlyingPrice} - underlying price at the time of theo price computation;
 * <li>{@link #getDelta() delta} - delta of the theoretical price;
 * <li>{@link #getGamma() gamma} -  gamma of the theoretical price;
 * <li>{@link #getDividend() dividend} - implied simple dividend return of the corresponding option series;
 * <li>{@link #getInterest() interest} - implied simple interest return of the corresponding option series.
 * </ul>
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code TheoPrice}.
 */
@XmlRootElement(name = "TheoPrice")
public class TheoPrice extends MarketEvent implements LastingEvent<String> {
	private static final long serialVersionUID = 0;

	private long time;
	private double price = Double.NaN;
	private double underlyingPrice = Double.NaN;
	private double delta = Double.NaN;
	private double gamma = Double.NaN;
	private double dividend = Double.NaN;
	private double interest = Double.NaN;

	/**
	 * Creates new theo price event with default values.
	 */
	public TheoPrice() {}

	/**
	 * Creates new theo price event with the specified event symbol.
	 * @param eventSymbol event symbol.
	 */
	public TheoPrice(String eventSymbol) {
		super(eventSymbol);
	}

	/**
	 * Returns time of the last theo price computation.
	 * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
	 * @return time of the last theo price computation.
	 */
	@XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
	@XmlSchemaType(name="dateTime")
	public long getTime() {
		return time;
	}

	/**
	 * Changes time of the last theo price computation.
	 * @param time time of the last theo price computation.
	 */
	public void setTime(long time) {
		this.time = time;
	}

	/**
	 * Returns theoretical option price.
	 * @return theoretical option price.
	 */
	public double getPrice() {
		return price;
	}

	/**
	 * Changes theoretical option price.
	 * @param price theoretical option price.
	 */
	public void setPrice(double price) {
		this.price = price;
	}

	/**
	 * Returns underlying price at the time of theo price computation.
	 * @return underlying price at the time of theo price computation.
	 */
	public double getUnderlyingPrice() {
		return underlyingPrice;
	}

	/**
	 * Changes underlying price at the time of theo price computation.
	 * @param underlyingPrice underlying price at the time of theo price computation.
	 */
	public void setUnderlyingPrice(double underlyingPrice) {
		this.underlyingPrice = underlyingPrice;
	}

	/**
	 * Returns delta of the theoretical price.
	 * Delta is the first derivative of the theoretical price by the underlying price.
	 * @return delta of the theoretical price.
	 */
	public double getDelta() {
		return delta;
	}

	/**
	 * Changes delta of the theoretical price.
	 * @param delta delta of the theoretical price.
	 */
	public void setDelta(double delta) {
		this.delta = delta;
	}

	/**
	 * Returns gamma of the theoretical price.
	 * Gamma is the second derivative of the theoretical price by the underlying price.
	 * @return gamma of the theoretical price.
	 */
	public double getGamma() {
		return gamma;
	}

	/**
	 * Changes gamma of the theoretical price.
	 * @param gamma gamma of the theoretical price.
	 */
	public void setGamma(double gamma) {
		this.gamma = gamma;
	}

	/**
	 * Returns implied simple dividend return of the corresponding option series.
	 * See <a href="package-summary.html#model">the model section</a> for an explanation this simple dividend return \( Q(\tau) \).
	 * @return implied simple dividend return of the corresponding option series.
	 */
	public double getDividend() {
		return dividend;
	}

	/**
	 * Changes implied simple dividend return of the corresponding option series.
	 * See <a href="package-summary.html#model">the model section</a> for an explanation this simple dividend return \( Q(\tau) \).
	 * @param dividend implied simple dividend return of the corresponding option series.
	 */
	public void setDividend(double dividend) {
		this.dividend = dividend;
	}

	/**
	 * Returns string representation of this theo price object.
	 * @return string representation of this theo price object.
	 */
	/**
	 * Returns implied simple interest return of the corresponding option series.
	 * See <a href="package-summary.html#model">the model section</a> for an explanation this simple interest return \( R(\tau) \).
	 * @return implied simple interest return of the corresponding option series.
	 */
	public double getInterest() {
		return interest;
	}

	/**
	 * Changes implied simple interest return of the corresponding option series.
	 * See <a href="package-summary.html#model">the model section</a> for an explanation this simple interest return \( R(\tau) \).
	 * @param interest implied simple interest return of the corresponding option series.
	 */
	public void setInterest(double interest) {
		this.interest = interest;
	}

	@Override
	public String toString() {
		return "TheoPrice{" + getEventSymbol() +
			", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
			", time=" + TimeFormat.DEFAULT.format(time) +
			", price=" + price +
			", underlyingPrice=" + underlyingPrice +
			", delta=" + delta +
			", gamma=" + gamma +
			", dividend=" + dividend +
			", interest=" + interest +
			'}';
	}
}
