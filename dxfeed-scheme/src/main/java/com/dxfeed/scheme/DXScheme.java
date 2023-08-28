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
package com.dxfeed.scheme;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.ConfigurableDataScheme;
import com.dxfeed.api.impl.VersionedRecord;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.scheme.impl.SchemeModelLoader;
import com.dxfeed.scheme.impl.properties.DXFeedPropertiesConverter;
import com.dxfeed.scheme.model.SchemeModel;
import com.dxfeed.scheme.model.SchemeRecord;
import com.dxfeed.scheme.model.SchemeRecordGenerator;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Implementation of {@link DataScheme} which creates all records from
 * external scheme model.
 * <p>
 * This scheme hasn't any pre-defined records in it. All records
 * must be loaded from external files.
 * <p>
 * To load data scheme use {@link DXScheme.Loader}.
 */
public class DXScheme extends DefaultScheme implements ConfigurableDataScheme {
    private static final boolean[] REGIONAL_RECORDS_BUILD_ORDER = new boolean[] { false, true };

    private static final Properties DEFAULT_PROPERTIES = System.getProperties();

    private final SchemeModel model;
    private final SchemeLoadingOptions options;

    public static Loader newLoader() {
        return new Loader();
    }

    private DXScheme(SchemeModel model, SchemeLoadingOptions options, Properties properties)
        throws SchemeException
    {
        super(PentaCodec.INSTANCE, loadRecords(model, options, properties));
        this.model = model;
        this.options = options;
    }

    @Override
    @Deprecated
    public DXScheme withProperties(Properties properties) {
        // Only "loadDXFeedProperties" now uses passed properties.
        // If it is disabled, do nothing as passed properties could change nothing
        if (!options.shouldUseDXFeedProperties()) {
            return this;
        }
        try {
            return new DXScheme(this.model, options, properties);
        } catch (SchemeException e) {
            throw new IllegalArgumentException("Can not rebuild scheme: " + e.getMessage());
        }
    }

    private static DataRecord[] loadRecords(SchemeModel model, SchemeLoadingOptions options, Properties properties)
        throws SchemeException
    {
        SchemeModel merged = model;
        // We need to load properties if they are not default OR options is enabled
        if (properties == null) {
            properties = DEFAULT_PROPERTIES;
        }
        SchemeModel propsModel =
            DXFeedPropertiesConverter.convertProperties(model.getEmbeddedTypes(), properties, options);
        if (propsModel != null) {
            merged = SchemeModel.newBuilder()
                .withName("<with-props>")
                .withTypes(model.getEmbeddedTypes())
                .build();
            merged.override(model);
            merged.override(propsModel);
            // Perform validation of merged model to re-resolve types
            List<SchemeException> errors = merged.validateState();
            // Check errors and bail out
            if (!errors.isEmpty()) {
                // Throw wrapping exception
                throw new SchemeException(errors, merged.getSources());
            }
        }

        List<DataRecord> result = new ArrayList<>();
        // Enumerate all record sources, build non-regional first, regional second
        for (boolean regional : REGIONAL_RECORDS_BUILD_ORDER) {
            for (SchemeModel.RecordSource src : merged.getRecordGenerationSources()) {
                switch (src.getSource()) {
                    case RECORD:
                        SchemeRecord r = merged.getRecords().get(src.getName());
                        buildRecord(result, merged, r, r.getName(), regional);
                        break;
                    case GENERATOR:
                        SchemeRecordGenerator g = merged.getGenerators().get(src.getName());
                        for (SchemeRecord t : g.getTemplates()) {
                            for (String actualRecordName : g.getRecordsNames(t)) {
                                buildRecord(result, merged, t, actualRecordName, regional);
                            }
                        }
                        break;
                }
            }
        }

        return result.toArray(new DataRecord[0]);
    }

    private static void buildRecord(List<DataRecord> result, SchemeModel scheme, SchemeRecord r, String recordName,
        boolean buildRegionals) throws SchemeException
    {
        DataRecord dr;
        if (r.hasRegionals() && buildRegionals) {
            for (char exchange : MarketEventSymbols.SUPPORTED_EXCHANGES.toCharArray()) {
                dr = buildOneRecord(result.size(), scheme, r, recordName + '&' + exchange, true);
                if (dr != null) {
                    result.add(dr);
                }
            }
        } else if (!buildRegionals) {
            dr = buildOneRecord(result.size(), scheme, r, recordName, false);
            if (dr != null) {
                result.add(dr);
            }
        }
    }

