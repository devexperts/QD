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
package com.dxfeed.ipf.option;

import com.devexperts.util.DayUtil;
import com.dxfeed.ipf.InstrumentProfile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder class for a set of option chains grouped by product or underlying symbol.
 *
 * <h3>Threads and clocks</h3>
 *
 * This class is <b>NOT</b> thread-safe and cannot be used from multiple threads without external synchronization.
 *
 * @param <T> The type of option instrument instances.
 */
public class OptionChainsBuilder<T> {

    /**
     * Builds options chains for all options from the given collections of {@link InstrumentProfile instrument profiles}.
     * @param instruments collection of instrument profiles.
     * @return builder with all the options from instruments collection.
     */
    public static OptionChainsBuilder<InstrumentProfile> build(Collection<InstrumentProfile> instruments) {
        OptionChainsBuilder<InstrumentProfile> ocb = new OptionChainsBuilder<>();
        for (InstrumentProfile ip : instruments) {
            if (!"OPTION".equals(ip.getType()))
                continue;
            ocb.setProduct(ip.getProduct());
            ocb.setUnderlying(ip.getUnderlying());
            ocb.setExpiration(ip.getExpiration());
            ocb.setLastTrade(ip.getLastTrade());
            ocb.setMultiplier(ip.getMultiplier());
            ocb.setSPC(ip.getSPC());
            ocb.setAdditionalUnderlyings(ip.getAdditionalUnderlyings());
            ocb.setMMY((ip.getMMY()));
            ocb.setOptionType(ip.getOptionType());
            ocb.setExpirationStyle(ip.getExpirationStyle());
            ocb.setSettlementStyle(ip.getSettlementStyle());
            ocb.setCFI(ip.getCFI());
            ocb.setStrike(ip.getStrike());
            ocb.addOption(ip);
        }
        return ocb;
    }

    // ---------------- instance ----------------

    String product = "";
    String underlying = "";
    OptionSeries<T> series = new OptionSeries<>();
    String cfi = "";
    double strike;

    private final Map<String, OptionChain<T>> chains = new HashMap<>();

    /**
     * Creates new option chains builder.
     */
    public OptionChainsBuilder() {
    }

    /**
     * Changes product for futures and options on futures (underlying asset name).
     * Example: "/YG".
     * @param product product for futures and options on futures (underlying asset name).
     */
    public void setProduct(String product) {
        this.product = product == null || product.isEmpty() ? "" : product;
    }

    /**
     * Changes primary underlying symbol for options.
     * Example: "C", "/YGM9"
     * @param underlying primary underlying symbol for options.
     */
    public void setUnderlying(String underlying) {
        this.underlying = underlying == null || underlying.isEmpty() ? "" : underlying;
    }

    /**
     * Changes day id of expiration.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090117).
     * @param expiration day id of expiration.
     */
    public void setExpiration(int expiration) {
        series.expiration = expiration;
    }

    /**
     * Changes day id of last trading day.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090116).
     * @param lastTrade day id of last trading day.
     */
    public void setLastTrade(int lastTrade) {
        series.lastTrade = lastTrade;
    }

    /**
     * Changes market value multiplier.
     * Example: 100, 33.2.
     * @param multiplier market value multiplier.
     */
    public void setMultiplier(double multiplier) {
        series.multiplier = multiplier;
    }

    /**
     * Changes shares per contract for options.
     * Example: 1, 100.
     * @param spc shares per contract for options.
     */
    public void setSPC(double spc) {
        series.spc = spc;
    }

    /**
     * Changes additional underlyings for options, including additional cash.
     * It shall use following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;LIST&gt; ::= &lt;AU&gt; | &lt;AU&gt; &lt;semicolon&gt; &lt;space&gt; &lt;LIST&gt;
     *     &lt;AU&gt; ::= &lt;UNDERLYING&gt; &lt;space&gt; &lt;SPC&gt; </pre>
     * the list shall be sorted by &lt;UNDERLYING&gt;.
     * Example: "SE 50", "FIS 53; US$ 45.46".
     * @param additionalUnderlyings additional underlyings for options, including additional cash.
     */
    public void setAdditionalUnderlyings(String additionalUnderlyings) {
        series.additionalUnderlyings = additionalUnderlyings == null || additionalUnderlyings.isEmpty() ? "" : additionalUnderlyings;
    }

