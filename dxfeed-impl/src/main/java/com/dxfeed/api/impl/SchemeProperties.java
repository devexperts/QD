/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import com.devexperts.logging.Logging;
import com.devexperts.qd.Deprecation;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.util.GlobListUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.event.custom.NuamOrder;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderImbalance;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.SpreadOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static com.dxfeed.api.DXEndpoint.DXSCHEME_ENABLED_PROPERTY_PREFIX;
import static com.dxfeed.api.DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY;

public class SchemeProperties {

    /** Common scheme related properties */
    public static final String DXSCHEME_PREFIX = "dxscheme.";

    /** @deprecated Use new properties format instead. */
    @Deprecated()
    public static final String OLD_DXSCHEME_PREFIX = "com.dxfeed.event.";

    public static final String DXSCHEME_WIDE_PROPERTY = "dxscheme.wide";

    public static final String DXSCHEME_FOB_PROPERTY = "dxscheme.fob";

    public static final String DXSCHEME_EXCHANGES_PROPERTY = "dxscheme.exchanges";

    public static final String DXSCHEME_SUFFIXES_PROPERTY = "dxscheme.suffixes";

    public static final String DXSCHEME_FOB_SUFFIXES_PROPERTY = "dxscheme.fob.suffixes";

    /** @deprecated Use {@link #DXSCHEME_FOB_SUFFIXES_PROPERTY} instead. */
    @Deprecated()
    public static final String OLD_FOB_SUFFIXES_PROPERTY = "com.dxfeed.event.market.impl.Order.fob.suffixes";

    private static final String FOB_SUFFIX_DEFAULT = getFullOrderBookSuffixes();

    private static final Logging log = Logging.getLogging(SchemeProperties.class);

    private final Map<String, String> patternStrings = new HashMap<>();
    private final Map<String, Pattern> patterns = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();

    public static boolean supportsProperty(String key) {
        return key.startsWith(DXSCHEME_PREFIX) || key.startsWith(OLD_DXSCHEME_PREFIX);
    }

    public SchemeProperties(Properties props) {
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (value == null)
                continue;

            if (supportsProperty(key)) {
                properties.put(key, value);
            }
            if (key.startsWith(DXSCHEME_ENABLED_PROPERTY_PREFIX)) {
                String propertyName = key.substring(DXSCHEME_ENABLED_PROPERTY_PREFIX.length());
                enableEventPropertyIfAbsent(propertyName, value);
            }
        }
        if (getBooleanProperty(DXSCHEME_NANO_TIME_PROPERTY, false)) {
            enableEventPropertyIfAbsent("Sequence", "*");
            enableEventPropertyIfAbsent("TimeNanoPart", "*");
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof SchemeProperties &&
            patternStrings.equals(((SchemeProperties) o).patternStrings) &&
            properties.equals(((SchemeProperties) o).properties);
    }

    @Override
    public int hashCode() {
        return patternStrings.hashCode() + properties.hashCode();
    }

    private void enableEventPropertyIfAbsent(String propertyName, String patternString) {
        if (patternStrings.containsKey(propertyName))
            return;
        patternStrings.put(propertyName, patternString);
        patterns.put(propertyName, GlobListUtil.compile(patternString));
    }

    /**
     * Returns {@link Boolean#TRUE} or {@link Boolean#FALSE} if specified property should be enabled or disabled
     * instead of its default behavior. Otherwise, returns {@code null}.
     */
    Boolean isEventPropertyEnabled(String propertyName, String eventName) {
        Pattern pattern = patterns.get(propertyName);
        if (pattern == null)
            return null;
        return pattern.matcher(eventName).matches();
    }

