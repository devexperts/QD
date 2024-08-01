/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf;

import com.devexperts.util.DayUtil;
import com.dxfeed.schedule.Schedule;

import java.io.Serializable;
import java.util.Collection;

/**
 * Represents basic profile information about market instrument.
 * Please see <a href="http://www.dxfeed.com/downloads/documentation/dxFeed_Instrument_Profile_Format.pdf">Instrument Profile Format documentation</a>
 * for complete description.
 */
@SuppressWarnings({"NonConstantFieldWithUpperCaseName", "InstanceVariableNamingConvention", "MethodParameterNamingConvention"})
public final class InstrumentProfile implements Comparable<InstrumentProfile>, Serializable {
    private static final long serialVersionUID = 0;

    private String type = "";
    private String symbol = "";
    private String description = "";
    private String localSymbol = "";
    private String localDescription = "";
    private String country = "";
    private String opol = "";
    private String exchangeData = "";
    private String exchanges = "";
    private String currency = "";
    private String baseCurrency = "";
    private String cfi = "";
    private String isin = "";
    private String sedol = "";
    private String cusip = "";
    private int icb;
    private int sic;
    private double multiplier;
    private String product = "";
    private String underlying = "";
    private double spc;
    private String additionalUnderlyings = "";
    private String mmy = "";
    private int expiration;
    private int lastTrade;
    private double strike;
    private String optionType = "";
    private String expirationStyle = "";
    private String settlementStyle = "";
    private String priceIncrements = "";
    private String tradingHours = "";

    private String[] customFields;

    /**
     * Creates an instrument profile with default values.
     */
    public InstrumentProfile() {}

    /**
     * Creates an instrument profile as a copy of the specified instrument profile.
     * @param ip an instrument profile to copy.
     */
    public InstrumentProfile(InstrumentProfile ip) {
        type = ip.type;
        symbol = ip.symbol;
        description = ip.description;
        localSymbol = ip.localSymbol;
        localDescription = ip.localDescription;
        country = ip.country;
        opol = ip.opol;
        exchangeData = ip.exchangeData;
        exchanges = ip.exchanges;
        currency = ip.currency;
        baseCurrency = ip.baseCurrency;
        cfi = ip.cfi;
        isin = ip.isin;
        sedol = ip.sedol;
        cusip = ip.cusip;
        icb = ip.icb;
        sic = ip.sic;
        multiplier = ip.multiplier;
        product = ip.product;
        underlying = ip.underlying;
        spc = ip.spc;
        additionalUnderlyings = ip.additionalUnderlyings;
        mmy = ip.mmy;
        expiration = ip.expiration;
        lastTrade = ip.lastTrade;
        strike = ip.strike;
        optionType = ip.optionType;
        expirationStyle = ip.expirationStyle;
        settlementStyle = ip.settlementStyle;
        priceIncrements = ip.priceIncrements;
        tradingHours = ip.tradingHours;

        String[] customFields = ip.customFields; // Atomic read.
        this.customFields = customFields == null || ArrayMap.isEmpty(customFields) ? null : customFields.clone();
    }

    /**
     * Returns type of instrument.
     * It takes precedence in conflict cases with other fields.
     * It is a mandatory field. It may not be empty.
     * Example: "STOCK", "FUTURE", "OPTION".
     * @return type of instrument.
     */
    public String getType() {
        return type;
    }

    /**
     * Changes type of instrument.
     * It takes precedence in conflict cases with other fields.
     * It is a mandatory field. It may not be empty.
     * Example: "STOCK", "FUTURE", "OPTION".
     * @param type type of instrument.
     */
    public void setType(String type) {
        this.type = type == null || type.isEmpty() ? "" : type;
    }

