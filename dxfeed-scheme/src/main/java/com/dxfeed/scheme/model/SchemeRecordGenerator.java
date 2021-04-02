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
package com.dxfeed.scheme.model;

import com.dxfeed.scheme.SchemeException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Description of one record generator.
 * <p>
 * Record generator describes family of records which have same fields and flags
 * and differs only by name.
 * <p>
 * Record generator consists of one or more record templates, which are same as
 * {@link SchemeRecord simple records} and iterator which is ordered set of strings
 * which are used to modify record names. This set can contain empty string among
 * others.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeRecordGenerator extends NamedEntity<SchemeRecordGenerator> {
    /**
     * Type of record name modification.
     */
    public enum Type {
        /**
         * Prepend strings from iterator to record template name.
         */
        PREFIX,
        /**
         * Append strings from iterator to record template name.
         */
        SUFFIX
    }

    /**
     * Mode of iterator modification when generator is merged with previously defined instance.
     */
    public enum IteratorMode {
        /**
         * It is new iterator. This mode is compatible only with generator with mode {@link Mode#NEW NEW}.
         */
        NEW,
        /**
         * Append content of this iterator to existing one.
         * This mode is compatible only with generator with mode {@link Mode#UPDATE UPDATE}.
         */
        APPEND,
        /**
         * Replace content of existing iterator with content of this iterator.
         * This mode is compatible only with generator with mode {@link Mode#UPDATE UPDATE}.
         */
        REPLACE
    }

    private Type type;
    private String delimiter;
    private IteratorMode iteratorMode;
    private final Set<String> iterator = new LinkedHashSet<>();
    private final Map<String, SchemeRecord> templates = new LinkedHashMap<>();

    /**
     * Creates new record generator.
     *
     * @param name name of generator, cannot be {@code null}.
     * @param mode mode of generator, cannot be {@code null}.
     * @param doc documentation string of generator, can be {@code null}.
     * @param file source file of generator, cannot be {@code null}.
     */
    public SchemeRecordGenerator(String name, Mode mode, String doc, String file) {
        super(name, mode, doc, file);
        type = null;
        delimiter = null;
        iteratorMode = mode == Mode.NEW ? IteratorMode.NEW : IteratorMode.APPEND;
    }

    /**
     * Returns type of name modification used by this generator.
     * <p>
     * Default value is {@link Type#SUFFIX SUFFIX}.
     */
    public Type getType() {
        return type == null ? Type.SUFFIX : type;
    }

    /**
     * Sets type of name modification used by this generator.
     */
    public void setType(Type type) {
        Objects.requireNonNull(type, "type");
        this.type = type;
    }

    /**
     * Returns delimiter which will be inserted between base record name and non-empty string from iterator.
     */
    public String getDelimiter() {
        return delimiter == null ? "" : delimiter;
    }

    /**
     * Sets delimiter which will be inserted between base record name and non-empty string from iterator.
     */
    public void setDelimiter(String delimiter) {
        Objects.requireNonNull(delimiter, "delimiter");
        this.delimiter = delimiter;
    }

    /**
     * Sets iterator update mode.
     */
    public void setIteratorMode(IteratorMode iteratorMode) {
        Objects.requireNonNull(iteratorMode, "mode");
        this.iteratorMode = iteratorMode;
    }

    /**
     * Adds string to iterator. Duplicates will be discarded silently.
     */
    public void addIteratorValue(String val) {
        iterator.add(Objects.requireNonNull(val, "val"));
    }

    /**
     * Returns all iterator's strings.
     */
    public Collection<String> getIterator() {
        return Collections.unmodifiableCollection(iterator);
    }

    /**
     * Adds template for records.
     *
     * @param t template.
     * @throws SchemeException if record is not a template or template with such name already exists in this generator.
     */
    public void addTemplate(SchemeRecord t) throws SchemeException {
        Objects.requireNonNull(t, "t");
        if (!t.isTemplate()) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Record cannot be used as template in generator"), getFilesList());
        }
        if (!t.getParentGenerator().equals(getName())) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Record template from wrong generator"), getFilesList());
        }

        if (templates.put(t.getName(), t) != null) {
            throw new SchemeException(SchemeException.formatConflictMessage(t, getLastFile(),
                "Record template cannot be defined twice in one file"), t.getFilesList());
        }
    }

    /**
     * Returns all templates in same order as they were added.
     */
    public Collection<SchemeRecord> getTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * Generates all names for given record. Record must be template and be owned by this generator.
     */
    public List<String> getRecordsNames(SchemeRecord rec) {
        Objects.requireNonNull(rec, "rec");
        if (!rec.isTemplate()) {
            throw new IllegalArgumentException("Record cannot be used as template in generator");
        }
        if (!rec.getParentGenerator().equals(getName())) {
            throw new IllegalArgumentException("Record template from wrong generator");
        }

        String baseName = rec.getName();
        List<String> names = new ArrayList<>();
        for (String s : iterator) {
            if (s.isEmpty()) {
                names.add(baseName);
            } else {
                switch (type) {
                    case PREFIX:
                        names.add(s + (delimiter == null ? "" : delimiter) + baseName);
                        break;
                    case SUFFIX:
                        names.add(baseName + (delimiter == null ? "" : delimiter) + s);
                        break;
                }
            }
        }
        return names;
    }

    @Override
    void override(SchemeRecordGenerator newInstance) throws SchemeException {
        super.override(newInstance);
        if (newInstance.type != null) {
            this.type = newInstance.type;
        }
        if (newInstance.delimiter != null) {
            this.delimiter = newInstance.delimiter;
        }
        if (newInstance.iteratorMode == IteratorMode.NEW) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Iterator with mode=\"new\" could not be used to update existing iterator."), getFilesList());
        }
        if (newInstance.iteratorMode == IteratorMode.REPLACE) {
            this.iterator.clear();
        }
        this.iterator.addAll(newInstance.iterator);

        // Merge records
        for (SchemeRecord t : newInstance.templates.values()) {
            SchemeRecord oldT = templates.get(t.getName());
            if (oldT == null) {
                templates.put(t.getName(), t);
            } else {
                oldT.override(t);
            }
        }
    }

    @Override
    void validateState(SchemeModel parent) throws SchemeException {
        super.validateState(parent);
        if (iteratorMode != IteratorMode.NEW) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Iterator with mode=\"" + iteratorMode.name().toLowerCase() + "\" must be update, not new object"),
                getFilesList());
        }
        for (SchemeRecord t : templates.values()) {
            t.validateState(parent);
        }
    }

    @Override
    public String toString() {
        return "SchemeRecordGenerator{" +
            "name='" + getName() + '\'' +
            '}';
    }
}