    public Map<? extends String, ? extends String> getProperties() {
        return properties;
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public String getProperty(String key, String oldKey, String defaultValue) {
        if (properties.containsKey(oldKey))
            Deprecation.ofProperty(oldKey, key).warn();
        return properties.getOrDefault(key, properties.getOrDefault(oldKey, defaultValue));
    }

    public boolean isWide() {
        return getBooleanProperty(DXSCHEME_WIDE_PROPERTY, true);
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    public Boolean isWideExplicit() {
        return getBooleanPropertyExplicit(DXSCHEME_WIDE_PROPERTY);
    }

    public boolean isFob() {
        return getBooleanProperty(DXSCHEME_FOB_PROPERTY, false);
    }

    public String getFobSuffixes() {
        return getSuffixes(DXSCHEME_FOB_SUFFIXES_PROPERTY, OLD_FOB_SUFFIXES_PROPERTY, FOB_SUFFIX_DEFAULT);
    }

    public boolean isFobEnabled(String suffix) {
        return isSuffixEnabled(suffix, DXSCHEME_FOB_SUFFIXES_PROPERTY, OLD_FOB_SUFFIXES_PROPERTY, FOB_SUFFIX_DEFAULT);
    }

    public boolean isSuffixEnabled(String suffix, String name, String oldName, String defaultSuffixes) {
        String suffixes = getSuffixes(name, oldName, defaultSuffixes);
        return suffix.matches(suffixes);
    }

    public String getOrderSuffixes(String orderRecordName, String oldName, Class<? extends MarketEvent> orderClass) {
        String defaultSuffixes = getDefaultOrderSuffixes(orderClass);
        // Create property name "dxscheme.suffixes.Candle"
        String name = DXSCHEME_SUFFIXES_PROPERTY + "." + orderRecordName;
        return getSuffixes(name, oldName, defaultSuffixes);
    }

    public String getSuffixes(String name, String oldName, String defaultSuffixes) {
        String base = getProperty(name, oldName, defaultSuffixes);
        String ext = properties.getOrDefault(name.replace("suffixes", "suffixesExt"), null);
        return combineSuffixes(base, ext);
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    public String getSuffixesExplicit(String name, String oldName) {
        return getProperty(name, oldName, null);
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    public String getSuffixesExtExplicit(String name) {
        return properties.get(name.replace("suffixes", "suffixesExt"));
    }

    public char[] getExchanges(String recordName, String oldName, String defaultExchanges) {
        if (defaultExchanges == null) {
            defaultExchanges = properties.getOrDefault(
                DXSCHEME_EXCHANGES_PROPERTY, MarketEventSymbols.DEFAULT_EXCHANGES);
        }
        String name = DXSCHEME_EXCHANGES_PROPERTY + "." + recordName;
        String pattern = getProperty(name, oldName, defaultExchanges);
        return MarketEventSymbols.getExchangesByPattern(pattern).toCharArray();
    }

    public SerialFieldType selectDecimal(SerialFieldType defaultType, String... typeSelectors) {
        if (isWide())
            defaultType = SerialFieldType.WIDE_DECIMAL;
        SerialFieldType type = selectDecimalExplicit(typeSelectors);
        return (type != null) ? type : defaultType;
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    @Nullable
    public SerialFieldType selectDecimalExplicit(String... typeSelectors) {
        SerialFieldType type = null;
        for (int i = typeSelectors.length; --i >= 0; ) {
            String selector = getProperty(typeSelectors[i], null);
            if ("wide".equalsIgnoreCase(selector))
                type = SerialFieldType.WIDE_DECIMAL;
            if ("tiny".equalsIgnoreCase(selector) || "decimal".equalsIgnoreCase(selector))
                type = SerialFieldType.DECIMAL;
            if ("int".equalsIgnoreCase(selector))
                type = SerialFieldType.COMPACT_INT;
        }
        return type;
    }

    public SerialFieldType selectTime(SerialFieldType defaultType, String... typeSelectors) {
        SerialFieldType type = selectTimeExplicit(typeSelectors);
        return (type != null) ? type : defaultType;
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    @Nullable
    public SerialFieldType selectTimeExplicit(String... typeSelectors) {
        SerialFieldType type = null;
        // As opposed to decimal fields, we don't have a scheme-wide property for the moment
        for (int i = typeSelectors.length; --i >= 0; ) {
            String selector = getProperty(typeSelectors[i], null);
            if ("millis".equalsIgnoreCase(selector))
                type = SerialFieldType.TIME_MILLIS;
            if ("seconds".equalsIgnoreCase(selector))
                type = SerialFieldType.TIME_SECONDS;
            // FIXME: Doesn't work in DXFeed API
            // if ("none".equalsIgnoreCase(selector))
            //    type = SerialFieldType.VOID;
        }
        return type;
    }

    public boolean getBooleanProperty(String key, boolean defValue) {
        try {
            String propStr = properties.get(key);
            return propStr == null ? defValue : SystemProperties.parseBooleanValue(propStr);
        } catch (Throwable t) {
            log.error("Failed to acquire correct value for \"" + key + "\" boolean system property", t);
            return defValue;
        }
    }

    // Explicit method for XML Scheme that has its defaults in the XML scheme
    public Boolean getBooleanPropertyExplicit(String key) {
        try {
            String propStr = properties.get(key);
            return propStr != null ? SystemProperties.parseBooleanValue(propStr) : null;
        } catch (Throwable t) {
            log.error("Failed to acquire correct value for \"" + key + "\" boolean system property", t);
            return null;
        }
    }

    public static String combineSuffixes(String suffixes, String suffixesExt) {
        if (suffixesExt != null) {
            return (suffixes != null) ? suffixes + "|" + suffixesExt : suffixesExt;
        }
        return suffixes;
    }

    /**
     * Get record suffixes for publishable order sources of specified type as a string delimited by '|' (pipe) symbol.
     *
     * @param eventType eventType with possible values <code>{@link Order}.<b>class</b></code>,
     *     <code>{@link AnalyticOrder}.<b>class</b></code>, <code>{@link OtcMarketsOrder}.<b>class</b></code>
     *     <code>{@link SpreadOrder}.<b>class</b></code>, <code>{@link NuamOrder}.<b>class</b></code> or
     *     <code>{@link OrderImbalance}.<b>class</b></code>.
     * @return a list of publishable record suffixes delimited by '|' symbol.
     * @see OrderSource#publishable(Class)
     */
    private static String getDefaultOrderSuffixes(Class<? extends MarketEvent> eventType) {
        return OrderSource.publishable(eventType).stream()
            .filter(os -> !OrderSource.DEFAULT.equals(os) && !OrderSource.isSpecialSourceId(os.id()))
            .map(orderSource -> "|#" + orderSource.name()).collect(Collectors.joining());
    }

    private static String getFullOrderBookSuffixes() {
        return OrderSource.fullOrderBook().stream()
            .filter(os -> !OrderSource.DEFAULT.equals(os) && !OrderSource.isSpecialSourceId(os.id()))
            .map(orderSource -> "|#" + orderSource.name())
            .collect(Collectors.joining());
    }
}
