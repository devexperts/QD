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
package com.dxfeed.ondemand.impl.event;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordMapping;
import com.dxfeed.event.candle.impl.TradeHistoryMapping;
import com.dxfeed.event.market.impl.FundamentalMapping;
import com.dxfeed.event.market.impl.MarketEventMapping;
import com.dxfeed.event.market.impl.MarketMakerMapping;
import com.dxfeed.event.market.impl.ProfileMapping;
import com.dxfeed.event.market.impl.QuoteMapping;
import com.dxfeed.event.market.impl.SummaryMapping;
import com.dxfeed.event.market.impl.TimeAndSaleMapping;
import com.dxfeed.event.market.impl.TradeMapping;
import com.dxfeed.ondemand.impl.Key;

import java.util.Arrays;
import java.util.Collection;

public class MDREventUtil {

    public static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    public static final SymbolCodec CODEC = SCHEME.getCodec();

    // ========== Event Types ==========

    private static final char[] RECORD_TYPES = new char[SCHEME.getRecordCount()];
    private static final char[] RECORD_EXCHANGES = new char[SCHEME.getRecordCount()];
    private static final DataRecord[][][] RECORDS_BY_TYPE_EXCHANGE = new DataRecord[128][][];

    static {
        for (int i = 0; i < SCHEME.getRecordCount(); i++) {
            buildRecordMapping(i, QuoteMapping.class, 'Q');
            buildRecordMapping(i, TradeMapping.class, 'T');
            buildRecordMapping(i, SummaryMapping.class, 'S');
            buildRecordMapping(i, FundamentalMapping.class, 'S');
            buildRecordMapping(i, ProfileMapping.class, 'P');
            buildRecordMapping(i, TimeAndSaleMapping.class, 'H');
            buildRecordMapping(i, TradeHistoryMapping.class, 'H');
            buildRecordMapping(i, MarketMakerMapping.class, 'M');
        }
    }

    private static void buildRecordMapping(int index, Class<? extends RecordMapping> mappingClass, char type) {
        DataRecord record = SCHEME.getRecord(index);
        RecordMapping mapping = record.getMapping(mappingClass);
        if (mapping == null)
            return;
        char exchange = mapping instanceof MarketEventMapping ? ((MarketEventMapping) mapping).getRecordExchange() : '\0';
        RECORD_TYPES[index] = type;
        RECORD_EXCHANGES[index] = (type == 'H') ? '\0' : exchange;
        if (RECORDS_BY_TYPE_EXCHANGE[type] == null)
            RECORDS_BY_TYPE_EXCHANGE[type] = new DataRecord[128][];
        DataRecord[] rs = RECORDS_BY_TYPE_EXCHANGE[type][exchange];
        rs = rs == null ? new DataRecord[1] : Arrays.copyOf(rs, rs.length + 1);
        rs[rs.length - 1] = record;
        RECORDS_BY_TYPE_EXCHANGE[type][exchange] = rs;
    }

    private static IllegalArgumentException unknownType(int type) {
        return new IllegalArgumentException("Unknown type '" + (type >= ' ' ? (char) type : '?') + "'");
    }

    public static char getType(DataRecord record) {
        return RECORD_TYPES[record.getId()];
    }

    public static char getExchange(DataRecord record) {
        return RECORD_EXCHANGES[record.getId()];
    }

    // multiple records may exist for a gives block type and exchange
    // 'S' -> Fundamental & Summary ; 'H' -> TradeHistory & TimeAndSale
    public static DataRecord[] getRecords(char type, char exchange) {
        DataRecord[][] a = RECORDS_BY_TYPE_EXCHANGE[type];
        if (a == null)
            throw unknownType(type);
        return a[exchange];
    }

    public static MDREvent createEvent(char type) {
        switch (type) {
            case 'Q':
                return new MDRQuote();
            case 'T':
                return new MDRTrade();
            case 'S':
                return new MDRSummary();
            case 'P':
                return new MDRProfile();
            case 'H':
                return new MDRTradeHistory();
            case 'M':
                return new MDRMarketMaker();
            default:
                throw unknownType(type);
        }
    }

    public static String getRecordName(char type) {
        switch (type) {
            case 'Q':
                return "Quote";
            case 'T':
                return "Trade";
            case 'S':
                return "Summary";
            case 'P':
                return "Profile";
            case 'H':
                return "TimeAndSale";
            case 'M':
                return "MarketMaker";
            default:
                throw unknownType(type);
        }
    }

    // ========== Event Categories ==========

    // 1st letter: E=Equities, F=Futures, O=Options, Q=Future Options, X=Forex
    // 2nd letter: -=Composite, .=Regional
    // 3rd letter: Q=Quote, T=Trade, S=Summary, P=Profile, H=TradeHistory, M=MarketMaker
    public static final String TYPES = "QTSPHM";
    public static final int NUMBER_OF_CATEGORIES = 8 * 2 * 8;
    private static final String[] CATEGORY_NAMES = new String[NUMBER_OF_CATEGORIES];
    private static final int[] CATEGORY_TYPE_INDEXES = new int[128];

