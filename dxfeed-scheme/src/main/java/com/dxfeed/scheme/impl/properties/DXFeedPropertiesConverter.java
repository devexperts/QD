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
package com.dxfeed.scheme.impl.properties;

import com.devexperts.annotation.Internal;
import com.devexperts.logging.Logging;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.util.GlobListUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.SchemeProperties;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.model.NamedEntity;
import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecordGenerator;
import com.dxfeed.scheme.model.SchemeType;
import com.dxfeed.scheme.model.VisibilityRule;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

@Internal
public final class DXFeedPropertiesConverter {

    private static final Logging log = Logging.getLogging(DXFeedPropertiesConverter.class);

    /**
     * This code must generate type overrides and visibility rules equal to
     * {@link com.dxfeed.api.impl.SchemeProperties}.
     */
    public static SchemeModel convertProperties(EmbeddedTypes embeddedTypes, Properties properties) {
        try {
            SchemeProperties schemeProperties = new SchemeProperties(properties);
            SchemeModel file = SchemeModel.newBuilder()
                .withTypes(embeddedTypes)
                .withName("<dxfeed-properties>")
                .build();

            loadTypeOverrides(file, schemeProperties);
            loadRegionalsVisibility(file, schemeProperties);
            loadOrderFieldsVisibility(file, schemeProperties);
            loadGenerators(file, schemeProperties);
            loadGenericFieldsVisibility(file, schemeProperties);

            return file.isEmpty() ? null : file;
        } catch (Throwable t) {
            log.error("Cannot convert properties to scheme configuration: " + t.getMessage());
            return null;
        }
    }

    /**
     * Disable all disabled regionals, as XML doesn't support changing list of regional letters
     */
    private static void loadRegionalsVisibility(SchemeModel file, SchemeProperties properties) {
        loadRegionalRecordVisibility(file, properties, "Quote", null);
        loadRegionalRecordVisibility(file, properties, "Trade", null);
        loadRegionalRecordVisibility(file, properties, "TradeETH", null);
        loadRegionalRecordVisibility(file, properties, "Summary", null);
        loadRegionalRecordVisibility(file, properties, "Fundamental", null);
        loadRegionalRecordVisibility(file, properties, "TimeAndSale", null);
        loadRegionalRecordVisibility(file, properties, "Book", "I");
    }

    private static void loadRegionalRecordVisibility(SchemeModel file, SchemeProperties properties,
        String rec, String defaultExchanges)
    {
        //FIXME NuamTrade and NuamTradeAndSale are not covered by the current code!
        String oldName = "com.dxfeed.event.market.impl." + rec + ".exchanges";
        String toEnable = new String(properties.getExchanges(rec, oldName, defaultExchanges));

        // Disable all
        file.addVisibilityRule(new VisibilityRule(
            Pattern.compile(rec + "&."),
            false,
            null,
            false,
            file.getName()
        ));

        // Enable only specified ones
        if (!toEnable.isEmpty()) {
            file.addVisibilityRule(new VisibilityRule(
                Pattern.compile(rec + "&[" + toEnable + "]"),
                false,
                null,
                true,
                file.getName()
            ));
        }
    }

    private static void loadOrderFieldsVisibility(SchemeModel file, SchemeProperties properties) {
        enableOrderField(file, properties, "Order", "Count", null);
        enableOrderField(file, properties, "AnalyticOrder", "Count", null);
        enableOrderField(file, properties, "OtcMarketsOrder", "Count", null);
        enableOrderField(file, properties, "NuamOrder", "Count", null);
        enableOrderField(file, properties, "SpreadOrder", "Count", null);

        // Enable MMID
        enableOrderField(file, properties, "Order", "MarketMaker", "mmid");
        enableOrderField(file, properties, "AnalyticOrder", "MarketMaker", "mmid");
        enableOrderField(file, properties, "OtcMarketsOrder", "MarketMaker", "mmid");

        // Global FOB flag, default to false
        if (properties.isFob()) {
            // List of suffixes to enable FOB for
            String fobSuffixes = properties.getFobSuffixes();
            enableFOB(file, fobSuffixes, "Order");
            enableFOB(file, fobSuffixes, "AnalyticOrder");
            enableFOB(file, fobSuffixes, "OtcMarketsOrder");
            enableFOB(file, fobSuffixes, "NuamOrder");
            enableFOB(file, fobSuffixes, "SpreadOrder");
        }
    }