    /**
     * Returns identifier of instrument,
     * preferable an international one in Latin alphabet.
     * It is a mandatory field. It may not be empty.
     * Example: "GOOG", "/YGM9", ".ZYEAD".
     * @return identifier of instrument.
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Changes identifier of instrument,
     * preferable an international one in Latin alphabet.
     * It is a mandatory field. It may not be empty.
     * Example: "GOOG", "/YGM9", ".ZYEAD".
     * @param symbol identifier of instrument.
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol == null || symbol.isEmpty() ? "" : symbol;
    }

    /**
     * Returns description of instrument,
     * preferable an international one in Latin alphabet.
     * Example: "Google Inc.", "Mini Gold Futures,Jun-2009,ETH".
     * @return  description of instrument.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Changes description of instrument,
     * preferable an international one in Latin alphabet.
     * Example: "Google Inc.", "Mini Gold Futures,Jun-2009,ETH".
     * @param description  description of instrument.
     */
    public void setDescription(String description) {
        this.description = description == null || description.isEmpty() ? "" : description;
    }

    /**
     * Returns identifier of instrument in national language.
     * It shall be empty if same as {@link #getSymbol symbol}.
     * @return identifier of instrument in national language.
     */
    public String getLocalSymbol() {
        return localSymbol;
    }

    /**
     * Changes identifier of instrument in national language.
     * It shall be empty if same as {@link #setSymbol symbol}.
     * @param localSymbol identifier of instrument in national language.
     */
    public void setLocalSymbol(String localSymbol) {
        this.localSymbol = localSymbol == null || localSymbol.isEmpty() ? "" : localSymbol;
    }

    /**
     * Returns description of instrument in national language.
     * It shall be empty if same as {@link #getDescription description}.
     * @return description of instrument in national language.
     */
    public String getLocalDescription() {
        return localDescription;
    }

    /**
     * Changes description of instrument in national language.
     * It shall be empty if same as {@link #getDescription description}.
     * @param localDescription description of instrument in national language.
     */
    public void setLocalDescription(String localDescription) {
        this.localDescription = localDescription == null || localDescription.isEmpty() ? "" : localDescription;
    }

    /**
     * Returns country of origin (incorporation) of corresponding company or parent entity.
     * It shall use two-letter country code from ISO 3166-1 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_3166-1">ISO 3166-1 on Wikipedia</a>.
     * Example: "US", "RU".
     * @return country of origin (incorporation) of corresponding company or parent entity.
     */
    public String getCountry() {
        return country;
    }

    /**
     * Changes country of origin (incorporation) of corresponding company or parent entity.
     * It shall use two-letter country code from ISO 3166-1 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_3166-1">ISO 3166-1 on Wikipedia</a>.
     * Example: "US", "RU".
     * @param country country of origin (incorporation) of corresponding company or parent entity.
     */
    public void setCountry(String country) {
        this.country = country == null || country.isEmpty() ? "" : country;
    }

    /**
     * Returns official Place Of Listing, the organization that have listed this instrument.
     * Instruments with multiple listings shall use separate profiles for each listing.
     * It shall use Market Identifier Code (MIC) from ISO 10383 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_10383">ISO 10383 on Wikipedia</a>
     * or <a href="http://www.iso15022.org/MIC/homepageMIC.htm">MIC homepage</a>.
     * Example: "XNAS", "RTSX"/
     * @return official Place Of Listing, the organization that have listed this instrument.
     */
    public String getOPOL() {
        return opol;
    }

    /**
     * Changes official Place Of Listing, the organization that have listed this instrument.
     * Instruments with multiple listings shall use separate profiles for each listing.
     * It shall use Market Identifier Code (MIC) from ISO 10383 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_10383">ISO 10383 on Wikipedia</a>
     * or <a href="http://www.iso15022.org/MIC/homepageMIC.htm">MIC homepage</a>.
     * Example: "XNAS", "RTSX"/
     * @param opol official Place Of Listing, the organization that have listed this instrument.
     */
    public void setOPOL(String opol) {
        this.opol = opol == null || opol.isEmpty() ? "" : opol;
    }

    /**
     * Returns exchange-specific data required to properly identify instrument when communicating with exchange.
     * It uses exchange-specific format.
     * @return exchange-specific data required to properly identify instrument when communicating with exchange.
     */
    public String getExchangeData() {
        return exchangeData;
    }

