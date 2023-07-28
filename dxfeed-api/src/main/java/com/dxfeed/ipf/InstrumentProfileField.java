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
package com.dxfeed.ipf;

import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeUtil;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * Defines standard fields of {@link InstrumentProfile} and provides data access methods.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public enum InstrumentProfileField {
    TYPE,
    SYMBOL,
    DESCRIPTION,
    LOCAL_SYMBOL,
    LOCAL_DESCRIPTION,
    COUNTRY,
    OPOL,
    EXCHANGE_DATA,
    EXCHANGES,
    CURRENCY,
    BASE_CURRENCY,
    CFI,
    ISIN,
    SEDOL,
    CUSIP,
    ICB(Double.class),
    SIC(Double.class),
    MULTIPLIER(Double.class),
    PRODUCT,
    UNDERLYING,
    SPC(Double.class),
    ADDITIONAL_UNDERLYINGS,
    MMY,
    EXPIRATION(Date.class),
    LAST_TRADE(Date.class),
    STRIKE(Double.class),
    OPTION_TYPE,
    EXPIRATION_STYLE,
    SETTLEMENT_STYLE,
    PRICE_INCREMENTS,
    TRADING_HOURS;

    private final Class<?> type;
    private final boolean numericField;

    private InstrumentProfileField() {
        this(String.class);
    }

    private InstrumentProfileField(Class<?> type) {
        this.type = type;
        numericField = type != String.class;
    }

    private static final HashMap<String, InstrumentProfileField> MAP = new HashMap<>();
    static {
        for (InstrumentProfileField ipf : values())
            MAP.put(ipf.name(), ipf);
    }

    /**
     * Returns field for specified name or <b>null</b> if field is not found.
     * The difference from {@link #valueOf} method is that later method throws exception for unknown fields.
     */
    public static InstrumentProfileField find(String name) {
        return MAP.get(name);
    }

    /**
     * Returns value of this field for specified profile in textual representation.
     */
    public String getField(InstrumentProfile ip) {
        switch (this) {
        case TYPE: return ip.getType();
        case SYMBOL: return ip.getSymbol();
        case DESCRIPTION: return ip.getDescription();
        case LOCAL_SYMBOL: return ip.getLocalSymbol();
        case LOCAL_DESCRIPTION: return ip.getLocalDescription();
        case COUNTRY: return ip.getCountry();
        case OPOL: return ip.getOPOL();
        case EXCHANGE_DATA: return ip.getExchangeData();
        case EXCHANGES: return ip.getExchanges();
        case CURRENCY: return ip.getCurrency();
        case BASE_CURRENCY: return ip.getBaseCurrency();
        case CFI: return ip.getCFI();
        case ISIN: return ip.getISIN();
        case SEDOL: return ip.getSEDOL();
        case CUSIP: return ip.getCUSIP();
        case ICB: return formatNumber(ip.getICB());
        case SIC: return formatNumber(ip.getSIC());
        case MULTIPLIER: return formatNumber(ip.getMultiplier());
        case PRODUCT: return ip.getProduct();
        case UNDERLYING: return ip.getUnderlying();
        case SPC: return formatNumber(ip.getSPC());
        case ADDITIONAL_UNDERLYINGS: return ip.getAdditionalUnderlyings();
        case MMY: return ip.getMMY();
        case EXPIRATION: return formatDate(ip.getExpiration());
        case LAST_TRADE: return formatDate(ip.getLastTrade());
        case STRIKE: return formatNumber(ip.getStrike());
        case OPTION_TYPE: return ip.getOptionType();
        case EXPIRATION_STYLE: return ip.getExpirationStyle();
        case SETTLEMENT_STYLE: return ip.getSettlementStyle();
        case PRICE_INCREMENTS: return ip.getPriceIncrements();
        case TRADING_HOURS: return ip.getTradingHours();
        }
        throw new InternalError("cannot process field " + this);
    }

    /**
     * Sets value of this field (in textual representation) to specified profile.
     *
     * @throws IllegalArgumentException if text uses wrong format or contains invalid values
     */
    public void setField(InstrumentProfile ip, String value) {
        switch (this) {
        case TYPE: ip.setType(value); return;
        case SYMBOL: ip.setSymbol(value); return;
        case DESCRIPTION: ip.setDescription(value); return;
        case LOCAL_SYMBOL: ip.setLocalSymbol(value); return;
        case LOCAL_DESCRIPTION: ip.setLocalDescription(value); return;
        case COUNTRY: ip.setCountry(value); return;
        case OPOL: ip.setOPOL(value); return;
        case EXCHANGE_DATA: ip.setExchangeData(value); return;
        case EXCHANGES: ip.setExchanges(value); return;
        case CURRENCY: ip.setCurrency(value); return;
        case BASE_CURRENCY: ip.setBaseCurrency(value); return;
        case CFI: ip.setCFI(value); return;
        case ISIN: ip.setISIN(value); return;
        case SEDOL: ip.setSEDOL(value); return;
        case CUSIP: ip.setCUSIP(value); return;
        case ICB: ip.setICB((int) parseNumber(value)); return;
        case SIC: ip.setSIC((int) parseNumber(value)); return;
        case MULTIPLIER: ip.setMultiplier(parseNumber(value)); return;
        case PRODUCT: ip.setProduct(value); return;
        case UNDERLYING: ip.setUnderlying(value); return;
        case SPC: ip.setSPC(parseNumber(value)); return;
        case ADDITIONAL_UNDERLYINGS: ip.setAdditionalUnderlyings(value); return;
        case MMY: ip.setMMY(value); return;
        case EXPIRATION: ip.setExpiration(parseDate(value)); return;
        case LAST_TRADE: ip.setLastTrade(parseDate(value)); return;
        case STRIKE: ip.setStrike(parseNumber(value)); return;
        case OPTION_TYPE: ip.setOptionType(value); return;
        case EXPIRATION_STYLE: ip.setExpirationStyle(value); return;
        case SETTLEMENT_STYLE: ip.setSettlementStyle(value); return;
        case PRICE_INCREMENTS: ip.setPriceIncrements(value); return;
        case TRADING_HOURS: ip.setTradingHours(value); return;
        }
        throw new InternalError("cannot process field " + this);
    }

    /**
     * Returns type of this field.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Returns "true" if this field supports numeric representation of a value.
     */
    public boolean isNumericField() {
        return numericField;
    }

    /**
     * Returns value of this field for specified profile in numeric representation.
     *
     * @throws IllegalArgumentException if this field has no numeric representation
     */
    public double getNumericField(InstrumentProfile ip) {
        switch (this) {
        case ICB: return ip.getICB();
        case SIC: return ip.getSIC();
        case MULTIPLIER: return ip.getMultiplier();
        case SPC: return ip.getSPC();
        case EXPIRATION: return ip.getExpiration();
        case LAST_TRADE: return ip.getLastTrade();
        case STRIKE: return ip.getStrike();
        }
        throw new IllegalArgumentException("textual field " + this);
    }

    /**
     * Sets value of this field (in numeric representation) to specified profile.
     *
     * @throws IllegalArgumentException if this field has no numeric representation
     */
    public void setNumericField(InstrumentProfile ip, double value) {
        switch (this) {
        case ICB: ip.setICB((int) value); return;
        case SIC: ip.setSIC((int) value); return;
        case MULTIPLIER: ip.setMultiplier(value); return;
        case SPC: ip.setSPC(value); return;
        case EXPIRATION: ip.setExpiration((int) value); return;
        case LAST_TRADE: ip.setLastTrade((int) value); return;
        case STRIKE: ip.setStrike(value); return;
        }
        throw new IllegalArgumentException("textual field " + this);
    }


    // ========== Internal Implementation ==========

    private static final ThreadLocal<NumberFormat> NUMBER_FORMATTER = new ThreadLocal<>();
    private static final ThreadLocal<DateFormat> DATE_FORMATTER = new ThreadLocal<>();

    private static class Entry {
        final long binary;
        final String text;

        Entry(long binary, String text) {
            this.binary = binary;
            this.text = text;
        }
    }

    private static final SynchronizedIndexedSet<Long, Entry> FORMATTED_NUMBERS = SynchronizedIndexedSet.createLong(entry -> entry.binary);
    private static final SynchronizedIndexedSet<Long, Entry> FORMATTED_DATES = SynchronizedIndexedSet.createLong(entry -> entry.binary);
    private static final SynchronizedIndexedSet<String, Entry> PARSED_NUMBERS = SynchronizedIndexedSet.create((Entry entry) -> entry.text);
    private static final SynchronizedIndexedSet<String, Entry> PARSED_DATES = SynchronizedIndexedSet.create((Entry entry) -> entry.text);

    private static final int CACHE_SIZE = SystemProperties.getIntProperty(InstrumentProfileField.class, "cacheSize", 25000, 100, 100000);

    private static <T> T doCache(SynchronizedIndexedSet<?, T> cache, T value) {
        if (cache.size() > CACHE_SIZE)
            cache.clear();
        return cache.putIfAbsentAndGet(value);
    }

    public static String formatNumber(double d) {
        if (d == 0)
            return "";
        long binary = Double.doubleToLongBits(d);
        Entry cached = FORMATTED_NUMBERS.getByKey(binary);
        if (cached == null)
            cached = doCache(FORMATTED_NUMBERS, new Entry(binary, formatNumberImpl(d)));
        return cached.text;
    }

    private static String formatNumberImpl(double d) {
        if (d == (double) (int) d)
            return Integer.toString((int) d);
        if (d == (double) (long) d)
            return Long.toString((long) d);
        double ad = Math.abs(d);
        if (ad > 1e-9 && ad < 1e12) {
            NumberFormat nf = NUMBER_FORMATTER.get();
            if (nf == null) {
                nf = NumberFormat.getInstance(Locale.US);
                nf.setMaximumFractionDigits(20);
                nf.setGroupingUsed(false);
                NUMBER_FORMATTER.set(nf);
            }
            return nf.format(d);
        }
        return Double.toString(d);
    }

    public static double parseNumber(String s) {
        if (s == null || s.isEmpty())
            return 0;
        Entry cached = PARSED_NUMBERS.getByKey(s);
        if (cached == null)
            cached = doCache(PARSED_NUMBERS, new Entry(Double.doubleToLongBits(Double.parseDouble(s)), s));
        return Double.longBitsToDouble(cached.binary);
    }

    public static String formatDate(int d) {
        if (d == 0)
            return "";
        Entry cached = FORMATTED_DATES.getByKey(d);
        if (cached == null)
            cached = doCache(FORMATTED_DATES, new Entry(d, getDateFormat().format(new Date(d * TimeUtil.DAY))));
        return cached.text;
    }

    public static int parseDate(String s) {
        if (s == null || s.isEmpty())
            return 0;
        Entry cached = PARSED_DATES.getByKey(s);
        if (cached == null)
            try {
                cached = doCache(PARSED_DATES, new Entry(getDateFormat().parse(s).getTime() / TimeUtil.DAY, s));
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        return (int) cached.binary;
    }

    private static DateFormat getDateFormat() {
        DateFormat df = DATE_FORMATTER.get();
        if (df == null) {
            df = new SimpleDateFormat("yyyy-MM-dd");
            df.setTimeZone(TimeUtil.getTimeZoneGmt());
            DATE_FORMATTER.set(df);
        }
        return df;
    }
}