    private static void enableOrderField(SchemeModel file, SchemeProperties properties,
        String rec, String field, String oldField)
    {
        //FIXME NuamOrder is not covered by the current code!
        String propertyName = SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + "." + rec + "." + field.toLowerCase();
        String oldPropertyName = "com.dxfeed.event.order.impl." + rec + ".suffixes." +
            ((oldField != null) ? oldField : field.toLowerCase());

        String suffixes = properties.getSuffixesExplicit(propertyName, oldPropertyName);
        String suffixesExt = properties.getSuffixesExtExplicit(propertyName);
        if (suffixes == null && suffixesExt == null) {
            return;
        }

        if (suffixes != null) {
            // Disable all counts to be sure, that we override XML defaults
            file.addVisibilityRule(new VisibilityRule(
                Pattern.compile(rec + "(|#.+)"),
                false,
                Pattern.compile(field),
                false,
                file.getName()
            ));
        }

        // Enable explicitly
        String combinedSuffixes = SchemeProperties.combineSuffixes(suffixes, suffixesExt);
        file.addVisibilityRule(new VisibilityRule(
            Pattern.compile(rec + (combinedSuffixes.isEmpty() ? "" : "(" + combinedSuffixes + ")")),
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

    private static void loadGenerators(SchemeModel file, SchemeProperties properties) {
        loadGenerator(file, properties, "market.impl.Order", "Order", "#");
        loadGenerator(file, properties, "market.impl.AnalyticOrder", "AnalyticOrder", "#");
        loadGenerator(file, properties, "market.impl.OtcMarketsOrder", "OtcMarketsOrder", "#");
        loadGenerator(file, properties, "custom.impl.NuamOrder", "NuamOrder", "#");
        loadGenerator(file, properties, "market.impl.SpreadOrder", "SpreadOrder", "#");
        loadGenerator(file, properties, "market.impl.OrderImbalance", "OrderImbalance", "#");
        loadGenerator(file, properties, "candle.impl.Candle", "Candle", "");
        loadGenerator(file, properties, "candle.impl.Trade", "OldStyleCandle", "");
    }

    private static void loadGenerator(SchemeModel file, SchemeProperties properties,
        String propName, String genName, String delimiter)
    {
        // Do nothing if property is not set, default is in XML file
        String recordName = propName.substring(propName.lastIndexOf(".") + 1);
        String propertyName = SchemeProperties.DXSCHEME_SUFFIXES_PROPERTY + "." + recordName;
        String oldPropertyName = "com.dxfeed.event." + propName + ".suffixes";

        String suffixes = properties.getSuffixesExplicit(propertyName, oldPropertyName);
        String suffixesExt = properties.getSuffixesExtExplicit(propertyName);
        if (suffixes == null && suffixesExt == null) {
            return;
        }

        try {
            // Replace existing suffixes
            if (suffixes != null) {
                SchemeRecordGenerator replaceGenerator = new SchemeRecordGenerator(genName, NamedEntity.Mode.UPDATE,
                    "Automatically created from environment, using property " + propertyName,
                    file.getName());
                replaceGenerator.setIteratorMode(SchemeRecordGenerator.IteratorMode.REPLACE);
                iterateSuffixes(replaceGenerator, delimiter, suffixes);
                file.addGenerator(replaceGenerator);
            }

            // Add suffixes to the existing ones
            if (suffixesExt != null) {
                SchemeRecordGenerator appendGenerator = new SchemeRecordGenerator(genName, NamedEntity.Mode.UPDATE,
                    "Automatically created from environment, using property " + propertyName,
                    file.getName());
                appendGenerator.setIteratorMode(SchemeRecordGenerator.IteratorMode.APPEND);
                iterateSuffixes(appendGenerator, delimiter, suffixesExt);
                file.addGenerator(appendGenerator);
            }
        } catch (SchemeException e) {
            log.error("Cannot create set of suffixes from " + propertyName + ": " + e.getMessage());
        }
    }

    private static void iterateSuffixes(SchemeRecordGenerator gen, String delimiter, String suffixes) {
        for (String s : suffixes.split("\\|")) {
            // Remove delimiter
            if (!delimiter.isEmpty() && s.startsWith(delimiter)) {
                s = s.substring(delimiter.length());
            }
            gen.addIteratorValue(s);
        }
    }

    private static void loadGenericFieldsVisibility(SchemeModel file, SchemeProperties properties) {
        Set<String> seenPropNames = new HashSet<>();

        properties.getProperties().forEach((key, value) -> {
            if (key.startsWith(DXEndpoint.DXSCHEME_ENABLED_PROPERTY_PREFIX)) {
                String propertyName = key.substring(DXEndpoint.DXSCHEME_ENABLED_PROPERTY_PREFIX.length());
                if (seenPropNames.add(propertyName)) {
                    file.addVisibilityRule(
                        new VisibilityRule(GlobListUtil.compile(value), true,
                            Pattern.compile(Pattern.quote(propertyName)), true, file.getName())
                    );
                }
            }
        });
        if (properties.getBooleanProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, false)) {
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

    private static void loadTypeOverrides(SchemeModel file, SchemeProperties properties) throws SchemeException {
        // Keep in sync with dynamic type definitions in com.dxfeed.api.codegen.FieldType
        overrideOneType(file, properties, "price", "decimal", "dxscheme.price");
        overrideOneType(file, properties, "size", "compact_int", "dxscheme.size");
        overrideOneType(file, properties, "volume", "decimal", "dxscheme.volume", "dxscheme.size");
        overrideOneType(file, properties, "turnover", "decimal", "dxscheme.turnover", "dxscheme.size");
        // open_interest is simple int_or_decimal
        overrideOneType(file, properties, "oi", "decimal", "dxscheme.oi");
        // Convert "decimal" into "wide_decimal" or "tiny_decimal"
        overrideOneType(file, properties, "decimal", "tiny_decimal");
        // Convert "int_or_decimal" to "wide_decimal" or "compact_int"
        overrideOneType(file, properties, "int_or_decimal", "compact_int");

        // Convert "bid_ask_time" (default "time_seconds")
        overrideTimeType(file, properties, "bid_ask_time", "time_seconds", "dxscheme.bat");
    }

    private static void overrideOneType(SchemeModel file, SchemeProperties properties,
        String name, String defaultType, String... typeSelectors)
        throws SchemeException
    {
        // Select types based on dxscheme.wide only if explicitly set
        Boolean isWide = properties.isWideExplicit();
        String targetType =
            (isWide == Boolean.TRUE) ? "wide_decimal" :
            (isWide == Boolean.FALSE) ? defaultType :
            null;

        SerialFieldType fieldType = properties.selectDecimalExplicit(typeSelectors);
        if (fieldType == SerialFieldType.WIDE_DECIMAL) {
            targetType = "wide_decimal";
        } else if (fieldType == SerialFieldType.DECIMAL) {
            targetType = "tiny_decimal";
        } else if (fieldType == SerialFieldType.COMPACT_INT) {
            targetType = "compact_int";
        }

        if (targetType != null) {
            file.addType(new SchemeType(name, NamedEntity.Mode.UPDATE, targetType,
                "Automatically created from environment properties", file.getName()));
        }
    }

    private static void overrideTimeType(SchemeModel file, SchemeProperties properties,
        String name, String defaultType, String... typeSelectors) throws SchemeException
    {
        String targetType = defaultType;

        SerialFieldType fieldType = properties.selectTimeExplicit(typeSelectors);
        if (fieldType == SerialFieldType.TIME_MILLIS) {
            targetType = "time_millis";
        } else if (fieldType == SerialFieldType.TIME_SECONDS) {
            targetType = "time_seconds";
        }

        if (targetType != null) {
            file.addType(new SchemeType(name, NamedEntity.Mode.UPDATE, targetType,
                "Automatically created from environment properties", file.getName()));
        }
    }
}