    /**
     * Changes exchange-specific data required to properly identify instrument when communicating with exchange.
     * It uses exchange-specific format.
     * @param exchangeData exchange-specific data required to properly identify instrument when communicating with exchange.
     */
    public void setExchangeData(String exchangeData) {
        this.exchangeData = exchangeData == null || exchangeData.isEmpty() ? "" : exchangeData;
    }

    /**
     * Returns list of exchanges where instrument is quoted or traded.
     * Its shall use the following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;IST&gt; ::= &lt;MIC&gt; | &lt;MIC&gt; &lt;semicolon&gt; </pre>
     * &lt;LIST&gt; the list shall be sorted by MIC.
     * Example: "ARCX;CBSX ;XNAS;XNYS".
     * @return list of exchanges where instrument is quoted or traded.
     */
    public String getExchanges() {
        return exchanges;
    }

    /**
     * Changes list of exchanges where instrument is quoted or traded.
     * It shall use the following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;IST&gt; ::= &lt;MIC&gt; | &lt;MIC&gt; &lt;semicolon&gt; </pre>
     * &lt;LIST&gt; the list shall be sorted by MIC.
     * Example: "ARCX;CBSX ;XNAS;XNYS".
     * @param exchanges list of exchanges where instrument is quoted or traded.
     */
    public void setExchanges(String exchanges) {
        this.exchanges = exchanges == null || exchanges.isEmpty() ? "" : exchanges;
    }

    /**
     * Returns currency of quotation, pricing and trading.
     * It shall use three-letter currency code from ISO 4217 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_4217">ISO 4217 on Wikipedia</a>.
     * Example: "USD", "RUB".
     * @return currency of quotation, pricing and trading.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Changes currency of quotation, pricing and trading.
     * It shall use three-letter currency code from ISO 4217 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_4217">ISO 4217 on Wikipedia</a>.
     * Example: "USD", "RUB".
     * @param currency currency of quotation, pricing and trading.
     */
    public void setCurrency(String currency) {
        this.currency = currency == null || currency.isEmpty() ? "" : currency;
    }

