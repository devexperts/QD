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
package com.dxfeed.api.impl;

import java.util.StringTokenizer;
import java.util.regex.Pattern;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.services.ServiceProvider;

@SuppressWarnings("UnusedDeclaration")
@ServiceProvider(order = -100)
public class FilterFactoryImpl extends QDFilterFactory {
    private static final String COMPFEED_RECORDS =
        "Quote, Trade, TradeETH, Summary, Fundamental, Profile, Book, Order*, SpreadOrder*, MarketMaker, TimeAndSale, TradeHistory";
    private static final String CHARTDATA_RECORDS =
        "TradeHistory, Trade.*, Candle, Candle[{]*, " +
        "DividendHistory, SplitHistory, EarningsHistory, ConferenceCallHistory, NewsHistory";

    @SpecificSubscriptionFilter("conversion rate filter, " +
        "accepts composite and regional Quote records, " +
        "accepts conversion rate symbols like " +
        "([A-Z]{3}$/[A-Z]{3}$)|([A-Z]{3}$FX/[A-Z]{3}$FX), " +
        "no optional symbol parameters are accepted")
    public static final String CONVRATES = "convrates";

    @SpecificSubscriptionFilter("NoWildCard filter, accepts all records, prohibits wildcard symbol")
    public static final String NWC = "nwc";

    @SpecificSubscriptionFilter("accepts data provided by charting server via history contract: " + CHARTDATA_RECORDS)
    public static final String CHARTDATA = "chartdata";

    @SpecificSubscriptionFilter("accepts composite and regional records forming exchange data feed: " + COMPFEED_RECORDS)
    public static final String FEED = "feed";

    @SpecificSubscriptionFilter("accepts composite records forming exchange data feed")
    public static final String COMPFEED = "compfeed";

    @SpecificSubscriptionFilter("accepts regional records forming exchange data feed")
    public static final String REGFEED = "regfeed";

    private static final String SYMBOL_SUFFIX = "symbol";

    @SpecificSubscriptionFilter("The following symbol-based filters exist in 2 variants:\n" +
        "symbol-only (record agnostic) or including additional \"feed\" filter, " +
        "e.g. \"opt = optsymbol & feed\", \"fx = fxsymbol & feed\", etc.")
    public static final String SYMBOL_DESC = "*" + SYMBOL_SUFFIX;

    @SpecificSubscriptionFilter("basic symbol aka [^/.=]* (not future, not option, not spread)")
    public static final String BS = "bs";

    @SpecificSubscriptionFilter("future symbol aka /*")
    public static final String FUT = "fut";

    @SpecificSubscriptionFilter("option symbol aka .*")
    public static final String OPT = "opt";

    @SpecificSubscriptionFilter("spread symbol aka =*")
    public static final String SPREAD = "spread";

    @SpecificSubscriptionFilter("non-option symbol aka [^.]*")
    public static final String CS = "cs";

    @SpecificSubscriptionFilter("equity option symbol aka .[^/]*")
    public static final String CSOPT = "csopt";

    @SpecificSubscriptionFilter("basic symbol option symbol aka .[^/]*")
    public static final String BSOPT = "bsopt";

    @SpecificSubscriptionFilter("future option symbol aka ./*")
    public static final String FUTOPT = "futopt";

    @SpecificSubscriptionFilter("indicator or index (non-exchange) symbol aka $*")
    public static final String IND = "ind";

    @SpecificSubscriptionFilter("product symbol aka /[A-Z0-9]{1,3}, " +
        "accepts optional symbol parameters like {tho=true} or {price=bid}, " +
        "accepts optional symbol namespace like :XCME or :RTSX")
    public static final String PROD = "prod";

    @SpecificSubscriptionFilter("forex symbol aka [A-Z]{3,4}/[A-Z]{3} with optional suffix hash, " +
        "accepts optional symbol parameters like {tho=true} or {price=bid}, " +
        "accepts optional symbol namespace like :XCME or :RTSX")
    public static final String FX = "fx";

    @Override
    public QDFilter createFilter(String spec) {
        return createFilter(spec, QDFilterContext.DEFAULT);
    }

    @Override
    public QDFilter createFilter(String spec, QDFilterContext context) {
        QDFilter filter = parseCategoryFilter(spec);
        if (filter != null)
            return filter;
        filter = parseSymbolFilter(spec);
        if (filter != null)
            return filter;
        filter = parseSymbolFilter(spec + SYMBOL_SUFFIX);
        if (filter != null && context != QDFilterContext.SYMBOL_SET)
            return CompositeFilters.makeAnd(filter, parseCategoryFilter(FEED));
        return filter;
    }

