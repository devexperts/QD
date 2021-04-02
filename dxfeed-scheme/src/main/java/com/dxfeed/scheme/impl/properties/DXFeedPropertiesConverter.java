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
package com.dxfeed.scheme.impl.properties;

import com.devexperts.qd.QDLog;
import com.devexperts.util.GlobListUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.SchemeLoadingOptions;
import com.dxfeed.scheme.model.NamedEntity;
import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecordGenerator;
import com.dxfeed.scheme.model.SchemeType;
import com.dxfeed.scheme.model.VisibilityRule;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static com.dxfeed.api.DXEndpoint.DXSCHEME_ENABLED_PROPERTY_PREFIX;
import static com.dxfeed.api.DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY;

public final class DXFeedPropertiesConverter {
    /**
     * This code must generate type overrides and visibility rules equal to
     * {@link com.dxfeed.api.impl.SchemeProperties} and
     * {@link com.dxfeed.api.impl.EventDelegateFactory}.select()
     * Also it should use all rules from {@link com.dxfeed.event.market.MarketFactoryImpl},
     * {@link com.dxfeed.event.candle.CandleFactoryImpl}, {@link com.dxfeed.event.misc.MiscFactoryImpl}
     * and {@link com.dxfeed.event.option.OptionFactoryImpl}.
     */
    public static SchemeModel convertProperties(EmbeddedTypes embeddedTypes, Properties dxFeedProps,
        SchemeLoadingOptions options)
    {
        if (!options.shouldUseSystemProperties() && !options.shouldUseDXFeedProperties()) {
            return null;
        }
        try {
            SchemeModel file = SchemeModel.newBuilder()
                .withTypes(embeddedTypes)
                .withName("<dxfeed-properties>")
                .build();

            if (options.shouldUseSystemProperties()) {
                loadTypeOverrides(file);
                loadRegionalsVisibility(file);
                loadOrderFieldsVisibility(file);
                loadGenerators(file);
            }
            // Only this code uses passed properties, all other
            // settings depends on system properties
            if (options.shouldUseDXFeedProperties()) {
                loadGenericFieldsVisibility(file, dxFeedProps);
            }
            return file.isEmpty() ? null : file;
        } catch (Throwable t) {
            QDLog.log.error("Cannot convert properties to scheme configuration: " + t.getMessage());
            return null;
        }
    }

    /**
     * Disable all disabled regionals, as XML doesn't support changing list of regional letters
     */
    private static void loadRegionalsVisibility(SchemeModel file) {
        loadRegionalRecordVisibility(file, "Quote");
        loadRegionalRecordVisibility(file, "Trade");
        loadRegionalRecordVisibility(file, "TradeETH");
        loadRegionalRecordVisibility(file, "Summary");
        loadRegionalRecordVisibility(file, "Fundamental");
        loadRegionalRecordVisibility(file, "TimeAndSale");
        loadRegionalRecordVisibility(file, "Book");
    }

    private static void loadRegionalRecordVisibility(SchemeModel file, String rec) {
        String prop = "com.dxfeed.event.market.impl." + rec + ".exchanges";

        // No property — use default (all letters), do nothing here
        String toEnable = SystemProperties.getProperty(prop, null);
        if (toEnable == null) {
            return;
        }

        // Disable all which is not mentioned explicitly
        StringBuilder toDisable = new StringBuilder();
        for (char exchange : "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()) {
            if (toEnable.indexOf(exchange) == -1) {
                toDisable.append(exchange);
            }
        }
        if (toDisable.length() == 0) {
            return;
        }
        // Create visibility rule to disable all not-found characters and enable all found explicitly
        if (toEnable.length() > 0) {
            file.addVisibilityRule(new VisibilityRule(
                Pattern.compile(rec + "&[" + toEnable + "]"),
                false,
                null,
                true,
                file.getName()
            ));
        }
        if (toDisable.length() > 0) {
            file.addVisibilityRule(new VisibilityRule(
                Pattern.compile(rec + "&[" + toDisable.toString() + "]"),
                false,
                null,
                false,
                file.getName()
            ));
        }
    }

    private static void loadOrderFieldsVisibility(SchemeModel file) {
        // Enable/disable Count fields
        enableOrderField(file, "Order", "Count");
        enableOrderField(file, "AnalyticOrder", "Count");
        enableOrderField(file, "SpreadOrder", "Count");
        // Enable MMID
        enableOrderField(file, "Order", "MarketMaker");
        enableOrderField(file, "AnalyticOrder", "MarketMaker");
        // Global FOB flag, default to false
        if (SystemProperties.getBooleanProperty("dxscheme.fob", false)) {
            // List of suffixes to enable FOB for
            String suffixes = SystemProperties.getProperty("com.dxfeed.event.market.impl.Order.fob.suffixes", "|#NTV");
            enableFOB(file, suffixes, "Order");
            enableFOB(file, suffixes, "AnalyticOrder");
            enableFOB(file, suffixes, "SpreadOrder");
        }
    }

    private static void enableOrderField(SchemeModel file, String rec, String field) {
        String prop = "com.dxfeed.event.order.impl." + rec + ".suffixes." + field.toLowerCase();
        String suffixes = SystemProperties.getProperty(prop, null);
        if (suffixes == null) {
            return;
        }
        // Disable all counts to be sure, that we override XML defaults
        file.addVisibilityRule(new VisibilityRule(
            Pattern.compile(rec + "(|#.+)"),
            false,
            Pattern.compile(field),
            false,
            file.getName()
        // Enable explicitly
        ));
        file.addVisibilityRule(new VisibilityRule(
            Pattern.compile(rec + (suffixes.isEmpty() ? "" : "(" + suffixes + ")")),
            false,
            Pattern.compile(field),
            true,
            file.getName()
        ));
    }