    /**
     * Returns base currency of currency pair (FOREX instruments).
     * It shall use three-letter currency code similarly to {@link #getCurrency currency}.
     * @return base currency of currency pair (FOREX instruments).
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Changes base currency of currency pair (FOREX instruments).
     * It shall use three-letter currency code similarly to {@link #setCurrency currency}.
     * @param baseCurrency base currency of currency pair (FOREX instruments).
     */
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency == null || baseCurrency.isEmpty() ? "" : baseCurrency;
    }

    /**
     * Returns Classification of Financial Instruments code.
     * It is a mandatory field for OPTION instruments as it is the only way to distinguish Call/Put type,
     * American/European exercise, Cash/Physical delivery.
     * It shall use six-letter CFI code from ISO 10962 standard.
     * It is allowed to use 'X' extensively and to omit trailing letters (assumed to be 'X').
     * See <a href="http://en.wikipedia.org/wiki/ISO_10962">ISO 10962 on Wikipedia</a>.
     * Example: "ESNTPB", "ESXXXX", "ES" , "OPASPS".
     * @return CFI code.
     */
    public String getCFI() {
        return cfi;
    }

    /**
     * Changes Classification of Financial Instruments code.
     * It is a mandatory field for OPTION instruments as it is the only way to distinguish Call/Put type,
     * American/European exercise, Cash/Physical delivery.
     * It shall use six-letter CFI code from ISO 10962 standard.
     * It is allowed to use 'X' extensively and to omit trailing letters (assumed to be 'X').
     * See <a href="http://en.wikipedia.org/wiki/ISO_10962">ISO 10962 on Wikipedia</a>.
     * Example: "ESNTPB", "ESXXXX", "ES" , "OPASPS".
     * @param cfi CFI code.
     */
    public void setCFI(String cfi) {
        this.cfi = cfi == null || cfi.isEmpty() ? "" : cfi;
    }

    /**
     * Returns International Securities Identifying Number.
     * It shall use twelve-letter code from ISO 6166 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_6166">ISO 6166 on Wikipedia</a>
     * or <a href="http://en.wikipedia.org/wiki/International_Securities_Identifying_Number">ISIN on Wikipedia</a>.
     * Example: "DE0007100000", "US38259P5089".
     * @return International Securities Identifying Number.
     */
    public String getISIN() {
        return isin;
    }

    /**
     * Changes International Securities Identifying Number.
     * It shall use twelve-letter code from ISO 6166 standard.
     * See <a href="http://en.wikipedia.org/wiki/ISO_6166">ISO 6166 on Wikipedia</a>
     * or <a href="http://en.wikipedia.org/wiki/International_Securities_Identifying_Number">ISIN on Wikipedia</a>.
     * Example: "DE0007100000", "US38259P5089".
     * @param isin International Securities Identifying Number.
     */
    public void setISIN(String isin) {
        this.isin = isin == null || isin.isEmpty() ? "" : isin;
    }

    /**
     * Returns Stock Exchange Daily Official List.
     * It shall use seven-letter code assigned by London Stock Exchange.
     * See <a href="http://en.wikipedia.org/wiki/SEDOL">SEDOL on Wikipedia</a> or
     * <a href="http://www.londonstockexchange.com/en-gb/products/informationproducts/sedol/">SEDOL on LSE</a>.
     * Example: "2310967", "5766857".
     * @return Stock Exchange Daily Official List.
     */
    public String getSEDOL() {
        return sedol;
    }

    /**
     * Changes Stock Exchange Daily Official List.
     * It shall use seven-letter code assigned by London Stock Exchange.
     * See <a href="http://en.wikipedia.org/wiki/SEDOL">SEDOL on Wikipedia</a> or
     * <a href="http://www.londonstockexchange.com/en-gb/products/informationproducts/sedol/">SEDOL on LSE</a>.
     * Example: "2310967", "5766857".
     * @param sedol Stock Exchange Daily Official List.
     */
    public void setSEDOL(String sedol) {
        this.sedol = sedol == null || sedol.isEmpty() ? "" : sedol;
    }

    /**
     * Returns Committee on Uniform Security Identification Procedures code.
     * It shall use nine-letter code assigned by CUSIP Services Bureau.
     * See <a href="http://en.wikipedia.org/wiki/CUSIP">CUSIP on Wikipedia</a>.
     * Example: "38259P508".
     * @return CUSIP code.
     */
    public String getCUSIP() {
        return cusip;
    }

    /**
     * Changes Committee on Uniform Security Identification Procedures code.
     * It shall use nine-letter code assigned by CUSIP Services Bureau.
     * See <a href="http://en.wikipedia.org/wiki/CUSIP">CUSIP on Wikipedia</a>.
     * Example: "38259P508".
     * @param cusip CUSIP code.
     */
    public void setCUSIP(String cusip) {
        this.cusip = cusip == null || cusip.isEmpty() ? "" : cusip;
    }

    /**
     * Returns Industry Classification Benchmark.
     * It shall use four-digit number from ICB catalog.
     * See <a href="http://en.wikipedia.org/wiki/Industry_Classification_Benchmark">ICB on Wikipedia</a>
     * or <a href="http://www.icbenchmark.com/">ICB homepage</a>.
     * Example: "9535".
     * @return Industry Classification Benchmark.
     */
    public int getICB() {
        return icb;
    }

    /**
     * Changes Industry Classification Benchmark.
     * It shall use four-digit number from ICB catalog.
     * See <a href="http://en.wikipedia.org/wiki/Industry_Classification_Benchmark">ICB on Wikipedia</a>
     * or <a href="http://www.icbenchmark.com/">ICB homepage</a>.
     * Example: "9535".
     * @param icb Industry Classification Benchmark.
     */
    public void setICB(int icb) {
        this.icb = icb;
    }

    /**
     * Returns Standard Industrial Classification.
     * It shall use four-digit number from SIC catalog.
     * See <a href="http://en.wikipedia.org/wiki/Standard_Industrial_Classification">SIC on Wikipedia</a>
     * or <a href="https://www.osha.gov/pls/imis/sic_manual.html">SIC structure</a>.
     * Example: "7371".
     * @return Standard Industrial Classification.
     */
    public int getSIC() {
        return sic;
    }

    /**
     * Changes Standard Industrial Classification.
     * It shall use four-digit number from SIC catalog.
     * See <a href="http://en.wikipedia.org/wiki/Standard_Industrial_Classification">SIC on Wikipedia</a>
     * or <a href="https://www.osha.gov/pls/imis/sic_manual.html">SIC structure</a>.
     * Example: "7371".
     * @param sic Standard Industrial Classification.
     */
    public void setSIC(int sic) {
        this.sic = sic;
    }

    /**
     * Returns market value multiplier.
     * Example: 100, 33.2.
     * @return market value multiplier.
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Changes market value multiplier.
     * Example: 100, 33.2.
     * @param multiplier market value multiplier.
     */
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * Returns product for futures and options on futures (underlying asset name).
     * Example: "/YG".
     * @return product for futures and options on futures (underlying asset name).
     */
    public String getProduct() {
        return product;
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
     * Returns primary underlying symbol for options.
     * Example: "C", "/YGM9"
     * @return primary underlying symbol for options.
     */
    public String getUnderlying() {
        return underlying;
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
     * Returns shares per contract for options.
     * Example: 1, 100.
     * @return shares per contract for options.
     */
    public double getSPC() {
        return spc;
    }

    /**
     * Changes shares per contract for options.
     * Example: 1, 100.
     * @param spc shares per contract for options.
     */
    public void setSPC(double spc) {
        this.spc = spc;
    }

    /**
     * Returns additional underlyings for options, including additional cash.
     * It shall use following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;LIST&gt; ::= &lt;AU&gt; | &lt;AU&gt; &lt;semicolon&gt; &lt;space&gt; &lt;LIST&gt;
     *     &lt;AU&gt; ::= &lt;UNDERLYING&gt; &lt;space&gt; &lt;SPC&gt; </pre>
     * the list shall be sorted by &lt;UNDERLYING&gt;.
     * Example: "SE 50", "FIS 53; US$ 45.46".
     * @return additional underlyings for options, including additional cash.
     */
    public String getAdditionalUnderlyings() {
        return additionalUnderlyings;
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
        this.additionalUnderlyings = additionalUnderlyings == null || additionalUnderlyings.isEmpty() ? "" : additionalUnderlyings;
    }

    /**
     * Returns maturity month-year as provided for corresponding FIX tag (200).
     * It can use several different formats depending on data source:
     * <ul>
     * <li>YYYYMM – if only year and month are specified
     * <li>YYYYMMDD – if full date is specified
     * <li>YYYYMMwN – if week number (within a month) is specified
     * </ul>
     * @return  maturity month-year as provided for corresponding FIX tag (200).
     */
    public String getMMY() {
        return mmy;
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
        this.mmy = mmy == null || mmy.isEmpty() ? "" : mmy;
    }

    /**
     * Returns day id of expiration.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090117).
     * @return day id of expiration.
     */
    public int getExpiration() {
        return expiration;
    }

    /**
     * Changes day id of expiration.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090117).
     * @param expiration day id of expiration.
     */
    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    /**
     * Returns day id of last trading day.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090116).
     * @return day id of last trading day.
     */
    public int getLastTrade() {
        return lastTrade;
    }

    /**
     * Changes day id of last trading day.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090116).
     * @param lastTrade day id of last trading day.
     */
    public void setLastTrade(int lastTrade) {
        this.lastTrade = lastTrade;
    }

    /**
     * Returns strike price for options.
     * Example: 80, 22.5.
     * @return strike price for options.
     */
    public double getStrike() {
        return strike;
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
     * Returns type of option.
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
     * @return type of option.
     */
    public String getOptionType() {
        return optionType;
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
        this.optionType = optionType == null || optionType.isEmpty() ? "" : optionType;
    }

    /**
     * Returns expiration cycle style, such as "Weeklys", "Quarterlys".
     * @return expiration cycle style.
     */
    public String getExpirationStyle() {
        return expirationStyle;
    }

    /**
     * Returns expiration cycle style, such as "Weeklys", "Quarterlys".
     * @param expirationStyle expiration cycle style.
     */
    public void setExpirationStyle(String expirationStyle) {
        this.expirationStyle = expirationStyle == null || expirationStyle.isEmpty() ? "" : expirationStyle;
    }

    /**
     * Returns settlement price determination style, such as "Open", "Close".
     * @return settlement price determination style.
     */
    public String getSettlementStyle() {
        return settlementStyle;
    }

    /**
     * Changes settlement price determination style, such as "Open", "Close".
     * @param settlementStyle settlement price determination style.
     */
    public void setSettlementStyle(String settlementStyle) {
        this.settlementStyle = settlementStyle == null || settlementStyle.isEmpty() ? "" : settlementStyle;
    }

    /**
     * Returns minimum allowed price increments with corresponding price ranges.
     * It shall use following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;LIST&gt; ::= &lt;INCREMENT&gt; | &lt;RANGE&gt; &lt;semicolon&gt; &lt;space&gt; &lt;LIST&gt;
     *     &lt;RANGE&gt; ::= &lt;INCREMENT&gt; &lt;space&gt; &lt;UPPER_LIMIT&gt; </pre>
     * the list shall be sorted by &lt;UPPER_LIMIT&gt;.
     * Example: "0.25", "0.01 3; 0.05".
     * @return minimum allowed price increments with corresponding price ranges.
     */
    public String getPriceIncrements() {
        return priceIncrements;
    }

    /**
     * Changes minimum allowed price increments with corresponding price ranges.
     * It shall use following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;LIST&gt; ::= &lt;INCREMENT&gt; | &lt;RANGE&gt; &lt;semicolon&gt; &lt;space&gt; &lt;LIST&gt;
     *     &lt;RANGE&gt; ::= &lt;INCREMENT&gt; &lt;space&gt; &lt;UPPER_LIMIT&gt; </pre>
     * the list shall be sorted by &lt;UPPER_LIMIT&gt;.
     * Example: "0.25", "0.01 3; 0.05".
     * @param priceIncrements minimum allowed price increments with corresponding price ranges.
     */
    public void setPriceIncrements(String priceIncrements) {
        this.priceIncrements = priceIncrements == null || priceIncrements.isEmpty() ? "" : priceIncrements;
    }

    /**
     * Returns trading hours specification.
     * See {@link Schedule#getInstance(String)}.
     * @return trading hours specification.
     */
    public String getTradingHours() {
        return tradingHours;
    }

    /**
     * Changes trading hours specification.
     * See {@link Schedule#getInstance(String)}.
     * @param tradingHours trading hours specification.
     */
    public void setTradingHours(String tradingHours) {
        this.tradingHours = tradingHours == null || tradingHours.isEmpty() ? "" : tradingHours;
    }

    /**
     * Returns custom field value with a specified name.
     * @param name name of custom field.
     * @return custom field value with a specified name.
     */
    private String getCustomField(String name) {
        String[] customFields = this.customFields; // Atomic read.
        return customFields == null ? null : ArrayMap.get(customFields, name);
    }

    /**
     * Changes custom field value with a specified name.
     * @param name name of custom field.
     * @param value custom field value.
     */
    private void setCustomField(String name, String value) {
        String[] customFields = this.customFields; // Atomic read.
        if (!value.isEmpty())
            this.customFields = ArrayMap.put(customFields == null ? new String[4] : customFields, name, value);
        else if (customFields != null)
            this.customFields = ArrayMap.putIfKeyPresent(customFields, name, value);
    }

    /**
     * Returns field value with a specified name.
     * @param name name of field.
     * @return field value.
     */
    public String getField(String name) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            return ipf.getField(this);
        String value = getCustomField(name);
        return value == null ? "" : value;
    }

    /**
     * Changes field value with a specified name.
     * @param name name of field.
     * @param value field value.
     */
    public void setField(String name, String value) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            ipf.setField(this, value);
        else
            setCustomField(name, value == null || value.isEmpty() ? "" : value);
    }

    /**
     * Returns numeric field value with a specified name.
     * @param name name of field.
     * @return field value.
     */
    public double getNumericField(String name) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            return ipf.getNumericField(this);
        String value = getCustomField(name);
        return value == null || value.isEmpty() ? 0 :
            value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-' ? InstrumentProfileField.parseDate(value) :
            InstrumentProfileField.parseNumber(value);
    }

    /**
     * Changes numeric field value with a specified name.
     * @param name name of field.
     * @param value field value.
     */
    public void setNumericField(String name, double value) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            ipf.setNumericField(this, value);
        else
            setCustomField(name, InstrumentProfileField.formatNumber(value));
    }

    /**
     * Returns day id value for a date field with a specified name.
     * @param name name of field.
     * @return day id value.
     */
    public int getDateField(String name) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            return (int) ipf.getNumericField(this);
        String value = getCustomField(name);
        return value == null || value.isEmpty() ? 0 : InstrumentProfileField.parseDate(value);
    }

    /**
     * Changes day id value for a date field with a specified name.
     * @param name name of field.
     * @param value day id value.
     */
    public void setDateField(String name, int value) {
        InstrumentProfileField ipf = InstrumentProfileField.find(name);
        if (ipf != null)
            ipf.setNumericField(this, value);
        else
            setCustomField(name, InstrumentProfileField.formatDate(value));
    }

    /**
     * Adds names of non-empty custom fields to the specified collection.
     * @return {@code true} if {@code targetFieldNames} changed as a result of the call
     */
    public boolean addNonEmptyCustomFieldNames(Collection<? super String> targetFieldNames) {
        boolean updated = false;
        String[] customFields = this.customFields; // Atomic read.
        if (customFields != null) {
            for (int i = customFields.length & ~1; (i -= 2) >= 0; ) {
                String name = customFields[i]; // Atomic read.
                String value = customFields[i + 1]; // Atomic read.
                if (name != null && value != null && !value.isEmpty()) {
                    if (targetFieldNames.add(name))
                        updated = true;
                }
            }
        }
        return updated;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     *         argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstrumentProfile)) return false;
        InstrumentProfile that = (InstrumentProfile) o;
        if (!type.equals(that.type)) return false;
        if (!symbol.equals(that.symbol)) return false;
        if (!description.equals(that.description)) return false;
        if (!localSymbol.equals(that.localSymbol)) return false;
        if (!localDescription.equals(that.localDescription)) return false;
        if (!country.equals(that.country)) return false;
        if (!opol.equals(that.opol)) return false;
        if (!exchangeData.equals(that.exchangeData)) return false;
        if (!exchanges.equals(that.exchanges)) return false;
        if (!currency.equals(that.currency)) return false;
        if (!baseCurrency.equals(that.baseCurrency)) return false;
        if (!cfi.equals(that.cfi)) return false;
        if (!isin.equals(that.isin)) return false;
        if (!sedol.equals(that.sedol)) return false;
        if (!cusip.equals(that.cusip)) return false;
        if (icb != that.icb) return false;
        if (sic != that.sic) return false;
        if (Double.compare(that.multiplier, multiplier) != 0) return false;
        if (!product.equals(that.product)) return false;
        if (!underlying.equals(that.underlying)) return false;
        if (Double.compare(that.spc, spc) != 0) return false;
        if (!additionalUnderlyings.equals(that.additionalUnderlyings)) return false;
        if (!mmy.equals(that.mmy)) return false;
        if (expiration != that.expiration) return false;
        if (lastTrade != that.lastTrade) return false;
        if (Double.compare(that.strike, strike) != 0) return false;
        if (!optionType.equals(that.optionType)) return false;
        if (!expirationStyle.equals(that.expirationStyle)) return false;
        if (!settlementStyle.equals(that.settlementStyle)) return false;
        if (!priceIncrements.equals(that.priceIncrements)) return false;
        if (!tradingHours.equals(that.tradingHours)) return false;
        return customEquals(customFields, that.customFields);
    }

    /**
     * Returns a hash code value for the object.
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = type.hashCode();
        result = 31 * result + symbol.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + localSymbol.hashCode();
        result = 31 * result + localDescription.hashCode();
        result = 31 * result + country.hashCode();
        result = 31 * result + opol.hashCode();
        result = 31 * result + exchangeData.hashCode();
        result = 31 * result + exchanges.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + baseCurrency.hashCode();
        result = 31 * result + cfi.hashCode();
        result = 31 * result + isin.hashCode();
        result = 31 * result + sedol.hashCode();
        result = 31 * result + cusip.hashCode();
        result = 31 * result + icb;
        result = 31 * result + sic;
        temp = Double.doubleToLongBits(multiplier);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + product.hashCode();
        result = 31 * result + underlying.hashCode();
        temp = Double.doubleToLongBits(spc);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + additionalUnderlyings.hashCode();
        result = 31 * result + mmy.hashCode();
        result = 31 * result + expiration;
        result = 31 * result + lastTrade;
        temp = Double.doubleToLongBits(strike);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + optionType.hashCode();
        result = 31 * result + expirationStyle.hashCode();
        result = 31 * result + settlementStyle.hashCode();
        result = 31 * result + priceIncrements.hashCode();
        result = 31 * result + tradingHours.hashCode();
        return 31 * result + customHashCode(customFields);
    }

    private static int customHashCode(String[] a) {
        if (a == null)
            return 0;
        int hash = 0;
        for (int i = a.length & ~1; (i -= 2) >= 0;) {
            String key = a[i];
            String value = a[i + 1];
            if (key != null && value != null && !value.isEmpty())
                hash += key.hashCode() ^ value.hashCode();
        }
        return hash;
    }

    private static boolean customEquals(String[] a, String[] b) {
        return customContainsAll(b, a) && customContainsAll(a, b);
    }

    private static boolean customContainsAll(String[] a, String[] b) {
        if (b == null)
            return true;
        for (int i = b.length & ~1; (i -= 2) >= 0;) {
            String key = b[i];
            String value = b[i + 1];
            if (key != null && value != null && !value.isEmpty())
                if (a == null || !value.equals(ArrayMap.get(a, key)))
                    return false;
        }
        return true;
    }


    /**
     * Compares this profile with the specified profile for order. Returns a negative integer, zero,
     * or a positive integer as this object is less than, equal to, or greater than the specified object.
     * <p>
     * The natural ordering implied by this method is designed for convenient data representation
     * in a file and shall not be used for business purposes.
     */
    @Override
    public int compareTo(InstrumentProfile ip) {
        int i = InstrumentProfileType.compareTypes(type, ip.type);
        if (i != 0)
            return i;
        i = product.compareTo(ip.product);
        if (i != 0)
            return i;
        i = underlying.compareTo(ip.underlying);
        if (i != 0)
            return i;
        i = lastTrade > ip.lastTrade ? 1 : lastTrade < ip.lastTrade ? -1 : 0;
        if (i != 0)
            return i;
        i = strike > ip.strike ? 1 : strike < ip.strike ? -1 : 0;
        if (i != 0)
            return i;
        i = symbol.compareTo(ip.symbol);
        if (i != 0)
            return i;
        return 0;
    }

    /**
     * Returns a string representation of the instrument profile.
     * @return string representation of the instrument profile.
     */
    public String toString() {
        return type + " " + symbol;
    }
}
