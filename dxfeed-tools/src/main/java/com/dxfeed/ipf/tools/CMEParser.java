/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.tools;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.devexperts.util.StringCache;
import com.dxfeed.glossary.PriceIncrements;
import com.dxfeed.ipf.*;

/**
 * Parses CME MO.dat file with RLC MO messages (futures and options) and constructs product profiles.
 * <p>
 * Used exchanges as of May 2008:
 * <pre>
 * -MIC-   -ACRONYM-
 * XCBT    CBOT
 * XCEC    COMEX
 * XCME    CME
 * XKBT    KCBT
 * XMGE    MGE
 * XNYM    NYMEX
 * XOCH    OneChicago
 * </pre>
 */
public class CMEParser extends InstrumentProfileReader {
    public static List<InstrumentProfile> createProducts(List<InstrumentProfile> profiles) {
        Map<String, InstrumentProfile> products = new HashMap<String, InstrumentProfile>();
        List<InstrumentProfile> result = new ArrayList<InstrumentProfile>();
        for (InstrumentProfile ip : profiles) {
            if (ip.getType().equals(InstrumentProfileType.FUTURE.name()) && ip.getProduct().length() > 0) {
                InstrumentProfile product = products.get(ip.getProduct());
                if (product == null) {
                    product = new InstrumentProfile();
                    product.setType(InstrumentProfileType.PRODUCT.name());
                    product.setSymbol(ip.getProduct());
                    products.put(ip.getProduct(), product);
                    result.add(product);
                }
                mergeText(product, ip, InstrumentProfileField.DESCRIPTION);
                mergeText(product, ip, InstrumentProfileField.LOCAL_DESCRIPTION);
                mergeFirst(product, ip, InstrumentProfileField.COUNTRY);
                mergeFirst(product, ip, InstrumentProfileField.OPOL);
                mergeFirst(product, ip, InstrumentProfileField.EXCHANGES);
                mergeFirst(product, ip, InstrumentProfileField.CURRENCY);
                mergeFirst(product, ip, InstrumentProfileField.CFI);
                mergeFirst(product, ip, InstrumentProfileField.ICB);
                mergeFirst(product, ip, InstrumentProfileField.SIC);
                mergeFirst(product, ip, InstrumentProfileField.PRICE_INCREMENTS);
                mergeFirst(product, ip, InstrumentProfileField.TRADING_HOURS);
            }
        }
        return result;
    }

    private static void mergeText(InstrumentProfile target, InstrumentProfile source, InstrumentProfileField field) {
        String t = field.getField(target);
        String s = field.getField(source);
        if (t.isEmpty())
            field.setField(target, s);
        else if (s.length() != 0) {
            int n = Math.min(t.length(), s.length());
            for (int i = 0; i < n; i++)
                if (t.charAt(i) != s.charAt(i))
                    n = i;
            while (n > 0 && (t.charAt(n - 1) <= ' ' || t.charAt(n - 1) == ',' || t.charAt(n - 1) == ';' || t.charAt(n - 1) == '-'))
                n--;
            if (n > 0 && n < t.length())
                field.setField(target, t.substring(0, n));
        }
    }

    private static void mergeFirst(InstrumentProfile target, InstrumentProfile source, InstrumentProfileField field) {
        if (field.getField(target).isEmpty())
            field.setField(target, field.getField(source));
    }

