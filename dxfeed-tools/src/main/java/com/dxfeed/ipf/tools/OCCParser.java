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
package com.dxfeed.ipf.tools;

import com.devexperts.io.UncloseableInputStream;
import com.devexperts.logging.Logging;
import com.dxfeed.glossary.AdditionalUnderlyings;
import com.dxfeed.glossary.PriceIncrements;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * Parses OCC FIXML file containing definitions of options and returns a list of {@link InstrumentProfile}.
 * <p>
 * OCC file does not contain information about primary underlying in case of multiple delivery.
 * In such cases parser uses a heuristic to determine which underlying should be primary.
 * It finds underlying with maximum shares per contract (SPC) value. If several underlyings have
 * same SPC then the smallest one (alphabetically) is chosen. Cash underlying is ignored and
 * could not be chosen as primary unless it is the sole underlying of an option.
 * <p>
 * Used exchanges according to OCC documentation, usage shown as of May 2008:
 * <pre>
 * -MIC-   -ACRONYM-
 * HEGX    HEGX
 * ICEL    IFX
 * XASE    AMEX    used
 * XBOX    BOX     used
 * XCBF    CFE
 * XCBO    CBOE    used
 * XCBT    CBOT
 * XCME    CME
 * XEUS    EOX
 * XISX    ISE     used
 * XNDQ    NSDQ    used
 * XNQL    NQLX
 * XOCH    ONE
 * XPBT    PBOT
 * XPHO    PHLX    used
 * XPSE    PSE     used
 * </pre>
 */
public class OCCParser extends InstrumentProfileReader {

    private static final Logging log = Logging.getLogging(OCCParser.class);
    private static final NumberFormat DECIMAL_FORMAT =
        new DecimalFormat("0.######", new DecimalFormatSymbols(Locale.US));

    private final int bizdate;
    private final boolean osi;

    public OCCParser() {
        this(System.currentTimeMillis());
    }

    public OCCParser(long bizdate) {
        this(bizdate, false);
    }

    public OCCParser(long bizdate, boolean osi) {
        this.bizdate = (int) (bizdate / (24 * 3600 * 1000));
        this.osi = osi;
    }