    private static void enableFOB(SchemeModel file, String suffixes, String rec) {
        VisibilityRule vr = new VisibilityRule(
            Pattern.compile(rec + (suffixes.isEmpty() ? "" : "(" + suffixes + ")")),
            false,
            null,
            true,
            file.getName()
        );
        // Enable all FOB fields
        vr.addIncludedTag("fob");
        file.addVisibilityRule(vr);
    }

    private static void loadGenerators(SchemeModel file) {
        loadGenerator(file, "market.impl.Order", "Order", "#");
        loadGenerator(file, "market.impl.AnalyticOrder", "AnalyticOrder", "#");
        loadGenerator(file, "market.impl.SpreadOrder", "SpreadOrder", "#");
        loadGenerator(file, "candle.impl.Candle", "Candle", "");
        loadGenerator(file, "candle.impl.Trade", "OldStyleCandle", "");
    }

    private static void loadGenerator(SchemeModel file, String propName, String genName, String delimiter) {
        String prop = "com.dxfeed.event." + propName + ".suffixes";
        String suffixes = SystemProperties.getProperty(prop, null);
        // Do nothing if property is not set, default is in XML file
        if (suffixes == null) {
            return;
        }
        SchemeRecordGenerator gen = new SchemeRecordGenerator(genName,
            NamedEntity.Mode.UPDATE,
            "Automatically created from environment, using property " + prop,
            file.getName());
        // Create iterator
        gen.setIteratorMode(SchemeRecordGenerator.IteratorMode.REPLACE);
        for (String s : suffixes.split("\\|")) {
            // Remove delimiter
            if (!delimiter.isEmpty() && s.startsWith(delimiter)) {
                s = s.substring(delimiter.length());
            }
            gen.addIteratorValue(s);
        }
        try {
            file.addGenerator(gen);
        } catch (SchemeException e) {
            QDLog.log.error("Cannot create set of suffixes from " + prop + ": " + e.getMessage());
        }
    }

    /**
     * This code adds visibility rules with same logic as
     * which is used by {@link com.dxfeed.api.impl.SchemeProperties}
     */
    private static void loadGenericFieldsVisibility(SchemeModel file, Properties props) {
        Set<String> seenPropNames = new HashSet<>();
        props.forEach((objKey, objValue) -> {
            String key = (String) objKey;
            if (key.startsWith(DXSCHEME_ENABLED_PROPERTY_PREFIX)) {
                String propertyName = key.substring(DXSCHEME_ENABLED_PROPERTY_PREFIX.length());
                if (seenPropNames.add(propertyName)) {
                    file.addVisibilityRule(
                        new VisibilityRule(GlobListUtil.compile((String) objValue), true,
                            Pattern.compile(Pattern.quote(propertyName)), true, file.getName())
                    );
                }
            }
        });
        if (Boolean.parseBoolean(props.getProperty(DXSCHEME_NANO_TIME_PROPERTY))) {
            if (seenPropNames.add("Sequence")) {
                file.addVisibilityRule(
                    new VisibilityRule(Pattern.compile(".*"), true, Pattern.compile(Pattern.quote("Sequence")), true,
                        file.getName())
                );
            }
            if (seenPropNames.add("TimeNanoPart")) {
                file.addVisibilityRule(
                    new VisibilityRule(Pattern.compile(".*"), true, Pattern.compile(Pattern.quote("TimeNanoPart")),
                        true, file.getName())
                );
            }
        }
    }

    /**
     * This code re-defines several types based on same properties
     * which is used by {@link com.dxfeed.api.impl.EventDelegateFactory}.select()
     */
    private static void loadTypeOverrides(SchemeModel file) throws SchemeException {
        overrideOneType(file, "price", "dxscheme.price");
        overrideOneType(file, "size", "dxscheme.size");
        overrideOneType(file, "volume", "dxscheme.volume", "dxscheme.size");
        overrideOneType(file, "turnover", "dxscheme.turnover", "dxscheme.size");
        // open_interest is simple int_or_decimal
        overrideOneType(file, "oi", "dxscheme.oi");
        // Convert "decimal" into "wide_decimal" or "tiny_decimal"
        overrideOneType(file, "decimal");
        // Convert "int_or_decimal" to "wide_decimal" or "compact_int"
        overrideOneType(file, "int_or_decimal");
    }

    private static void overrideOneType(SchemeModel file, String name, String... typeSelectors) throws SchemeException {
        String targetType = null;
        String reason = null;

        if (SystemProperties.getBooleanProperty("dxscheme.wide", true)) {
            targetType = "wide_decimal";
            reason = "dxscheme.wide=true or default";
        }

        /*
        This code was copied from EventDelegateFactory.select() and replicates
        its backward looping.
         */
        for (int i = typeSelectors.length; --i >= 0;) {
            String selector = System.getProperty(typeSelectors[i]);
            if ("wide".equalsIgnoreCase(selector)) {
                targetType = "wide_decimal";
                reason = typeSelectors[i] + "=" + selector;
            }
            if ("tiny".equalsIgnoreCase(selector) || "decimal".equalsIgnoreCase(selector)) {
                targetType = "tiny_decimal";
                reason = typeSelectors[i] + "=" + selector;
            }
            if ("int".equalsIgnoreCase(selector)) {
                targetType = "compact_int";
                reason = typeSelectors[i] + "=" + selector;
            }
        }
        if (targetType != null) {
            file.addType(new SchemeType(name, NamedEntity.Mode.UPDATE, targetType,
                "Automatically created from environment, using property " + reason, file.getName()));
        }
    }
}