    public QDFilter parseCategoryFilter(String spec) {
        if (spec.equals(CONVRATES))
            return new ConversionRateFilter(getScheme());
        if (spec.equals(NWC))
            return new NWCFilter(getScheme());
        if (spec.equals(CHARTDATA))
            return CompositeFilters.valueOf(makeFilter(CHARTDATA_RECORDS, ""), spec, getScheme());
        if (spec.equals(FEED))
            return CompositeFilters.valueOf(makeFilter(COMPFEED_RECORDS, "") + "," + makeFilter(COMPFEED_RECORDS, "[&][A-Z]"), spec, getScheme());
        if (spec.equals(COMPFEED))
            return CompositeFilters.valueOf(makeFilter(COMPFEED_RECORDS, ""), spec, getScheme());
        if (spec.equals(REGFEED))
            return CompositeFilters.valueOf(makeFilter(COMPFEED_RECORDS, "[&][A-Z]"), spec, getScheme());
        return null;
    }

    private static String makeFilter(String records, String suffix) {
        StringBuilder sb = new StringBuilder();
        for (StringTokenizer st = new StringTokenizer(records, ", "); st.hasMoreTokens();) {
            String recordMask = st.nextToken();
            if (recordMask.endsWith("*") && !suffix.isEmpty())
                continue; // these multi-records cannot be "regional"
            if (sb.length() > 0)
                sb.append(',');
            sb.append(':').append(recordMask).append(suffix);
        }
        return sb.toString();
    }

    private QDFilter parseSymbolFilter(String spec) throws FilterSyntaxException {
        if (spec.equals(BS + SYMBOL_SUFFIX))
            return PatternFilter.valueOf("[^/.=]*", spec, getScheme());
        if (spec.equals(FUT + SYMBOL_SUFFIX))
            return PatternFilter.valueOf("/*", spec, getScheme());
        if (spec.equals(OPT + SYMBOL_SUFFIX))
            return PatternFilter.valueOf(".*", spec, getScheme());
        if (spec.equals(SPREAD + SYMBOL_SUFFIX))
            return PatternFilter.valueOf("=*", spec, getScheme());

        if (spec.equals(CS + SYMBOL_SUFFIX)) // Backward compatibility filter; cs = non-opt
            return PatternFilter.valueOf("[^.]*", spec, getScheme());
        if (spec.equals(CSOPT + SYMBOL_SUFFIX))
            return PatternFilter.valueOf(".[^/]*", spec, getScheme());
        if (spec.equals(BSOPT + SYMBOL_SUFFIX))
            return PatternFilter.valueOf(".[^/]*", spec, getScheme());
        if (spec.equals(FUTOPT + SYMBOL_SUFFIX))
            return PatternFilter.valueOf("./*", spec, getScheme());

        if (spec.equals(IND + SYMBOL_SUFFIX))
            return PatternFilter.valueOf("$*", spec, getScheme());
        if (spec.equals(PROD + SYMBOL_SUFFIX))
            return new ProductSymbolFilter(getScheme());
        if (spec.equals(FX + SYMBOL_SUFFIX))
            return new ForexSymbolFilter(getScheme());
        return null;
    }

    private static class ProductSymbolFilter extends QDFilter {
        private final int wildcard;
        private final boolean negated;

        ProductSymbolFilter(DataScheme scheme) {
            this(scheme, false);
        }

        private ProductSymbolFilter(DataScheme scheme, boolean negated) {
            super(scheme);
            wildcard = scheme.getCodec().getWildcardCipher();
            this.negated = negated;
        }

        @Override
        public Kind getKind() {
            return Kind.OTHER_SYMBOL_ONLY;
        }

        @Override
        public boolean isFast() {
            return true;
        }

        @Override
        public QDFilter negate() {
            return new ProductSymbolFilter(getScheme(), !negated);
        }

        /**
         * Product symbols are: /[A-Z0-9]{1,3}([{:].*)?
         */
        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            if (cipher == wildcard)
                return true;
            if (symbol != null)
                return negated ^ acceptSymbol(symbol);
            return negated ^ acceptCode(getScheme().getCodec().decodeToLong(cipher));
        }