    /**
     * Reads and returns instrument profiles from specified stream.
     *
     * @throws IOException  If an I/O error occurs
     */
    @SuppressWarnings("deprecation")
    public List<InstrumentProfile> read(InputStream in) throws IOException {
        try {
            InstrumentFileHandler handler = new InstrumentFileHandler();
            XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new UncloseableInputStream(in)));
            return handler.getInstrumentProfiles();
        } catch (ParserConfigurationException | SAXException e) {
            log.error("Exception was thrown during OCC file parsing.", e);
        }
        return Collections.emptyList();
    }


    // ========== Implementation ==========

    private static final String SECURITY_DEFINITION_TAG = "SecDef";
    private static final String SECURITY_DEFINITION_UPDATE_TAG = "SecDefUpd";
    private static final String SECURITY_LIST_TAG = "SecList";
    private static final String SECURITY_LIST_UPDATE_TAG = "SecListUpd";
    private static final String INSTRUMENT_TAG = "Instrmt";
    private static final String UNDERLYING_TAG = "Undly";
    private static final String PARTY_TAG = "Pty";
    private static final String EVENT_TAG = "Evnt";

    private static final String REPORT_ID_ATTRIBUTE = "RptID";
    private static final String STATUS_ATTRIBUTE = "Status";
    private static final String SYMBOL_ATTRIBUTE = "Sym";
    private static final String CFI_ATTRIBUTE = "CFI";
    private static final String STRIKE_CURRENCY_ATTRIBUTE = "StrkCcy";
    private static final String MULTIPLIER_ATTRIBUTE = "Mult";
    private static final String STRIKE_MULTIPLIER_ATTRIBUTE = "StrkMult";
    private static final String DESCRIPTION_ATTRIBUTE = "Desc";
    private static final String SETTLEMENT_ON_OPEN_ATTRIBUTE = "SettlOnOpenFlag";
    private static final String PENNY_PILOT_ATTRIBUTE = "PPInd";
    private static final String OPRA_SUFFIX_ATTRIBUTE = "ID";
    private static final String MATURITY_DATE_ATTRIBUTE = "MatDt";
    private static final String MMY_ATTRIBUTE = "MMY";
    private static final String STRIKE_PRICE_ATTRIBUTE = "StrkPx";
    private static final String SPC_ATTRIBUTE = "Qty";
    private static final String CASH_AMOUNT_ATTRIBUTE = "CashAmt";
    private static final String EVENT_TYPE_ATTRIBUTE = "EventTyp";
    private static final String EVENT_VALUE_ATTRIBUTE = "Dt";
    private static final String PARTY_ROLE_ATTRIBUTE = "R";
    private static final String PARTY_ID_ATTRIBUTE = "ID";

    private static final String NORMAL_INCREMENTS = PriceIncrements.valueOf(new double[] {0.05, 3, 0.10}).getText();
    private static final String PENNY_INCREMENTS = PriceIncrements.valueOf(new double[] {0.01, 3, 0.05}).getText();

    private enum Type { SERIES, OPTION }
    private enum Status { ACTIVE, INACTIVE }

    private class InstrumentFileHandler extends DefaultHandler {
        private final HashMap<String, Series> allSeries = new HashMap<>();

        private boolean finished;

        // Parsing process state
        private Type type;
        private Status status;
        private String optionRoot;
        private Series series;
        private InstrumentProfile profile;
        private String profileKey;

        InstrumentFileHandler() {}

        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (SECURITY_LIST_TAG.equals(qName) || SECURITY_LIST_UPDATE_TAG.equals(qName)) {
                if (checkReportId(attributes.getValue(REPORT_ID_ATTRIBUTE), qName))
                    type = Type.OPTION;
                return;
            }
            if (SECURITY_DEFINITION_TAG.equals(qName) || SECURITY_DEFINITION_UPDATE_TAG.equals(qName)) {
                if (checkReportId(attributes.getValue(REPORT_ID_ATTRIBUTE), qName))
                    type = Type.SERIES;
                return;
            }
            // NOTE: if "checkReportId" detects conflict, then "type", "series" and "profile" below will be "null"
            if (INSTRUMENT_TAG.equals(qName) && type != null) {
                status = "2".equals(attributes.getValue(STATUS_ATTRIBUTE)) ? Status.INACTIVE : Status.ACTIVE;
                optionRoot = attributes.getValue(SYMBOL_ATTRIBUTE);
                String cfi = getValue(attributes, CFI_ATTRIBUTE, "XXXXXX");
                if (type == Type.SERIES) {
                    series = getSeries(optionRoot);
                    series.reset();
                    series.currency = getValue(attributes, STRIKE_CURRENCY_ATTRIBUTE, "");
                    series.multiplier = parseDouble(attributes.getValue(MULTIPLIER_ATTRIBUTE));
                    series.strikeMultiplier = parseDouble(attributes.getValue(STRIKE_MULTIPLIER_ATTRIBUTE));
                    series.optionType = getValue(attributes, DESCRIPTION_ATTRIBUTE, "");
                    series.settlementStyle = "Y".equals(attributes.getValue(SETTLEMENT_ON_OPEN_ATTRIBUTE)) ? "Open" : "Close";
                    series.priceIncrements = "Y".equals(attributes.getValue(PENNY_PILOT_ATTRIBUTE)) ? PENNY_INCREMENTS : NORMAL_INCREMENTS;
                }
                if (type == Type.OPTION) {
                    profile = new InstrumentProfile();
                    profile.setType(cfi.startsWith("O") ? "OPTION" : ""); // safety check
                    String opraSuffix = attributes.getValue(OPRA_SUFFIX_ATTRIBUTE);
                    if (opraSuffix != null && !osi)
                        profile.setSymbol("." + optionRoot + opraSuffix);
                    profile.setCountry("US");
                    profile.setCFI(cfi);
                    String maturityString = getValue(attributes, MMY_ATTRIBUTE, "");
                    profile.setMMY(maturityString);
                    profile.setExpiration(parseDate(attributes.getValue(MATURITY_DATE_ATTRIBUTE)));
                    profile.setLastTrade(profile.getExpiration());
                    String strikeString = attributes.getValue(STRIKE_PRICE_ATTRIBUTE);
                    profile.setStrike(parseDouble(strikeString));
                    profileKey = cfi.charAt(1) + "|" + maturityString + "|" + strikeString;
                }
                return;
            }
            if (UNDERLYING_TAG.equals(qName) && series != null) {
                String underlying = attributes.getValue(SYMBOL_ATTRIBUTE);
                if ("USD".equals(underlying) && attributes.getValue(CFI_ATTRIBUTE).startsWith("MRC"))
                    underlying = "US$";
                series.underlyingMap.put(underlying, parseDouble(attributes.getValue(SPC_ATTRIBUTE)));
                // if additional cash attribute exists then create separate element for it (if one is required)
                if (attributes.getValue(CASH_AMOUNT_ATTRIBUTE) != null) {
                    String currency = "USD".equals(series.currency) ? "US$" : series.currency;
                    series.underlyingMap.put(currency, parseDouble(attributes.getValue(CASH_AMOUNT_ATTRIBUTE)));
                }
                return;
            }
            if (EVENT_TAG.equals(qName)) {
                String event = attributes.getValue(EVENT_TYPE_ATTRIBUTE);
                String dateString = attributes.getValue(EVENT_VALUE_ATTRIBUTE);
                if ("5".equals(event) && dateString != null && profileKey != null)
                    profileKey = profileKey + "|" + dateString;
                int date = parseDate(dateString);
                // if activation date is in the future - ignore this record
                if ("5".equals(event) && date > bizdate)
                    status = Status.INACTIVE;
                // if deactivation date is in the past - ignore this record
                if ("6".equals(event) && date <= bizdate)
                    status = Status.INACTIVE;
                return;
            }
            if (PARTY_TAG.equals(qName) && series != null) {
                if ("22".equals(attributes.getValue(PARTY_ROLE_ATTRIBUTE)))
                    series.exchangeSet.add(attributes.getValue(PARTY_ID_ATTRIBUTE));
                return;
            }
        }

        public void endElement(String uri, String local_name, String q_name) {
            if (INSTRUMENT_TAG.equals(q_name)) {
                if (status == Status.INACTIVE) {
                    if (type == Type.SERIES && series != null)
                        series.deleted = true;
                    if (type == Type.OPTION && profile != null)
                        getSeries(optionRoot).options.remove(profileKey);
                }
                return;
            }
            if (SECURITY_LIST_TAG.equals(q_name) || SECURITY_LIST_UPDATE_TAG.equals(q_name)) {
                if (status == Status.ACTIVE && profile != null)
                    getSeries(optionRoot).options.put(profileKey, profile);
                clearState();
                return;
            }
            if (SECURITY_DEFINITION_TAG.equals(q_name) || SECURITY_DEFINITION_UPDATE_TAG.equals(q_name)) {
                if (status == Status.ACTIVE && series != null)
                    series.complete();
                clearState();
                return;
            }
        }

        private Series getSeries(String optionRoot) {
            Series series = allSeries.get(optionRoot);
            if (series == null)
                allSeries.put(optionRoot, series = new Series(optionRoot));
            return series;
        }

        private void clearState() {
            type = null;
            status = null;
            optionRoot = null;
            series = null;
            profile = null;
            profileKey = null;
        }

        public void endDocument() {
            finishProcessing();

            log.debug("Reused report ids (" + reusedIds.size() + "): " + reusedIds);
            log.debug("Uniqueness violating report ids (" + conflictIds.size() + "): " + conflictIds);
        }

        private final Map<String, String> idToTag = new HashMap<>(); // report id -> last tag
        private final Map<String, Set<String>> reusedIds = new LinkedHashMap<>(); // report id -> used tags
        private final Set<String> conflictIds = new LinkedHashSet<>();

        private boolean checkReportId(String id, String tag) {
            String oldTag = idToTag.put(id, tag);
            if (oldTag == null)
                return true;
            Set<String> tags = reusedIds.computeIfAbsent(id, k -> new LinkedHashSet<>(Collections.singleton(oldTag)));
            if (tags.add(tag))
                return true;
            conflictIds.add(id);
            return false;
        }

        private void finishProcessing() {
            if (finished)
                return;
            for (Series s : allSeries.values()) {
                if (!s.deleted && !s.completed)
                    log.info("DATA INCONSISTENCY: series definition is absent for option root " + s.optionRoot);
                for (InstrumentProfile ip : s.options.values())
                    s.fillWithSeriesData(ip);
            }
            finished = true;
        }

        List<InstrumentProfile> getInstrumentProfiles() {
            finishProcessing();
            ArrayList<InstrumentProfile> result = new ArrayList<>();
            for (Series s : allSeries.values())
                if (s.completed)
                    result.addAll(s.options.values());
            Collections.sort(result);
            return result;
        }

        private int parseDate(String date) {
            return InstrumentProfileField.parseDate(date); // Optimized parsing.
        }

        private double parseDouble(String number) {
            return InstrumentProfileField.parseNumber(number); // Optimized parsing.
        }

        private String getValue(Attributes attributes, String qName, String defValue) {
            String value = attributes.getValue(qName);
            return value != null ? value : defValue;
        }
    }

    private static class Series {
        final String optionRoot;
        final HashMap<String, InstrumentProfile> options = new HashMap<>();

        boolean deleted;
        boolean completed;

        String currency = "";
        double multiplier;
        double strikeMultiplier;
        String optionType = "";
        String settlementStyle = "";
        String priceIncrements = "";

        final TreeSet<String> exchangeSet = new TreeSet<>();
        String opol = "";
        String exchanges = "";

        final Map<String, Double> underlyingMap = new HashMap<>();
        String underlying = "";
        double spc;
        String additionalUnderlyings = "";

        Series(String optionRoot) {
            this.optionRoot = optionRoot;
        }

        void reset() {
            completed = false;
            exchangeSet.clear();
            opol = "";
            exchanges = "";
            underlyingMap.clear();
            underlying = "";
            spc = 0;
            additionalUnderlyings = "";
        }

        void complete() {
            if (completed)
                log.info("DATA INCONSISTENCY: duplicate series definition for option root " + optionRoot);
            deleted = false;
            completed = true;

            if (!exchangeSet.isEmpty()) {
                opol = exchangeSet.first();
                StringBuilder sb = new StringBuilder();
                for (String s : exchangeSet)
                    sb.append(sb.length() == 0 ? "" : ";").append(s);
                exchanges = sb.toString();
            }

            if (!underlyingMap.isEmpty()) {
                String cur = "USD".equals(currency) ? "US$" : currency;
                String best = null;
                for (String s : underlyingMap.keySet())
                    if (best == null || best.equals(cur) ||
                        !s.equals(cur) && (spc < underlyingMap.get(s) || spc == underlyingMap.get(s) && best.compareTo(s) > 0))
                    {
                        best = s;
                        spc = underlyingMap.get(best);
                    }
                underlying = best;
                underlyingMap.remove(best);
                additionalUnderlyings = AdditionalUnderlyings.valueOf(underlyingMap).getText();
            } else {
                log.warn("WARNING: Underlying information is absent for option root " + optionRoot);
            }
        }

        void fillWithSeriesData(InstrumentProfile profile) {
            profile.setCurrency(currency);
            profile.setMultiplier(multiplier);
            profile.setStrike(Math.floor(profile.getStrike() * strikeMultiplier * 1e6 + 0.5) / 1e6);
            profile.setOptionType(optionType);
            profile.setSettlementStyle(settlementStyle);
            profile.setPriceIncrements(priceIncrements);

            profile.setOPOL(opol);
            profile.setExchanges(exchanges);

            profile.setUnderlying(underlying);
            profile.setSPC(spc);
            profile.setAdditionalUnderlyings(additionalUnderlyings);

            if (profile.getSymbol().isEmpty())
                profile.setSymbol("." + optionRoot +
                    profile.getMMY().substring(Math.max(profile.getMMY().length() - 6, 0)) +
                    profile.getCFI().charAt(1) + DECIMAL_FORMAT.format(profile.getStrike()));
        }
    }
}
