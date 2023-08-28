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
package com.dxfeed.scheme.model;

import com.devexperts.qd.SerialFieldType;
import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;
import com.dxfeed.scheme.SchemeLoadingOptions;
import com.dxfeed.scheme.impl.SchemeModelLoader;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class is container for QDS Scheme model, which can be used to construct {@link com.dxfeed.scheme.DXScheme}.
 * <p>
 * Instances of this class are created via loading with {@link Loader} or building empty one via {@link Builder}.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeModel {
    /*
    TODO:
       [ ] Extract internal classes and enums.
       [ ] Think about Scheme/Name/Child entities, maybe collapse one level.
       [ ] Think about better reader/writer API?
     */

    /**
     * Source of one record.
     * <p>
     * This class is used to enumerate {@link SchemeRecord records} and {@link SchemeRecordGenerator generators}
     * in one collection to preserve their relative order.
     */
    public final static class RecordSource {
        /**
         * Type of source of the record, which determines meaning of {@link #getName() name} property.
         */
        public enum Type {
            /**
             * {@link #getName() Name} property references record.
             */
            RECORD,
            /**
             * {@link #getName() Name} property references record generator.
             */
            GENERATOR
        }

        private final Type type;
        private final String name;

        private RecordSource(Type type, String name) {
            this.type = Objects.requireNonNull(type, "type");
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Returns type of source of this record or records.
         */
        public Type getSource() {
            return type;
        }

        /**
         * Returns name of source of this record or records.
         */
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            RecordSource that = (RecordSource) o;
            return type == that.type && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }

    private final EmbeddedTypes embeddedTypes;
    private final String parent;
    private final String name;
    private final List<String> sources = new ArrayList<>();

    private final List<SchemeImport> imports = new ArrayList<>();
    private final Map<String, SchemeType> types = new HashMap<>();
    private final Map<String, SchemeEnum> enums = new HashMap<>();
    private final Map<String, SchemeRecord> records = new HashMap<>();
    private final Map<String, SchemeRecordGenerator> generators = new HashMap<>();
    private final Map<String, SchemeMapping> mappings = new HashMap<>();
    private final List<VisibilityRule> vrules = new ArrayList<>();
    private final List<RecordSource> generationOrder = new ArrayList<>();

    private int mergedIn = 0;

    /**
     * Creates new builder for model.
     *
     * @return new builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates new loader for model.
     *
     * @return new loader.
     */
    public static Loader newLoader() {
        return new Loader();
    }

    private SchemeModel(EmbeddedTypes embeddedTypes, String name, String parent) {
        this.embeddedTypes = Objects.requireNonNull(embeddedTypes, "embeddedTypes");
        this.parent = parent;
        this.name = Objects.requireNonNull(name, "name");
        this.sources.add(name);
    }

    /**
     * Returns name of this scheme model. Typically it is name of file this model was loaded from,
     * but it can be some identification string and not a file name for merged models.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns name of this scheme model's parent. Can be {@link null}.
     */
    public String getParent() {
        return parent;
    }

    /**
     * Returns list of sources from which this scheme model was created. For simple models loaded from a single file
     * it will be a list of size one, with the only element equals to name returned by {@link #getName()}.
     */
    public List<String> getSources() {
        return sources;
    }

    /**
     * Returns all {@link SchemeImport imports}, loaded for this model.
     */
    public Collection<SchemeImport> getImports() {
        return Collections.unmodifiableCollection(imports);
    }

    /**
     * Returns all {@link SchemeType custom types} defined by this model.
     */
    public Map<String, SchemeType> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    /**
     * Returns all {@link SchemeEnum enums} defined by this model.
     */
    public Map<String, SchemeEnum> getEnums() {
        return Collections.unmodifiableMap(enums);
    }

    /**
     * Returns all {@link SchemeRecord records} defined by this model, including disabled ones.
     */
    public Map<String, SchemeRecord> getRecords() {
        return Collections.unmodifiableMap(records);
    }

    /**
     * Returns all {@link SchemeRecordGenerator record generators} defined by this model.
     */
    public Map<String, SchemeRecordGenerator> getGenerators() {
        return Collections.unmodifiableMap(generators);
    }

    /**
     * Returns all {@link SchemeMapping record/event mappings} defined by this model.
     * <p>
     * Returns empty list for now, as mappings are not supported yet.
     */
    public Map<String, SchemeMapping> getMappings() {
        return Collections.unmodifiableMap(mappings);
    }

    /**
     * Returns all {@link VisibilityRule visibility rules} defined by this model.
     */
    public Collection<VisibilityRule> getVisibilityRules() {
        return Collections.unmodifiableCollection(vrules);
    }

    /**
     * Returns {@link RecordSource sources of records} — {@link SchemeRecord records} themselves or
     * {@link SchemeRecordGenerator generators} — in order in which they are
     * added to model with {@link #addRecord(SchemeRecord)} and {@link #addGenerator(SchemeRecordGenerator)} methods.
     * <p>
     * Use this method to enumerate all records (single and generated) in same order as they were added to the model.
     */
    public Collection<RecordSource> getRecordGenerationSources() {
        return Collections.unmodifiableCollection(generationOrder);
    }

    /**
     * Returns, does this scheme model contains any data or not.
     */
    public boolean isEmpty() {
        return imports.isEmpty() &&
            types.isEmpty() &&
            enums.isEmpty() &&
            records.isEmpty() &&
            generators.isEmpty() &&
            mappings.isEmpty() &&
            vrules.isEmpty();
    }

    /**
     * Checks, does this scheme model contains given type, either {@link EmbeddedTypes embedded} or
     * {@link SchemeType custom} one.
     *
     * @param name name of type.
     * @return {@code true} if type with given name is known by model, {@code false} otherwise.
     */
    public boolean hasType(String name) {
        return types.containsKey(name) || embeddedTypes.isKnownType(name);
    }

    /**
     * Returns {@link EmbeddedTypes registry of embedded types} used by this scheme model.
     */
    public EmbeddedTypes getEmbeddedTypes() {
        return embeddedTypes;
    }

    /**
     * Resolves type by name, using {@link SchemeType custom} or {@link EmbeddedTypes embedded} types.
     *
     * @param type name of type.
     * @return Internal type representation or {@code null} if type name is unknown.
     */
    public SerialFieldType resolveType(String type) {
        SchemeType t = types.get(type);
        if (t != null)
            return embeddedTypes.getSerialType(t.getResolvedType());
        return embeddedTypes.getSerialType(type);
    }

    /**
     * Adds {@link SchemeImport import} instruction to this scheme model.
     *
     * @param imp import instruction to add.
     */
    public void addImport(SchemeImport imp) {
        imports.add(Objects.requireNonNull(imp, "imp"));
    }

    /**
     * Adds new {@link SchemeType custom type} to this scheme model or updates existing one.
     * <p>
     * If {@link SchemeType type} with same name exists, existing type will be updated.
     * New enum will be added to model otherwise.
     * <p>
     * It is error to add type with name known to {@link EmbeddedTypes registry of embedded types}.
     *
     * @param type new custom type.
     * @throws SchemeException if type with given name is known as embedded one.
     */
    public void addType(SchemeType type) throws SchemeException {
        if (embeddedTypes.isKnownType(type.getName())) {
            throw new SchemeException(SchemeException.formatConflictMessage(type, name,
                "Type with name \"" + type.getName() + "\" conflicts with embedded type"), type.getFilesList());
        }

        SchemeType existingType = types.get(type.getName());
        if (existingType != null) {
            existingType.override(type);
        } else {
            types.put(type.getName(), type);
        }
        addNewSources(type);
    }

    /**
     * Adds new {@link SchemeEnum enum} to this scheme model or updates existing one.
     * <p>
     * If {@link SchemeEnum enum} with same name exists, existing enum will be updated.
     * New enum will be added to model otherwise.
     *
     * @param enm new enum.
     * @throws SchemeException if enum with same name exists and update fails.
     */
    public void addEnum(SchemeEnum enm) throws SchemeException {
        SchemeEnum existingEnum = enums.get(enm.getName());
        if (existingEnum != null) {
            existingEnum.override(enm);
        } else {
            enums.put(enm.getName(), enm);
        }
        addNewSources(enm);
    }

    /**
     * Adds new {@link SchemeRecord record} to this scheme model or updates existing one.
     * <p>
     * If {@link SchemeRecord record} with same name exists, existing record will be updated.
     * New record will be added to model otherwise.
     *
     * @param record new record.
     * @throws SchemeException if record with same name exists and update fails.
     */
    public void addRecord(SchemeRecord record) throws SchemeException {
        SchemeRecord existingRecord = records.get(record.getName());
        if (existingRecord != null) {
            existingRecord.override(record);
        } else {
            records.put(record.getName(), record);
            generationOrder.add(new RecordSource(RecordSource.Type.RECORD, record.getName()));
        }
        addNewSources(record);
    }

    /**
     * Adds new {@link SchemeRecordGenerator generator} to this scheme model or updates existing one.
     * <p>
     * If {@link SchemeRecordGenerator Generator} with same name exists, existing generator will be updated.
     * New generator will be added to model otherwise.
     *
     * @param generator new generator.
     * @throws SchemeException if generator with same name exists and update fails.
     */
    public void addGenerator(SchemeRecordGenerator generator) throws SchemeException {
        SchemeRecordGenerator existingGenerator = generators.get(generator.getName());
        if (existingGenerator != null) {
            existingGenerator.override(generator);
        } else {
            generators.put(generator.getName(), generator);
            generationOrder.add(new RecordSource(RecordSource.Type.GENERATOR, generator.getName()));
        }
        addNewSources(generator);
    }

    /**
     * Adds new {@link SchemeMapping mapping} to this scheme model or updates existing one.
     * <p>
     * If {@link SchemeMapping mapping} with same name exists, existing mapping will be updated.
     * New mapping will be added to model otherwise.
     * <p>
     * This method does nothing for now, as mappings are not supported yet.
     *
     * @param mapping new mapping.
     * @throws SchemeException if mapping with same name exists and update fails.
     */
    public void addMapping(SchemeMapping mapping) throws SchemeException {
        SchemeMapping existingMapping = mappings.get(mapping.getName());
        if (existingMapping != null) {
            existingMapping.override(mapping);
        } else {
            mappings.put(mapping.getName(), mapping);
        }
        addNewSources(mapping);
    }

    /**
     * Adds {@link VisibilityRule visibility rule} to this scheme model.
     *
     * @param rule rule to add.
     */
    public void addVisibilityRule(VisibilityRule rule) {
        vrules.add(rule);
    }

    /**
     * Merges this scheme model with a given one. Method updates the model in place, it does not create a new one.
     * <p>
     * Another model is viewed as an overlay, and all unique entities defined by it go after entities defined by
     * this model.
     *
     * @param other model to apply as overlay.
     * @throws SchemeException thrown when here are conflicts or inconsistencies between two models.
     */
    public void override(SchemeModel other) throws SchemeException {
        sources.addAll(other.getSources());
        imports.addAll(other.imports);
        overrideEntityCollection(types, other.types);
        overrideEntityCollection(enums, other.enums);
        overrideEntityCollection(records, other.records);
        overrideEntityCollection(generators, other.generators);
        overrideEntityCollection(mappings, other.mappings);
        vrules.addAll(other.vrules);

        Set<RecordSource> go = new HashSet<>(generationOrder);
        for (RecordSource rs : other.generationOrder) {
            if (go.add(rs)) {
                generationOrder.add(rs);
            }
        }

        // FIXME: currently validation is needed to re-resolve types.
        types.values().forEach(SchemeType::setUnresolved);
        mergedIn++;
    }

    /**
     * Returns number of overlays already applied to this model with {@link #override(SchemeModel)} method.
     */
    public int mergedInCount() {
        return mergedIn;
    }

    /**
     * Validates state of model, check cross-references, entity states, etc.
     *
     * @return List of exceptions generated by individual entities. This allows to report all errors in one call.
     */
    public List<SchemeException> validateState() {
        List<SchemeException> rv = new ArrayList<>();
        validateCollectionState(types.values(), rv);
        validateCollectionState(enums.values(), rv);
        validateCollectionState(records.values(), rv);
        validateCollectionState(generators.values(), rv);
        return rv;
    }

    /**
     * Checks if given record is enabled in this scheme model.
     * <p>
     * This method apply all visibility rules one by one, last matching visibility rule wins.
     *
     * @param rec record to check.
     * @param actualRecordName actual name of record, can be different from record's name property for generated or
     * regional records.
     * @return {@code true} if record is enabled, {@code false} otherwise.
     */
    public boolean isRecordEnabled(SchemeRecord rec, String actualRecordName) {
        boolean enabled = !rec.isDisabled();
        // Last win
        for (VisibilityRule r : vrules) {
            if (r.match(rec, actualRecordName)) {
                enabled = r.isEnable();
            }
        }
        return enabled;
    }

    /**
     * Checks if given record field is enabled in this scheme model.
     * <p>
     * This method apply all visibility rules one by one, last matching visibility rule wins.
     * <p>
     * This method doesn't check visibility of record itself.
     *
     * @param actualRecordName actual name of record, can be different from record's name property for generated or
     * regional records.
     * @param field record field to check.
     * @return {@code true} if field is enabled, {@code false} otherwise.
     */
    public boolean isRecordFieldEnabled(String actualRecordName, SchemeRecord.Field field) {
        boolean enabled = !field.isDisabled();
        // Last win
        for (VisibilityRule r : vrules) {
            if (r.match(actualRecordName, field)) {
                enabled = r.isEnable();
            }
        }
        return enabled;
    }

    /**
     * Marks that new singleton overlay was merged in via {@code addXXX()} methods.
     *
     * @param source source (name) of overlay.
     */
    public void finishMergeIn(String source) {
        addNewSourcesImpl(Collections.singletonList(source));
    }

    private <T extends SchemeEntity> void addNewSources(T entity) {
        addNewSourcesImpl(entity.getFilesList());
    }

    private void addNewSourcesImpl(List<String> newSources) {
        boolean add;
        if (newSources.size() == 1) {
            add = !sources.get(sources.size() - 1).equals(newSources.get(0));
        } else {
            int from = sources.size() - newSources.size();
            add = from < 0 || !sources.subList(from, sources.size()).equals(newSources);
        }
        if (add) {
            sources.addAll(newSources);
            mergedIn += newSources.size();
        }
    }

    private <T extends NamedEntity<T>> void overrideEntityCollection(Map<String, T> our, Map<String, T> their)
        throws SchemeException
    {
        for (T t : their.values()) {
            T o = our.get(t.getName());
            if (o == null) {
                our.put(t.getName(), t);
            } else {
                o.override(t);
            }
        }
    }

    private <T extends NamedEntity<T>> void validateCollectionState(Collection<T> collection,
        List<SchemeException> rv)
    {
        for (T e : collection) {
            try {
                e.validateState(this);
            } catch (SchemeException ex) {
                rv.add(ex);
            }
        }
    }

    private abstract static class BuilderBase<T extends BuilderBase<T>> {
        protected EmbeddedTypes embeddedTypes = SchemeModelLoader.DEFAULT_TYPES;
        protected SchemeLoadingOptions options = new SchemeLoadingOptions();
        protected String parent = null;

        protected BuilderBase() {
        }

        /**
         * Sets registry of embedded types to use instead of default one.
         *
         * @param embeddedTypes provider of embedded types to use.
         * @return {@code this} scheme model builder.
         */
        public T withTypes(EmbeddedTypes embeddedTypes) {
            this.embeddedTypes = Objects.requireNonNull(embeddedTypes, "embeddedTypesProvider");
            return (T) this;
        }

        /**
         * Sets registry of embedded types to default one.
         *
         * @return {@code this} scheme model builder.
         */
        public T withDefaultTypes() {
            this.embeddedTypes = SchemeModelLoader.DEFAULT_TYPES;
            return (T) this;
        }

        /**
         * Sets options to use for scheme model loading or creation.
         *
         * @param options options to use.
         * @return {@code this} scheme model builder.
         */
        public T withOptions(SchemeLoadingOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return (T) this;
        }

        /**
         * Sets name of parent model to use for scheme model loading or creation.
         *
         * @param parent name of parent model.
         * @return {@code this} scheme model builder.
         */
        public T withParent(String parent) {
            this.parent = parent;
            return (T) this;
        }
    }

    /**
     * Builder for empty scheme model.
     */
    public static class Builder extends BuilderBase<Builder> {
        private String name = null;

        private Builder() {
        }

        /**
         * Sets name for model to create.
         *
         * @param name model name.
         * @return {@code this} scheme model builder.
         */
        public Builder withName(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * Builds empty scheme model, with configured options, name and parent.
         *
         * @return new empty Scheme Model.
         */
        public SchemeModel build() {
            if (name == null) {
                throw new IllegalStateException("Need name to build empty scheme model");
            }
            return new SchemeModel(embeddedTypes, name, parent);
        }
    }

    /**
     * Loader of scheme model from external file.
     */
    public static class Loader extends BuilderBase<Loader> {
        private final List<URL> urls = new ArrayList<>();

        private Loader() {
        }

        /**
         * Adds URL to load and parse.
         * <p>
         * This method don't override list of URLs, it adds one more URL at the end of existing list.
         * URL list is empty in the new loader.
         *
         * @param url URL to add.
         * @return {@code this} scheme model loader.
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
         * @return {@code this} scheme model loader.
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
         * supported by JDK are supported, and additionally special {@code resource} scheme ius supported.
         * {@code resource} scheme is resolved with current class loader. If provided {@code resource}
         * URL is resolved into several resources, exception is thrown. {@code resource} URLs must be
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
         * @return {@code this} scheme model loader.
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
         * Loads, parses and prepares {@link SchemeModel scheme model} from configured URLs with using configured options.
         * <p>
         * At least one URL must be configured via {@link #fromURL(URL)}, {@link #fromURLs(Collection)} or
         * {@link #fromSpecification(String)}.
         *
         * @return Scheme model.
         * @throws IOException if one of URLs cannot be read or contains syntactic errors.
         * @throws SchemeException if one of specified scheme files contains structural errors or files in the resulting
         * set are incompatible.
         */
        public SchemeModel load() throws IOException, SchemeException {
            if (urls.isEmpty()) {
                throw new IllegalStateException("Cannot load scheme model without URLs");
            }
            return SchemeModelLoader.loadSchemeModel(urls, options, embeddedTypes);
        }
    }
}