    static {
        for (int si = 0; si < 8; si++)
            for (int ei = 0; ei < 2; ei++)
                for (int ti = 0; ti < 8; ti++)
                    CATEGORY_NAMES[(si << 4) + (ei << 3) + ti] = new String(new char[] {"EFOQX---".charAt(si), "-.".charAt(ei), "QTSPHM--".charAt(ti)});
        Arrays.fill(CATEGORY_TYPE_INDEXES, -1);
        CATEGORY_TYPE_INDEXES['Q'] = 0;
        CATEGORY_TYPE_INDEXES['T'] = 1;
        CATEGORY_TYPE_INDEXES['S'] = 2;
        CATEGORY_TYPE_INDEXES['P'] = 3;
        CATEGORY_TYPE_INDEXES['H'] = 4;
        CATEGORY_TYPE_INDEXES['M'] = 5;
    }

    private static int getCategory(int si, int exchange, int type) {
        int ei = exchange == '\0' ? 0 : 1;
        int ti;
        if (type < 0 || type >= CATEGORY_TYPE_INDEXES.length || (ti = CATEGORY_TYPE_INDEXES[type]) < 0)
            throw unknownType(type);
        return (si << 4) + (ei << 3) + ti;
    }

    public static String getCategoryName(int category) {
        return CATEGORY_NAMES[category];
    }

    public static int getCategory(String name) {
        for (int i = 0; i < CATEGORY_NAMES.length; i++)
            if (CATEGORY_NAMES[i].equals(name))
                return i;
        throw new IllegalArgumentException("Unknown category: " + name);
    }

    public static int getCategory(Key key) {
        String s = key.getSymbol();
        int si = s == null || s.length() == 0 ? 0 :
            s.charAt(0) == '/' ? 1 :
                s.charAt(0) == '.' ? (s.length() > 1 && s.charAt(1) == '/' ? 3 : 2) :
                    isForex(s) ? 4 : 0;
        return getCategory(si, key.getExchange(), key.getType());
    }

    public static int getCategory(byte[] bytes, int offset, int length) {
        int si = length == 0 ? 0 :
            bytes[offset] == '/' ? 1 :
                bytes[offset] == '.' ? (length > 1 && bytes[offset + 1] == '/' ? 3 : 2) :
                    isForex(bytes, offset, length) ? 4 : 0;
        return getCategory(si, bytes[offset + length], bytes[offset + length + 1]);
    }