    /**
     * Reads and returns instrument profiles from specified stream.
     *
     * @throws IOException  If an I/O error occurs
     */
    public List<InstrumentProfile> read(InputStream in) throws IOException {
        List<InstrumentProfile> profiles = new ArrayList<InstrumentProfile>();
        List<InstrumentProfile> options = new ArrayList<InstrumentProfile>();
        Map<String, InstrumentProfile> futureMap = new HashMap<String, InstrumentProfile>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            InstrumentProfile ip = parseLine(line);
            if (ip == null)
                continue;
            if (ip.getType().equals(InstrumentProfileType.FUTURE.name())) {
                profiles.add(ip);
                futureMap.put(getISIN(line), ip);
            } else if (ip.getType().equals(InstrumentProfileType.OPTION.name()))
                options.add(ip);
        }
        profiles.addAll(createProducts(profiles));
        // Update underlying data (it may be unavailable earlier during parsing).
        for (InstrumentProfile option : options) {
            InstrumentProfile future = futureMap.get(option.getUnderlying());
            if (future != null) {
                option.setMultiplier(future.getMultiplier());
                option.setProduct(future.getProduct());
                option.setUnderlying(future.getSymbol());
                option.setStrike(round(option.getStrike() / Double.parseDouble(future.getExchangeData().split(";")[2])));
                profiles.add(option);
            }
        }
        Collections.sort(profiles);
        return profiles;
    }

    private static final double[][] VTT = {
        {}, {5, 500, 10}, {0.5, 5, 1}, {1, 10, 2}, {5, 500, 25}, {1},
        {2}, {1}, {}, {}, {5, 300, 25}, {5, 300, 10}, {0.25, 5, 0.5}
    };

    private static InstrumentProfile parseLine(String line) {
        InstrumentProfile ip = new InstrumentProfile();
        double conversion = getConversionFactor(line);
        char type = getType(line);
        if (type == 'F') {
            ip.setType(InstrumentProfileType.FUTURE.name());
            ip.setSymbol(cached("/" + getInstrumentCode(line)));
            double m = round(getMonetaryValue(line) / getPriceFluctuation(line) * conversion);
            ip.setMultiplier(Double.isNaN(m) || Double.isInfinite(m) ? 0 : m);
            ip.setProduct(cached("/" + getProductCode(line)));
        } else if (type == 'C' || type == 'P' || type == 'X' || type == 'Y') {
            ip.setType(InstrumentProfileType.OPTION.name());
            ip.setCFI(type == 'C' ? "OCAFPX" : type == 'P' ? "OPAFPX" : type == 'X' ? "OCEFPX" : "OPEFPX");
            ip.setUnderlying(getUnderlyingISIN(line)); // Temporary, will be replaced with future symbol.
            ip.setSPC(1);
            ip.setStrike(getStrike(line));
        } else
            return null;

        ip.setCountry("US");
        ip.setOPOL(getExchange(line));
        ip.setExchangeData(cached(getGroupCode(line) + ";" + getInstrumentCode(line) + ";" + format(conversion)));
        ip.setExchanges(ip.getOPOL());
        ip.setCurrency(getCurrency(line));
        ip.setMMY(getMMY(line));
        ip.setExpiration(getExpiration(line));
        ip.setLastTrade(ip.getExpiration());
        int vtt = getVTT(line);
        if (vtt >= 0) {
            double[] pi = VTT[vtt].clone();
            for (int i = 0; i < pi.length; i++)
                pi[i] /= conversion;
            ip.setPriceIncrements(PriceIncrements.valueOf(pi).getText());
        } else
            ip.setPriceIncrements(PriceIncrements.valueOf(getPriceFluctuation(line) / conversion).getText());

        return ip;
    }

    // ========== Field Access ==========

    private static String getISIN(String line) {
        // This is not a real 'ISIN' although it fulfils all technical requirements.
        return cached(extract(line, 1, 12).substring(5, 11)); // UnderlyingISIN refers only chars [5-11]
    }

    private static int getExpiration(String line) {
        try {
            return (int) (EXPIRATION_FORMAT.parse(extract(line, 56, 14)).getTime() / (24 * 3600 * 1000));
        } catch (ParseException ignored) {}
        return 0;
    }

    private static String getGroupCode(String line) {
        return cached(extract(line, 70, 2));
    }

    private static String getInstrumentCode(String line) {
        return cached(extract(line, 72, 20).trim());
    }

    private static char getType(String line) {
        return extract(line, 92, 1).charAt(0);
    }

    private static double getStrike(String line) {
        return convertLocator(extract(line, 494, 19));
    }

    private static String getCurrency(String line) {
        return cached(extract(line, 545, 3));
    }

    private static double getTick(String line) {
        return convertLocator(extract(line, 548, 19));
    }

    private static int getVTT(String line) {
        String vtt = extract(line, 576, 2);
        return vtt.charAt(0) == ' ' ? -1 : Integer.parseInt(vtt);
    }

    private static double getSettlementPrice(String line) {
        return convertLocator(extract(line, 587, 19));
    }

    private static String getUnderlyingISIN(String line) {
        // See getISIN notes.
        return cached(extract(line, 746, 12).trim());
    }

    private static String getMMY(String line) {
        return cached(extract(line, 824, 6));
    }

    private static String getProductCode(String line) {
        return cached(extract(line, 830, 10).trim());
    }

    private static double getPriceFluctuation(String line) {
        return convertLocator(extract(line, 840, 19));
    }

    private static double getMonetaryValue(String line) {
        return convertLocator(extract(line, 859, 19));
    }

    private static double getConversionFactor(String line) {
        return convertLocatorInverted(extract(line, 878, 19));
    }

    private static String getExchange(String line) {
        return cached(extract(line, 900, 4));
    }

    // ========== Data Parsing ==========

    private static final StringCache strings = new StringCache(); // LRU cache to reduce memory footprint and garbage.

    private static final double[] POWERS = {1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9};
    private static final SimpleDateFormat EXPIRATION_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    static {
        // The expiration date is actually in America/Chicago zone and it specifies exact time up to a second.
        // For example: 03:00:00, 09:05:00, 11:59:40, 13:54:00, 15:28:00, 16:29:30, 18:00:00.
        // It also covers large range (see examples). So we parse date as GMT to convert then to "day".
        EXPIRATION_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private static String cached(String s) {
        return strings.get(s, true);
    }

    private static String extract(String line, int pos, int len) {
        return line.substring(pos - 1, pos - 1 + len);
    }

    private static double round(double d) {
        return Math.floor(d * 1e9 + 0.5) / 1e9;
    }

    private static double convertLocator(String s) {
        char c = s.charAt(0);
        long m = Long.parseLong(s.substring(1));
        if (c == ' ')
            return m;
        if (c >= '0' && c <= '7')
            return m / POWERS[c - '0'];
        if (c >= 'A' && c <= 'H')
            return -m / POWERS[c - 'A'];
        return Double.NaN;
    }

    private static double convertLocatorInverted(String s) {
        char c = s.charAt(0);
        long m = Long.parseLong(s.substring(1));
        if (c == ' ')
            return 1.0 / m;
        if (c >= '0' && c <= '7')
            return POWERS[c - '0'] / m;
        if (c >= 'A' && c <= 'H')
            return -POWERS[c - 'A'] / m;
        return Double.NaN;
    }

    private static String format(double d) {
        if (d == 0)
            return "0";
        return InstrumentProfileField.formatNumber(d);
    }
}