        private boolean acceptCode(long code) {
            if (code >>> 56 != '/')
                return false;
            for (int i = 1; i <= 4; i++) {
                int c = (int) (code >>> ((7 - i) << 3)) & 0xff;
                if (c == 0 || c == '{' || c == ':')
                    return i >= 2 && i <= 4;
                if (!(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'))
                    return false;
            }
            return false;
        }

        private boolean acceptSymbol(String symbol) {
            int n = symbol.length();
            if (n < 2)
                return false;
            if (symbol.charAt(0) != '/')
                return false;
            for (int i = 1; i <= 4 && i < n; i++) {
                char c = symbol.charAt(i);
                if (c == '{' || c == ':')
                    return i >= 2 && i <= 4;
                if (!(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'))
                    return false;
            }
            return n <= 4;
        }

        @Override
        public String getDefaultName() {
            return (negated ? "!" : "") + PROD + SYMBOL_SUFFIX;
        }

        @Override
        public QDFilter toStableFilter() {
            return this;
        }
    }

    private static class ForexSymbolFilter extends QDFilter {
        private final int wildcard;
        private final boolean negated;

        ForexSymbolFilter(DataScheme scheme) {
            this(scheme, false);
        }

        private ForexSymbolFilter(DataScheme scheme, boolean negated) {
            super(scheme);
            wildcard = scheme.getCodec().getWildcardCipher();
            this.negated = negated;
        }

        @Override
        public Kind getKind() {
            return Kind.OTHER_SYMBOL_ONLY;
        }

        @Override
        public boolean isFast() {
            return true;
        }

        @Override
        public QDFilter negate() {
            return new ForexSymbolFilter(getScheme(), !negated);
        }

        /**
         * Forex symbols are: [A-Z]{3,4}/[A-Z]{3}([#{:].*)?
         */
        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            if (cipher == wildcard)
                return true;
            // Penta codec cannot encode forex symbols - they are too long. Assume string symbol check only.
            if (cipher != 0)
                return negated;
            return negated ^ acceptSymbol(symbol);
        }

        private boolean acceptSymbol(String symbol) {
            if (symbol == null || symbol.length() < 7)
                return false;
            if (symbol.charAt(3) == '/')
                return isChar(symbol.charAt(0)) && isChar(symbol.charAt(1)) && isChar(symbol.charAt(2)) &&
                    isChar(symbol.charAt(4)) && isChar(symbol.charAt(5)) && isChar(symbol.charAt(6)) &&
                    (symbol.length() <= 7 || isEnding(symbol.charAt(7)));
            if (symbol.charAt(4) == '/' && symbol.length() >= 8)
                return isChar(symbol.charAt(0)) && isChar(symbol.charAt(1)) && isChar(symbol.charAt(2)) && isChar(symbol.charAt(3)) &&
                    isChar(symbol.charAt(5)) && isChar(symbol.charAt(6)) && isChar(symbol.charAt(7)) &&
                    (symbol.length() <= 8 || isEnding(symbol.charAt(8)));
            return false;
        }

        private static boolean isChar(char c) {
            return c >= 'A' && c <= 'Z';
        }

        private static boolean isEnding(char c) {
            return c == '#' || c == '{' || c == ':';
        }

        @Override
        public QDFilter toStableFilter() {
            return this;
        }

        @Override
        public String getDefaultName() {
            return (negated ? "!" : "") + FX + SYMBOL_SUFFIX;
        }
    }

    private static class ConversionRateFilter extends QDFilter {
        private final int wildcard;
        private final boolean[] accepts;

        ConversionRateFilter(DataScheme scheme) {
            super(scheme);
            wildcard = scheme.getCodec().getWildcardCipher();
            accepts = new boolean[scheme.getRecordCount()];
            Pattern regex = Pattern.compile("Quote(&[A-Z])?");
            for (int i = 0; i < accepts.length; i++)
                accepts[i] = regex.matcher(scheme.getRecord(i).getName()).matches();
        }

        @Override
        public boolean isFast() {
            return true;
        }

        /**
         * Conversion rate symbols are: ([A-Z]{3}$/[A-Z]{3}$)|([A-Z]{3}$FX/[A-Z]{3}$FX)
         */
        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            int id = record.getId();
            if (id >= accepts.length || !accepts[id])
                return false;
            // Penta codec cannot encode conversion rate symbols - they are too long. Assume string symbol check only.
            if (cipher != 0)
                return cipher == wildcard;
            return acceptSymbol(symbol);
        }

        private boolean acceptSymbol(String symbol) {
            if (symbol == null)
                return false;
            if (symbol.length() == 9)
                return symbol.charAt(3) == '$' && symbol.charAt(4) == '/' && symbol.charAt(8) == '$' &&
                    isChar(symbol.charAt(0)) && isChar(symbol.charAt(1)) && isChar(symbol.charAt(2)) &&
                    isChar(symbol.charAt(5)) && isChar(symbol.charAt(6)) && isChar(symbol.charAt(7));
            if (symbol.length() == 13)
                return symbol.charAt(3) == '$' && symbol.charAt(4) == 'F' && symbol.charAt(5) == 'X' && symbol.charAt(6) == '/' &&
                    symbol.charAt(10) == '$' && symbol.charAt(11) == 'F' && symbol.charAt(12) == 'X' &&
                    isChar(symbol.charAt(0)) && isChar(symbol.charAt(1)) && isChar(symbol.charAt(2)) &&
                    isChar(symbol.charAt(7)) && isChar(symbol.charAt(8)) && isChar(symbol.charAt(9));
            return false;
        }

        private static boolean isChar(char c) {
            return c >= 'A' && c <= 'Z';
        }

        @Override
        public QDFilter toStableFilter() {
            return this;
        }

        @Override
        public String getDefaultName() {
            return CONVRATES;
        }
    }

    private static class NWCFilter extends QDFilter {
        private final int wildcard;

        NWCFilter(DataScheme scheme) {
            super(scheme);
            wildcard = scheme.getCodec().getWildcardCipher();
        }

        @Override
        public boolean isFast() {
            return true;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return cipher != wildcard;
        }

        @Override
        public QDFilter toStableFilter() {
            return this;
        }

        @Override
        public String getDefaultName() {
            return NWC;
        }
    }
}