    public static String countCategories(Collection<Key> keys) {
        int[] categories = new int[NUMBER_OF_CATEGORIES];
        for (Key key : keys)
            categories[getCategory(key)]++;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categories.length; i++)
            if (categories[i] > 0)
                sb.append(" ").append(getCategoryName(i)).append("=").append(categories[i]);
        return sb.toString();
    }

    // ========== Currency and Forex ==========

    private static final String CURRENCY_CODES =
        // Active currency codes
        "AED,AFN,ALL,AMD,ANG,AOA,ARS,AUD,AWG,AZN," +
            "BAM,BBD,BDT,BGN,BHD,BIF,BMD,BND,BOB,BOV,BRL,BSD,BTN,BWP,BYR,BZD," +
            "CAD,CDF,CHE,CHF,CHW,CLF,CLP,CNY,COP,COU,CRC,CUC,CUP,CVE,CZK," +
            "DJF,DKK,DOP,DZD,EEK,EGP,ERN,ETB,EUR,FJD,FKP," +
            "GBP,GEL,GHS,GIP,GMD,GNF,GTQ,GYD,HKD,HNL,HRK,HTG,HUF," +
            "IDR,ILS,INR,IQD,IRR,ISK,JMD,JOD,JPY," +
            "KES,KGS,KHR,KMF,KPW,KRW,KWD,KYD,KZT," +
            "LAK,LBP,LKR,LRD,LSL,LTL,LVL,LYD," +
            "MAD,MDL,MGA,MKD,MMK,MNT,MOP,MRO,MUR,MVR,MWK,MXN,MXV,MYR,MZN," +
            "NAD,NGN,NIO,NOK,NPR,NZD,OMR,PAB,PEN,PGK,PHP,PKR,PLN,PYG,QAR," +
            "RON,RSD,RUB,RWF," +
            "SAR,SBD,SCR,SDG,SEK,SGD,SHP,SLL,SOS,SRD,STD,SYP,SZL," +
            "THB,TJS,TMT,TND,TOP,TRY,TTD,TWD,TZS," +
            "UAH,UGX,USD,USN,USS,UYU,UZS,VEF,VND,VUV,WST," +
            "XAF,XAG,XAU,XBA,XBB,XBC,XBD,XCD,XDR,XFU,XOF,XPD,XPF,XPT,XTS,XXX," + // Special codes
            "YER,ZAR,ZMK,ZWL," +
            // Obsolete currency codes
            "ADF,ADP,ATS,BEF,CYP,DEM,ESP,FIM,FRF,GRD,IEP,ITL,LUF,MCF,MAF,MTL,NLG,PTE,SIT,SKK,SML,VAL,XEU," + // EUR zone
            "AFA,AON,AOR,ARL,ARP,ARA,AZM,BGL,BOP,BRB,BRC,BRE,BRN,BRR," +
            "CSD,CSK,DDM,ECS,ECV,GQE,ESA,ESB,GNE,GHC,GWP,ILP,ILR,ISJ," +
            "LAJ,MGF,MKN,MLF,MVQ,MXP,MZM,NFD,PEH,PEI,PLZ," +
            "ROL,RUR,SDD,SRG,SUR,SVC,TJR,TMM,TRL,UAK,UGS,UYN,VEB,XFO," +
            "YDD,YUD,YUN,YUR,YUO,YUG,YUM,ZAL,ZRN,ZRZ,ZWC,ZWD,ZWN,ZWR,";

    private static final int[] CURRENCY_SET = new int[1024];

    static {
        for (String s : CURRENCY_CODES.split(",")) {
            if (s.length() != 3)
                throw new IllegalArgumentException("Bad currency " + s);
            int currencyIndex = getCurrencyIndex(s.charAt(0), s.charAt(1), s.charAt(2));
            if (currencyIndex < 0)
                throw new IllegalArgumentException("Bad currency " + s);
            if (isCurrency(currencyIndex))
                throw new IllegalArgumentException("Duplicate currency " + s);
            CURRENCY_SET[currencyIndex >> 5] |= 1 << currencyIndex;
        }
    }

    private static int getCurrencyIndex(int c1, int c2, int c3) {
        // Expression below packs 3 characters from range 'A'..'Z' into int using 5 bits per character.
        // If any character is outside required range, then resulting index will be negative.
        // Note: this logic fails for very large negative numbers, i.e. lower than -2 millions.
        return c1 - 'A' << 10 | c2 - 'A' << 5 | c3 - 'A' | ('Z' - c1 | 'Z' - c2 | 'Z' - c3) >> 5;
    }

    private static boolean isCurrency(int currencyIndex) {
        return currencyIndex >= 0 && (CURRENCY_SET[currencyIndex >> 5] & 1 << currencyIndex) != 0;
    }

    public static boolean isCurrency(String s) {
        return s != null && s.length() == 3 && isCurrency(s, 0);
    }

    public static boolean isCurrency(String s, int offset) {
        return isCurrency(getCurrencyIndex(s.charAt(offset), s.charAt(offset + 1), s.charAt(offset + 2)));
    }

    public static boolean isCurrency(byte[] bytes, int offset) {
        return isCurrency(getCurrencyIndex(bytes[offset], bytes[offset + 1], bytes[offset + 2]));
    }

    public static boolean isForex(String s) {
        // Forex category includes all forex-related symbols with any suffixes.
        if (s == null || s.length() < 7)
            return false;
        if (s.charAt(3) == '/') // EUR/USD
            return isCurrency(s, 0) && isCurrency(s, 4);
        if (s.charAt(4) == '/') // EUR$/USD$
            return s.length() >= 9 && isCurrency(s, 0) && s.charAt(3) == '$' && isCurrency(s, 5) && s.charAt(8) == '$';
        if (s.charAt(6) == '/') // EUR$FX/USD$FX
            return s.length() >= 13 && isCurrency(s, 0) && s.startsWith("$FX", 3) && isCurrency(s, 7) && s.startsWith("$FX", 10);
        return false;
    }

    public static boolean isForex(byte[] bytes, int offset, int length) {
        // Forex category includes all forex-related symbols with any suffixes.
        if (length < 7)
            return false;
        if (bytes[offset + 3] == '/') // EUR/USD
            return isCurrency(bytes, offset) && isCurrency(bytes, offset + 4);
        if (bytes[offset + 4] == '/') // EUR$/USD$
            return length >= 9 && isCurrency(bytes, offset) && bytes[offset + 3] == '$' && isCurrency(bytes, offset + 5) && bytes[offset + 8] == '$';
        if (bytes[offset + 6] == '/') // EUR$FX/USD$FX
            return length >= 13 && isCurrency(bytes, offset) && bytes[offset + 3] == '$' && bytes[offset + 4] == 'F' && bytes[offset + 5] == 'X' &&
                isCurrency(bytes, offset + 7) && bytes[offset + 10] == '$' && bytes[offset + 11] == 'F' && bytes[offset + 12] == 'X';
        return false;
    }

    public static boolean isGoodSymbol(String symbol) {
        if (symbol == null || symbol.length() == 0 || symbol.length() >= 256)
            return false;
        if (symbol.charAt(0) == '#' && (symbol.equals("#LOCKED") || symbol.equals("#INVERTED"))) // CSGate feed metrics
            return false;
        boolean hasLetter = false;
        for (int i = 0; i < symbol.length(); i++) {
            char c = symbol.charAt(i);
            if (Character.isISOControl(c) || Character.isHighSurrogate(c) || Character.isLowSurrogate(c))
                return false;
            if (c == ' ' || c == ',')
                return false;
            if (!hasLetter && Character.isLetter(c))
                hasLetter = true;
        }
        return hasLetter;
    }
}