    private static DataRecord buildOneRecord(int idx, SchemeModel scheme, SchemeRecord r,
        String actualRecordName, boolean isRegional) throws SchemeException
    {
        // Skip disabled records
        if (!scheme.isRecordEnabled(r, actualRecordName)) {
            return null;
        }

        List<DataIntField> intFields = new ArrayList<>();
        List<DataObjField> objFields = new ArrayList<>();

        String i1 = r.getIndex1();
        String i2 = r.getIndex2();
        boolean hasTime = i1 != null || i2 != null;

        // Special fields must be defined first
        if (hasTime) {
            boolean hasRealTimeField;
            hasRealTimeField = addIndexField(intFields, scheme, r, actualRecordName, i1, isRegional);
            // We don't need shortcuts here, so call function first.
            hasRealTimeField = addIndexField(intFields, scheme, r, actualRecordName, i2, isRegional) ||
                hasRealTimeField;
            if (!hasRealTimeField) {
                throw new SchemeException(SchemeException.formatInconsistencyMessage(r,
                    "All index fields have been disabled"), r.getFilesList());
            }
        }

        Map<String, List<String>> aliases = new HashMap<>();
        for (SchemeRecord.Field f : r.getFields()) {
            // Skip composite-only fields from regional record
            if (f.isCompositeOnly() && isRegional) {
                continue;
            }
            // Skip disabled fields
            if (!scheme.isRecordFieldEnabled(actualRecordName, f)) {
                continue;
            }

            String fieldName = actualRecordName + "." + f.getMainAlias();
            // Add aliases into map for any enabled fieled
            aliases.put(fieldName, new ArrayList<>());
            for (SchemeRecord.Field.Alias a : f.getAliases()) {
                aliases.get(fieldName).add(a.getValue());
            }
            aliases.get(fieldName).add(f.getName());

            // Skip index fields, they are defined already
            if (f.getName().equals(i1) || f.getName().equals(i2)) {
                continue;
            }

            // Add field
            SerialFieldType type = scheme.resolveType(f.getType());
            if (type.isObject()) {
                DataObjField field = type.createDefaultObjInstance(objFields.size(), fieldName);
                if (field == null) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(f,
                        "Cannot construct field of type " + type), f.getFilesList());
                }
                objFields.add(field);
            } else {
                intFields.add(type.createDefaultIntInstance(intFields.size(), fieldName));
                if (type.isLong()) {
                    intFields.add(SerialFieldType.VOID.createDefaultIntInstance(intFields.size(),
                        fieldName + "$VoidTail"));
                }
            }
        }

        DefaultRecord rec;
        // Very special hack
        if (actualRecordName.equals("Configuration")) {
            rec = new VersionedRecord(idx, actualRecordName, hasTime, intFields.toArray(new DataIntField[0]),
                objFields.toArray(new DataObjField[0]), "Version");
        } else {
            rec = new DefaultRecord(idx, actualRecordName, hasTime, intFields.toArray(new DataIntField[0]),
                objFields.toArray(new DataObjField[0]));
        }

        for (String fieldName : aliases.keySet()) {
            for (String a : aliases.get(fieldName)) {
                rec.addFieldAlias(fieldName, a);
            }
        }

        return rec;
    }

    private static boolean addIndexField(List<DataIntField> fields, SchemeModel scheme, SchemeRecord r,
        String actualRecordName, String index, boolean isRegional)
    {
        SchemeRecord.Field f = null;
        if (index != null) {
            f = r.getField(index);
            if (!isFieldEnabled(scheme, r, actualRecordName, f, isRegional)) {
                f = null;
            }
        }

        boolean realField;
        if (f != null) {
            // Add real field
            SerialFieldType type = scheme.resolveType(f.getType());
            fields.add(type.createDefaultIntInstance(fields.size(), actualRecordName + "." + f.getMainAlias()));
            realField = true;
        } else {
            // Add synthetic field
            fields.add(
                SerialFieldType.VOID.createDefaultIntInstance(fields.size(), actualRecordName + ".$VoidTimeField"));
            realField = false;
        }

        return realField;
    }

    private static boolean isFieldEnabled(SchemeModel scheme, SchemeRecord r, String actualRecordName,
        SchemeRecord.Field field, boolean isRegional)
    {
        return (!field.isCompositeOnly() || !isRegional) &&
            scheme.isRecordFieldEnabled(actualRecordName, field);
    }

    /**
     * Builder class for {@link DXScheme} that supports additional configuration properties.
     */
    public static final class Loader {
        private EmbeddedTypes embeddedTypes = SchemeModelLoader.DEFAULT_TYPES;
        private SchemeLoadingOptions options = new SchemeLoadingOptions();
        private final Properties properties = new Properties();
        private final List<URL> urls = new ArrayList<>();

        private Loader() {
        }

        /**
         * Changes provider of embedded types known to the scheme. Default type provider
         * supports all public types implemented by {@link SerialFieldType}.
         *
         * @param embeddedTypes new type provider implementation.
         * @return {@code this} scheme loader.
         */
        public Loader withTypes(EmbeddedTypes embeddedTypes) {
            this.embeddedTypes = Objects.requireNonNull(embeddedTypes, "embeddedTypesProvider");
            return this;
        }

        /**
         * Sets options for scheme loading.
         *
         * @param options options to use.
         * @return {@code this} scheme loader.
         */
        public Loader withOptions(SchemeLoadingOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        /**
         * Sets properties from provided properties object.
         * <p>
         * Properties, described in {@link DXEndpoint#DXSCHEME_NANO_TIME_PROPERTY} and
         * {@link DXEndpoint#DXSCHEME_ENABLED_PROPERTY_PREFIX} are supported for backward compatibility.
         * <p>
         * This method doesn't check content of {@code props} and doesn't warn or return error
         * if {@code props} contains unsupported properties.
         * <p>
         * User must specify custom options with enabled {@link SchemeLoadingOptions#useDXFeedProperties(boolean)}
         * to make these properties effective.
         *
         * @param props properties to use.
         * @return {@code this} scheme loader.
         * @deprecated Use custom scheme overlays instead of properties.
         */
        @Deprecated
        public Loader withProperties(Properties props) {
            for (String key : props.stringPropertyNames()) {
                withProperty(key, props.getProperty(key));
            }
            return this;
        }

        /**
         * Sets the specified property.
         * <p>
         * Properties, described in {@link DXEndpoint#DXSCHEME_NANO_TIME_PROPERTY} and
         * {@link DXEndpoint#DXSCHEME_ENABLED_PROPERTY_PREFIX} are supported for backward compatibility.
         * <p>
         * This method doesn't check {@code key} and doesn't warn or return error
         * if {@code key} specifies unsupported properties.
         * <p>
         * User must specify custom options with enabled {@link SchemeLoadingOptions#useDXFeedProperties(boolean)}
         * to make these properties effective.
         *
         * @param key name of property to set.
         * @param value value of property to set.
         * @return {@code this} scheme loader.
         * @deprecated Use custom scheme overlays.
         */
        @Deprecated
        public Loader withProperty(String key, String value) {
            this.properties.setProperty(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")
            );
            return this;
        }

        /**
         * Adds URL to load and parse.
         * <p>
         * This method don't override list of URLs, it adds one more URL at the end of existing list.
         * URL list is empty in the new loader.
         *
         * @param url URL to add.
         * @return {@code this} scheme loader.
         */
        public Loader fromURL(URL url) {
            this.urls.add(Objects.requireNonNull(url, "url"));
            return this;
        }

        /**
         * Adds URLs to load and parse.
         * <p>
         * This method don't override list of URLs, it adds one or more URLs at the end of existing list.
         * URL list is empty in the new loader.
         *
         * @param urls URLs to add.
         * @return {@code this} scheme loader.
         */
        public Loader fromURLs(Collection<URL> urls) {
            this.urls.addAll(Objects.requireNonNull(urls, "urls"));
            return this;
        }

        /**
         * Configures scheme loading from specification string.
         * <p>
         * This method don't override list of URLs, it adds zero or more URLs at the end of existing list.
         * URL list is empty in the new loader.
         * <p>
         * Specification string consist of URLs, shortcuts and options, delimited by comma ({@code ','}). Comma
         * must be escaped as {@code '%2C'} in URLs.
         * <p>
         * Each URL could be absolute or relative. Relative URLs are resolved from current directory. All schemes
         * supported by JDK are supported, and additionally special {@code 'resource'} scheme is supported.
         * Scheme {@code 'resource'} is resolved with current class loader. If provided {@code 'resource'}
         * URL is resolved into several resources, exception is thrown. URLs with {@code 'resource'} scheme must be
         * unique.
         * <p>
         * Only one shortcut is supported: {@code dxfeed}. This shortcut is replaced with URL of default
         * DXFeed-compatible scheme, provided in resources. Also, it turns on compatibility options with
         * {@link SchemeLoadingOptions#setDXFeedDefaults()}.
         * <p>
         * Options are specified as {@code opt:<options-string>}. See {@link SchemeLoadingOptions#applyOptions(String)}
         * for details.
         *
         * @param specification specification string.
         * @return {@code this} scheme loader.
         * @throws IOException when URL cannot be parsed or {@code resource} URL is resolved to multiple
         * files.
         */
        public Loader fromSpecification(String specification) throws IOException {
            this.urls.addAll(
                SchemeModelLoader.parseSpecification(
                    Objects.requireNonNull(specification, "specification"),
                    options)
            );
            return this;
        }

        /**
         * Loads, parses and prepares {@link DXScheme} from configured URLs with using configured options.
         * <p>
         * At least one URL must be configured via {@link #fromURL(URL)}, {@link #fromURLs(Collection)} or
         * {@link #fromSpecification(String)}.
         *
         * @return Data scheme.
         * @throws IOException if one of URLs cannot be read or contains syntactic errors.
         * @throws SchemeException if one of specified scheme files contains structural errors or files in the resulting
         * set are incompatible.
         */
        public DXScheme load() throws IOException, SchemeException {
            if (urls.isEmpty()) {
                throw new IllegalStateException("Cannot load scheme without URLs");
            }
            SchemeModel model = SchemeModelLoader.loadSchemeModel(urls, options, embeddedTypes);
            return new DXScheme(model, options, properties);
        }
    }
}