    /**
     * Changes maturity month-year as provided for corresponding FIX tag (200).
     * It can use several different formats depending on data source:
     * <ul>
     * <li>YYYYMM – if only year and month are specified
     * <li>YYYYMMDD – if full date is specified
     * <li>YYYYMMwN – if week number (within a month) is specified
     * </ul>
     * @param mmy  maturity month-year as provided for corresponding FIX tag (200).
     */
    public void setMMY(String mmy) {
        series.mmy = mmy == null || mmy.isEmpty() ? "" : mmy;
    }

    /**
     * Changes type of option.
     * It shall use one of following values:
     * <ul>
     * <li>STAN = Standard Options
     * <li>LEAP = Long-term Equity AnticiPation Securities
     * <li>SDO = Special Dated Options
     * <li>BINY = Binary Options
     * <li>FLEX = FLexible EXchange Options
     * <li>VSO = Variable Start Options
     * <li>RNGE = Range
     * </ul>
     * @param optionType type of option.
     */
    public void setOptionType(String optionType) {
        series.optionType = optionType == null || optionType.isEmpty() ? "" : optionType;
    }

    /**
     * Returns expiration cycle style, such as "Weeklys", "Quarterlys".
     * @param expirationStyle expiration cycle style.
     */
    public void setExpirationStyle(String expirationStyle) {
        series.expirationStyle = expirationStyle == null || expirationStyle.isEmpty() ? "" : expirationStyle;
    }

    /**
     * Changes settlement price determination style, such as "Open", "Close".
     * @param settlementStyle settlement price determination style.
     */
    public void setSettlementStyle(String settlementStyle) {
        series.settlementStyle = settlementStyle == null || settlementStyle.isEmpty() ? "" : settlementStyle;
    }

    /**
     * Changes Classification of Financial Instruments code.
     * It is a mandatory field as it is the only way to distinguish Call/Put option type,
     * American/European exercise, Cash/Physical delivery.
     * It shall use six-letter CFI code from ISO 10962 standard.
     * It is allowed to use 'X' extensively and to omit trailing letters (assumed to be 'X').
     * See <a href="http://en.wikipedia.org/wiki/ISO_10962">ISO 10962 on Wikipedia</a>.
     * Example: "OC" for generic call, "OP" for generic put.
     * @param cfi CFI code.
     */
    public void setCFI(String cfi) {
        this.cfi = cfi == null || cfi.isEmpty() ? "" : cfi;
        series.cfi = this.cfi.length() < 2 ? this.cfi : this.cfi.charAt(0) + "X" + this.cfi.substring(2);
    }

    /**
     * Changes strike price for options.
     * Example: 80, 22.5.
     * @param strike strike price for options.
     */
    public void setStrike(double strike) {
        this.strike = strike;
    }

    /**
     * Adds an option instrument to this builder.
     * Option is added to chains for the currently set {@link #setProduct(String) product} and/or
     * {@link #setUnderlying(String) underlying} to the {@link OptionSeries series} that corresponding
     * to all other currently set attributes. This method is safe in the sense that is ignores
     * illegal state of the builder. It only adds an option when all of the following conditions are met:
     * <ul>
     *  <li>{@link #setCFI(String) CFI code} is set and starts with either "OC" for call or "OP" for put.
     *  <li>{@link #setExpiration(int) expiration} is set and is not zero;
     *  <li>{@link #setStrike(double) strike} is set and is not {@link Double#NaN NaN} nor {@link Double#isInfinite() infinite};
     *  <li>{@link #setProduct(String) product} or {@link #setUnderlying(String) underlying symbol} are set;
     * </ul>
     * All the attributes remain set as before after the call to this method, but
     * {@link #getChains() chains} are updated correspondingly.
     *
     * @param option option to add.
     */
    public void addOption(T option) {
        boolean isCall = cfi.startsWith("OC");
        if (!isCall && !cfi.startsWith("OP"))
            return;
        if (series.expiration == 0)
            return;
        if (Double.isNaN(strike) || Double.isInfinite(strike))
            return;
        if (product.length() > 0)
            getOrCreateChain(product).addOption(series, isCall, strike, option);
        if (underlying.length() > 0)
            getOrCreateChain(underlying).addOption(series, isCall, strike, option);
    }

    private OptionChain<T> getOrCreateChain(String symbol) {
        OptionChain<T> chain = chains.get(symbol);
        if (chain == null)
            chains.put(symbol, chain = new OptionChain<>(symbol));
        return chain;
    }

    /**
     * Returns a view of chains created by this builder.
     * It updates as new options are added with {@link #addOption(Object) addOption} method.
     * @return view of chains created by this builder.
     */
    public Map<String, OptionChain<T>> getChains() {
        return chains;
    }
}
