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

import com.dxfeed.scheme.EmbeddedTypes;
import com.dxfeed.scheme.SchemeException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Description of one record type for {@link SchemeModel scheme model}.
 * <p>
 * Record is ordered collection of {@link SchemeRecord.Field fields} and some additional properties.
 * <p>
 * Each field has a name, type, and optional aliases and tags. All fields' names and aliases must be unique
 * among fields of one record.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeRecord extends NamedEntity<SchemeRecord> {
    private final String base;
    private final String parentGenerator;
    private Boolean hasRegionals;
    private Boolean disabled;

    @Deprecated
    private String eventName = null;

    private String index1 = null;
    private String index2 = null;

    private final Map<String, Field> fields = new LinkedHashMap<>();

    /**
     * Creates new empty record.
     *
     * @param name name of record, cannot be {@code null}.
     * @param mode mode of record, cannot be {@code null}.
     * @param parentGenerator parent generator of this record, if it is generator template, or {@code} null otherwise.
     * @param hasRegionals flag marks this record as regionals one. Use {@code null} for undefined or default.
     * @param doc documentation string of record, can be {@code null}.
     * @param file source file of record, cannot be {@code null}.
     */
    public SchemeRecord(String name, Mode mode, String parentGenerator, Boolean hasRegionals, String doc, String file) {
        this(name, mode, parentGenerator, null, hasRegionals, doc, file);
    }

    /**
     * Creates new empty record.
     *
     * @param name name of record, cannot be {@code null}.
     * @param mode mode of record, cannot be {@code null}.
     * @param hasRegionals flag marks this record as regionals one. Use {@code null} for undefined or default.
     * @param doc documentation string of record, can be {@code null}.
     * @param file source file of record, cannot be {@code null}.
     */
    public SchemeRecord(String name, Mode mode, Boolean hasRegionals, String doc, String file) {
        this(name, mode, null, null, hasRegionals, doc, file);
    }

    private SchemeRecord(String name, Mode mode, String parentGenerator, String base, Boolean hasRegionals, String doc,
        String file)
    {
        super(name, mode, doc, file);
        this.base = base;
        this.parentGenerator = parentGenerator;
        this.hasRegionals = hasRegionals;
        this.disabled = null;
    }

    /**
     * Returns name of base record if this record has one, {@code null} otherwise.
     */
    public String getBase() {
        return base;
    }

    /**
     * Returns, does this record have base or not.
     */
    public boolean hasBase() {
        return base != null;
    }

    /**
     * Returns, is this record generator template or not.
     */
    public boolean isTemplate() {
        return parentGenerator != null;
    }

    /**
     * Returns name of generator owns this record if it is generator template, {@code null} otherwise.
     */
    public String getParentGenerator() {
        return parentGenerator;
    }

    /**
     * Returns, does this record have regional variants or not.
     */
    public boolean hasRegionals() {
        return hasRegionals != null && hasRegionals;
    }

    /**
     * Returns, does this record have event flags and index field or not.
     */
    public boolean hasEventFlags() {
        return index1 != null || index2 != null;
    }

    /**
     * Returns is this record disabled by default.
     * <p>
     * Record can be disabled or enabled later with visibility rules.
     */
    public boolean isDisabled() {
        return disabled != null && disabled;
    }

    /**
     * Sets is this record disabled by default.
     * <p>
     * Record can be disabled or enabled later with visibility rules.
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * Sets names of index fields for records with event flags.
     * <p>
     * Any of two index fields can be {@code null}. Set both of them to {@code null} to disable index field and event
     * flags.
     * <p>
     * Names of index fields can be set before adding respectable fields to record, but these names must be valid
     * and fields must be present in record when {@link SchemeModel model} to which this record has been added checks
     * its state.
     *
     * @param index1 first index field name.
     * @param index2 second index field name.
     */
    public void setIndex(String index1, String index2) {
        this.index1 = index1;
        this.index2 = index2;
    }

    /**
     * Returns first index field. Can return {@code null}.
     */
    public String getIndex1() {
        return index1;
    }

    /**
     * Returns second index field. Can return {@code null}.
     */
    public String getIndex2() {
        return index2;
    }

    /**
     * Sets event name of this record to support its enabling/disabling via legacy properties.
     */
    @Deprecated
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Returns event name of this record to support its enabling/disabling via legacy properties.
     * <p>
     * Returns record's {@link #getName() name} if event name is not set.
     */
    @Deprecated
    public String getEventName() {
        return eventName == null || eventName.isEmpty() ? getName() : eventName;
    }

    /**
     * Adds new field to the record.
     *
     * @param fieldName name fo field. Must be unique for this record, cannot be {@code null}.
     * @param mode mode of field, cannot be {@code null}.
     * @param disabled default state of field, is it enabled or disabled. Can be {@code null} for
     * unspecified.
     * @param type name of field's type, cannot be {@code null}. Existence of type is not checked here,
     * it will be checked by {@link SchemeModel model} which owns this record when it will check its state.
     * @param hasBitfields flag which enables bitfields for this field.
     * @param compositeOnly flag which marks this field as field of composite record only. Can be {@code null} for
     * unspecified.
     * @param doc documentation string for field, can be {@code null}.
     * @return Newly created field.
     * @throws SchemeException if field with such name already exists.
     */
    public Field addField(String fieldName, Mode mode, Boolean disabled, String type, boolean hasBitfields,
        Boolean compositeOnly, String doc) throws SchemeException
    {
        Objects.requireNonNull(fieldName, "fieldName");
        if (fields.containsKey(fieldName)) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                "Field \"" + fieldName + "\" already exists"), getFilesList());
        }
        Field f = new Field(this, fieldName, mode, disabled, type, hasBitfields, compositeOnly, doc,
            getLastFile());
        fields.put(fieldName, f);
        return f;
    }

    /**
     * Returns all added fields in order of adding.
     */
    public Collection<Field> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    /**
     * Returns field by its name or {@code null} if there is no field with given name.
     */
    public Field getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Constructs new record based on this one. New record is exact clone of this one, except it must have other name
     * and can have other mode, parent generator and source file. {@link #getBase() Base} of new record will be set
     * to name of this one.
     * <p>
     * This record must have mode {@link Mode#NEW NEW}, records with mode {@link Mode#UPDATE UPDATE} cannot
     * be used as base for new record.
     *
     * @param parentGenerator parent generator's name of new record if it is generator template.
     * @param to name of new record, cannot be {@code null}.
     * @param mode mode of new record, cannot be {@code null}.
     * @param file source file of new record, cannot be {@code null}.
     * @return newly created record.
     * @throws SchemeException if this record has wrong type.
     */
    public SchemeRecord copyFrom(String parentGenerator, String to, Mode mode, String file) throws SchemeException {
        if (getMode() != Mode.NEW) {
            throw new SchemeException(
                "Base record \"" + getName() + "\" for record \"" + to + "\" is update, not new record",
                file);
        }
        // Clone has this record as base!
        SchemeRecord clone = new SchemeRecord(to, mode, parentGenerator, getName(), hasRegionals, getDoc(), file);
        clone.setIndex(index1, index2);
        for (Field f : fields.values()) {
            Field nf = f.copyFrom(clone, file);
            clone.fields.put(nf.getName(), nf);
        }
        clone.eventName = eventName;
        return clone;
    }

    @Override
    void override(SchemeRecord newInstance) throws SchemeException {
        super.override(newInstance);
        // Bases must be equal or new instance (overlay) must have empty base
        if (newInstance.hasBase()) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Record with mode=\"update\" cannot have copyFrom=\"" + newInstance.getBase() + "\""),
                getFilesList());
        }

        if (newInstance.isTemplate() && !isTemplate()) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Cannot update record with record template"), getFilesList());
        }
        if (!newInstance.isTemplate() && isTemplate()) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Cannot update record template with record"), getFilesList());
        }
        if (!Objects.equals(newInstance.getParentGenerator(), getParentGenerator())) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                "Parent generators conflict"), getFilesList());
        }

        if (newInstance.hasRegionals != null) {
            hasRegionals = newInstance.hasRegionals;
        }
        if (newInstance.disabled != null) {
            disabled = newInstance.disabled;
        }
        if (newInstance.index1 != null || newInstance.index2 != null) {
            index1 = newInstance.index1;
            index2 = newInstance.index2;
        }
        if (newInstance.eventName != null) {
            eventName = newInstance.eventName;
        }

        // Merge fields
        for (Field newF : newInstance.fields.values()) {
            Field oldF = fields.get(newF.getName());
            if (oldF == null) {
                // Check, that this field is "new"
                if (newF.getMode() != Mode.NEW) {
                    throw new SchemeException(SchemeException.formatConflictMessage(this, newF.getLastFile(),
                        "Cannot add new field with mode=\"update\"."), getFilesList());
                }
                newF.setParent(this);
                fields.put(newF.getName(), newF);
            } else {
                oldF.override(newF);
            }
        }
    }

    @Override
    void validateState(SchemeModel parent) throws SchemeException {
        super.validateState(parent);
        // Reset "" to null
        index1 = index1 == null || !index1.isEmpty() ? index1 : null;
        index2 = index2 == null || !index2.isEmpty() ? index2 : null;

        // Check that index fields are known and are enabled
        if (index1 != null && !fields.containsKey(index1)) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Index1 field \"" + index1 + "\" is absent"), getFilesList());
        }
        if (index2 != null && !fields.containsKey(index2)) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "Index2 field \"" + index2 + "\" is absent"), getFilesList());
        }

        // Check that all aliases are unique
        Map<Field.Alias, Field> seen = new HashMap<>();
        for (Field f : fields.values()) {
            f.validateState(parent);
            for (Field.Alias a : f.getAliases()) {
                Field of = seen.get(a);
                if (of != null) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                        "Alias \"" + a + "\" exists for two fields \"" + f.getName() + "\" and " +
                            "\"" + of.getName() + "\""), getFilesList());
                }
                seen.put(a, f);
            }
        }
    }

    /**
     * Description of one field of {@link SchemeRecord record}.
     */
    public static final class Field extends ChildEntity<SchemeRecord, Field> {
        /**
         * Mode of field's {@link Tag tag} or {@link Alias alias}.
         */
        public enum AliasOrTagMode {
            /**
             * {@link Tag Tag} or {@link Alias alias} must be added to field.
             * <p>
             * This mode is valid for fields both with {@link Mode#NEW NEW} or {@link Mode#UPDATE UPDATE} modes.
             */
            ADD,
            /**
             * {@link Tag Tag} or {@link Alias alias} must be removed from field.
             * <p>
             * This mode is valid for fields only with {@link Mode#UPDATE UPDATE} mode.
             */
            REMOVE
        }

        private String type;
        private boolean hasBitfields;
        private boolean disabled;
        private boolean compositeOnly;
        private final Set<Alias> aliases = new HashSet<>();
        private final List<Alias> aliasesAsAdded = new ArrayList<>();
        private Alias mainAlias = null;
        private final Set<Tag> tags = new HashSet<>();
        private final Map<String, Bitfield> bitFields = new HashMap<>();
        @Deprecated
        private String eventName = null;

        private Field(SchemeRecord parent, String name, Mode mode, boolean disabled, String type, boolean hasBitfields,
            boolean compositeOnly, String doc, String file)
        {
            super(parent, name, mode, doc, file);
            this.disabled = disabled;
            this.type = type;
            this.hasBitfields = hasBitfields;
            this.compositeOnly = compositeOnly;
        }

        /**
         * Returns name of type of this field.
         */
        public String getType() {
            return type;
        }

        /**
         * Returns, does this field has {@link Bitfield bitfields} added to it.
         */
        public boolean hasBitfields() {
            return hasBitfields && !bitFields.isEmpty();
        }

        /**
         * Returns is this field disabled by default.
         * <p>
         * Field can be disabled or enabled later with visibility rules.
         */
        public boolean isDisabled() {
            return disabled;
        }

        /**
         * Returns is this field only for composite record and not for regional variants.
         */
        public boolean isCompositeOnly() {
            return compositeOnly;
        }

        /**
         * Returns list of all field's aliases. Can be empty.
         */
        public List<Alias> getAliases() {
            return Collections.unmodifiableList(aliasesAsAdded);
        }

        /**
         * Returns field's main alias. If field doesn't have aliases, return field's name.
         */
        public String getMainAlias() {
            if (mainAlias != null) {
                return mainAlias.getValue();
            }
            if (!aliasesAsAdded.isEmpty()) {
                return aliasesAsAdded.get(0).getValue();
            }
            return getName();
        }

        /**
         * Returns field's tags. Can be empty.
         */
        public Set<String> getTags() {
            return tags.stream().map(Tag::getValue).collect(Collectors.toSet());
        }

        /**
         * Returns field's {@link Bitfield bitfields}. This method is valid only if {@link #hasBitfields()} is true.
         */
        public Collection<Bitfield> getBitfields() {
            if (!hasBitfields) {
                throw new IllegalStateException("Cannot return bitfields for type without bitfields");
            }
            return Collections.unmodifiableCollection(bitFields.values());
        }

        /**
         * Adds new alias for the field.
         *
         * @param alias alias name, cannot be {@code null}.
         * @param main flag which marks this alias as amin one. Only one alias can be main.
         * @param mode mode of this alias.
         * @throws SchemeException if there is mode mismatch, alias is already present or alias is second main alias.
         */
        public void addAlias(String alias, boolean main, AliasOrTagMode mode) throws SchemeException {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(mode, "mode");
            Alias a = new Alias(alias.trim(), mode, main);

            if (mode == AliasOrTagMode.REMOVE && getMode() == Mode.NEW) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                    "Alias \"" + a.toString() + "\" has mode REMOVE but field has mode NEW"), getFilesList());
            }

            if (mainAlias != null && main) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                    "Alias \"" + a.toString() + "\" is second main alias, first one was " +
                        "\"" + mainAlias.getValue() + "\""), getFilesList());
            }
            if (!aliases.add(a)) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                    "Alias \"" + a.toString() + "\" is duplicate"), getFilesList());
            }
            aliasesAsAdded.add(a);
            if (main) {
                mainAlias = a;
            }
        }

        /**
         * Adds new tag for the field.
         *
         * @param tag tag name, cannot be {@code null}.
         * @param mode mode of this tag.
         * @throws SchemeException if there is mode mismatch or tag is already present.
         */
        public void addTag(String tag, AliasOrTagMode mode) throws SchemeException {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(mode, "mode");
            Tag t = new Tag(tag.trim(), mode);
            if (mode == AliasOrTagMode.REMOVE && getMode() == Mode.NEW) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                    "Tag \"" + t.toString() + "\" has mode REMOVE but field has mode NEW"), getFilesList());
            }
            if (!tags.add(t)) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, this.getLastFile(),
                    "Tag \"" + t.toString() + "\" is duplicate"), getFilesList());
            }
        }

        /**
         * Adds new bitfield to this field. This method can be called only for fields which has been created with
         * {@code hasBitfields} flag set to {@code true}.
         * <p>
         * All bitfields must be mon-overlapping, but this method doesn't check this condition. All bitfields will
         * be checked by {@link SchemeModel model} state check.
         *
         * @param fieldName name of bitfield, cannot be {@code null}.
         * @param mode mode of this bitfield.
         * @param offset offset of this bitfield in bits inside it's parent field. Must be between {@code 0} and
         * {@code 63} inclusive.
         * @param size size of this bitfield in bits. Must be between {@code 1} and {@code 64} inclusive.
         * @param doc documentation string of bitfield, can be {@code null}.
         * @param file source file of bitfield, cannot be {@code null}.
         * @throws SchemeException if there is
         */
        public void addBitfield(String fieldName, Mode mode, Integer offset, Integer size, String doc, String file)
            throws SchemeException
        {
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(mode, "mode");
            if (!hasBitfields) {
                throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                    "Cannot add bitfield for type without bitfields"), getFilesList());
            }
            bitFields.put(fieldName, new Bitfield(this, fieldName, mode, offset, size, doc, file));
        }

        /**
         * Sets event name of this field to support its enabling/disabling via legacy properties.
         * <p>
         * Please note that in the legacy scheme, not all fields have an event name even when they belong to
         * the same record, and sometimes field event name is not equal to the record event name.
         */
        @Deprecated
        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        /**
         * Returns event name of this field to support its enabling/disabling via legacy properties.
         * <p>
         * Returns parent record's {@link SchemeRecord#getEventName() event name} if field haven't event name set.
         */
        @Deprecated
        public String getEventName() {
            return eventName == null || eventName.isEmpty() ? getParent().getEventName() : eventName;
        }

        private Field copyFrom(SchemeRecord target, String file) throws SchemeException {
            if (getMode() != Mode.NEW) {
                throw new SchemeException(
                    "Base record \"" + getName() + "\" for record \"" + target.getName() + "\" has update field \"" +
                    getName(), file);
            }
            Field clone = new Field(target, getName(), getMode(), disabled, type, hasBitfields, compositeOnly,
                getDoc(), file);
            clone.aliases.addAll(aliases);
            clone.aliasesAsAdded.addAll(aliasesAsAdded);
            clone.tags.addAll(tags);
            for (Bitfield f : bitFields.values()) {
                clone.bitFields.put(f.getName(), f.copyFrom(clone, file));
            }
            clone.eventName = eventName;
            return clone;
        }

        @Override
        void override(Field newInstance) throws SchemeException {
            super.override(newInstance);
            if (newInstance.type != null) {
                type = newInstance.type;
                hasBitfields = newInstance.hasBitfields;
            }
            compositeOnly = newInstance.compositeOnly;
            disabled = newInstance.disabled;

            if (!hasBitfields) {
                bitFields.clear();
            } else {
                for (Bitfield newF : newInstance.bitFields.values()) {
                    Bitfield oldF = bitFields.get(newF.getName());
                    if (oldF == null) {
                        // Check, that this field is "new"
                        if (newF.getMode() != Mode.NEW) {
                            throw new SchemeException(SchemeException.formatConflictMessage(this,
                                newF.getLastFile(), "Cannot add new bitfield with mode=\"update\"."), getFilesList());
                        }
                        newF.setParent(this);
                        bitFields.put(newF.getName(), newF);
                    } else {
                        oldF.override(newF);
                    }
                }
            }

            for (Alias a : newInstance.aliases) {
                switch (a.getMode()) {
                    case ADD:
                        if (!aliases.add(a)) {
                            throw new SchemeException(
                                SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                                    "Cannot add new Alias \"" + a.getValue() + "\", already exist"),
                                getFilesList());
                        }
                        aliasesAsAdded.add(a);
                        if (a.isMain()) {
                            mainAlias = a;
                        }
                        break;
                    case REMOVE:
                        if (!aliases.remove(a)) {
                            throw new SchemeException(
                                SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                                    "Cannot remove Alias \"" + a.getValue() + "\", doesn't exist"),
                                getFilesList());
                        }
                        // Linear search, but it is not relevant here
                        aliasesAsAdded.remove(a);
                        if (a.isMain() && a.equals(mainAlias)) {
                            mainAlias = null;
                        }
                        break;
                }
            }

            for (Tag t : newInstance.tags) {
                switch (t.getMode()) {
                    case ADD:
                        if (!tags.add(t)) {
                            throw new SchemeException(
                                SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                                    "Cannot add new Tag \"" + t.getValue() + "\", already exist"),
                                getFilesList());
                        }
                        break;
                    case REMOVE:
                        if (!tags.remove(t)) {
                            throw new SchemeException(
                                SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                                    "Cannot remove Tag \"" + t.getValue() + "\", doesn't exist"),
                                getFilesList());
                        }
                        // Linear search, but it is not relevant here
                        break;
                }
            }

            if (newInstance.eventName != null) {
                eventName = newInstance.eventName;
            }
        }

        @Override
        void validateState(SchemeModel parent) throws SchemeException {
            super.validateState(parent);
            if (type == null) {
                throw new SchemeException(
                    SchemeException.formatInconsistencyMessage(this, "Field without type"),
                    getFilesList());
            }
            if (!parent.hasType(type)) {
                throw new SchemeException(
                    SchemeException.formatInconsistencyMessage(this, "Unknown type \"" + type + "\""),
                    getFilesList());
            }
            for (Alias a : aliases) {
                if (a.getMode() != AliasOrTagMode.ADD) {
                    throw new SchemeException(
                        SchemeException.formatInconsistencyMessage(this, "Alias \"" + a.getValue() + "\" with mode=\"" +
                            a.getMode().name().toLowerCase() + "\"" + " must be used in update"),
                        getFilesList());
                }
            }
            for (Tag t : tags) {
                if (t.getMode() != AliasOrTagMode.ADD) {
                    throw new SchemeException(
                        SchemeException.formatInconsistencyMessage(this, "Tag \"" + t.getValue() + "\" with mode=\"" +
                            t.getMode().name().toLowerCase() + "\"" + " must be used in update"),
                        getFilesList());
                }
            }
            if (hasBitfields && !bitFields.isEmpty()) {
                Bitfield[] bits = new Bitfield[64];

                for (Bitfield f : bitFields.values()) {
                    f.validateState(parent);
                    for (int i = f.getOffset(); i < f.getOffset() + f.getSize(); i++) {
                        if (bits[i] != null) {
                            throw new SchemeException(
                                SchemeException.formatInconsistencyMessage(this, "Bitfields \"" + f.getName() +
                                    "\" and \"" + bits[i].getName() + "\" overlap."),
                                getFilesList());
                        }
                        bits[i] = f;
                    }
                }
            }
        }

        /**
         * Description of one bitfield.
         * <p>
         * Bitfield is part of {@link Field field} with type for which {@link EmbeddedTypes
         * types registry} says, that it is {@link EmbeddedTypes#canHaveBitfields(String) flags}
         * type.
         * <p>
         * This type must have 64 bit integer representation and could be divided into disjoint bitfields. Bitfields
         * don't need to cover whole flags field.
         */
        public static final class Bitfield extends ChildEntity<Field, Bitfield> {
            private Integer offset;
            private Integer size;

            private Bitfield(Field parent, String name, Mode mode, Integer offset, Integer size, String doc,
                String file) throws SchemeException
            {
                super(parent, name, mode, doc, file);
                if (mode == Mode.NEW) {
                    if (offset == null || size == null) {
                        throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                            "New bitfield must have size and offset"), getFilesList());
                    }
                }
                this.offset = offset;
                this.size = size;
            }

            /**
             * Returns offset of bitfield in bits. Could return -1 if scheme model was not checked for consistency yet.
             */
            public int getOffset() {
                return offset == null ? -1 : offset;
            }

            /**
             * Returns size of bitfield in bits. Could return -1 if scheme model was not checked for consistency yet.
             */
            public int getSize() {
                return size == null ? -1 : size;
            }

            /**
             * Returns {@code true} if bitfield has its {@link @getOffset() offset} and {@link #getSize() size} defined.
             */
            public boolean defined() {
                return offset != null && size != null;
            }

            Bitfield copyFrom(Field target, String file) {
                try {
                    return new Bitfield(target, getName(), getMode(), offset, size, getDoc(), file);
                } catch (SchemeException e) {
                    // Cannot be here
                    return null;
                }
            }

            @Override
            void override(Bitfield newInstance) throws SchemeException {
                super.override(newInstance);
                if (newInstance.offset != null) {
                    offset = newInstance.offset;
                }
                if (newInstance.size != null) {
                    size = newInstance.size;
                }
            }

            @Override
            void validateState(SchemeModel parent) throws SchemeException {
                super.validateState(parent);
                if (offset == null) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                        "Bitfield must have non-zero offset"), getFilesList());
                }
                if (offset < 0 || offset > 63) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                        "Bitfield must have offset between 0 and 63"), getFilesList());
                }
                if (size == null) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                        "Bitfield must have non-zero size"), getFilesList());
                }
                if (size < 1 || size > 64) {
                    throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                        "Bitfield must have size between 1 and 64"), getFilesList());
                }
            }
        }

        /**
         * Description of {@link Field field's} alias.
         * <p>
         * Alias contains of name and flag which defines is this alias main one or not.
         */
        public static final class Alias {
            private final String alias;
            private final AliasOrTagMode mode;
            private final boolean main;

            private Alias(String alias, AliasOrTagMode mode, boolean main) {
                Objects.requireNonNull(alias, "alias");
                Objects.requireNonNull(mode, "mode");
                this.alias = alias;
                this.mode = mode;
                this.main = main;
            }

            /**
             * Returns name of alias.
             */
            public String getValue() {
                return alias;
            }

            /**
             * Returns mode of alias.
             */
            public AliasOrTagMode getMode() {
                return mode;
            }

            /**
             * Returns is alias main or not.
             */
            public boolean isMain() {
                return main;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Alias other = (Alias) o;
                return alias.equals(other.alias);
            }

            @Override
            public int hashCode() {
                return Objects.hash(alias);
            }

            @Override
            public String toString() {
                return alias;
            }
        }

        private static final class Tag {
            private final String tag;
            private final AliasOrTagMode mode;

            public Tag(String tag, AliasOrTagMode mode) {
                Objects.requireNonNull(tag, "tag");
                Objects.requireNonNull(mode, "mode");
                this.tag = tag;
                this.mode = mode;
            }

            public String getValue() {
                return tag;
            }

            public AliasOrTagMode getMode() {
                return mode;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Tag other = (Tag) o;
                return tag.equals(other.tag);
            }

            @Override
            public int hashCode() {
                return Objects.hash(tag);
            }

            @Override
            public String toString() {
                return tag;
            }
        }
    }
}
