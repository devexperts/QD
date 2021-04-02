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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Description of one custom enum type for {@link SchemeModel scheme model}.
 * <p>
 * Enum is collection of {@link Value values}. Each value has name and ordinal number. All values' names and ordinal
 * numbers must be unique among values of one enum.
 * <p>
 * Term "ordinal number" is abbreviated as {@code ord} in API.
 *
 * @deprecated Will be improved in near future.
 */
@Deprecated
public final class SchemeEnum extends NamedEntity<SchemeEnum> {
    private final Map<String, Value> valuesByName = new LinkedHashMap<>();
    private final SortedMap<Integer, Value> valuesByOrd = new TreeMap<>();

    /**
     * Creates new empty enum.
     *
     * @param name name of enum, cannot be {@code null}.
     * @param mode mode of enum, cannot be {@code null}.
     * @param doc documentation string of enum, can be {@code null}.
     * @param file source file of enum, cannot be {@code null}.
     */
    public SchemeEnum(String name, Mode mode, String doc, String file) {
        super(name, mode, doc, file);
    }

    /**
     * Returns map of all values, with values' names as a key.
     */
    public Map<String, Value> getValuesByName() {
        return Collections.unmodifiableMap(valuesByName);
    }

    /**
     * Returns collection of all values, sorted by values' ordinal numbers.
     */
    public Collection<Value> getValuesByOrd() {
        return Collections.unmodifiableCollection(valuesByOrd.values());
    }

    /**
     * Adds new value to enum.
     * <p>
     * New value will have provided ordinal number.
     *
     * @param valueName name of value, cannot be {@code null}.
     * @param mode mode of value, cannot be {@code null}.
     * @param ord ordinal number for value.
     * @param valueDoc documentation string of value, can be {@code null}.
     * @throws SchemeException if value with such name already exists in this enum or provided ordinal number has been
     * used already.
     */
    public void addValue(String valueName, Mode mode, int ord, String valueDoc) throws SchemeException {
        Objects.requireNonNull(valueName, "valueName");
        if (valuesByName.containsKey(valueName)) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, getLastFile(),
                "Value \"" + valueName + "\" already exist"), getFilesList());
        }
        if (ord < 0 && mode ==Mode.NEW) {
            throw new SchemeException(SchemeException.formatInconsistencyMessage(this,
                "New value \"" + valueName + "\" must have ordinal number"), getFilesList());
        }
        if (ord >= 0 && valuesByOrd.containsKey(ord)) {
            throw new SchemeException(SchemeException.formatConflictMessage(this, getLastFile(),
                "Value \"" + valueName + "\" has duplicate ordinal number " + ord), getFilesList());
        }
        Value v = new Value(this, valueName, mode, ord, valueDoc, getLastFile());
        valuesByName.put(valueName, v);
        if (v.hasValidOrd()) {
            valuesByOrd.put(ord, v);
        }
    }

    @Override
    void override(SchemeEnum newInstance) throws SchemeException {
        super.override(newInstance);
        for (Value newVal : newInstance.valuesByName.values()) {
            Value oldByName = valuesByName.get(newVal.getName());
            if (oldByName == null) {
                // Could we add this?
                Value oldByOrd = valuesByOrd.get(newVal.getOrd());
                if (oldByOrd != null) {
                    throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                        "Name could not be changed for value with ordinal number " + newVal.getOrd() + " from " +
                            "\"" + oldByOrd.getName() + "\" to \"" + newVal.getName() + "\""
                    ), getFilesList());
                }
                valuesByName.put(newVal.getName(), newVal);
                if (newVal.hasValidOrd()) {
                    valuesByOrd.put(newVal.getOrd(), newVal);
                }
            } else {
                oldByName.override(newVal);
            }
        }
    }

    @Override
    void validateState(SchemeModel parent) throws SchemeException {
        super.validateState(parent);
        int ord = 0;
        for (Value v : valuesByName.values()) {
            v.validateState(parent);
            if (v.getOrd() != ord) {
                throw new SchemeException(
                    SchemeException.formatInconsistencyMessage(v, "Value #" + ord + " has ordinal number " + v.getOrd()),
                    v.getFilesList()
                );
            }
            ord++;
        }
    }

    @Override
    public String toString() {
        return "SchemeEnum{" +
            "from=" + getFrom() +
            ", name='" + getFullName() + '\'' +
            ", values=" +
            valuesByName.values().stream().map(v -> v.getName() + '(' + v.getOrd() + ')')
                .collect(Collectors.joining(", ", "{", "}")) +
            '}';
    }

    /**
     * Description of one value in {@link SchemeEnum enum}.
     * <p>
     * Each value has name and ordinal number.
     */
    public static final class Value extends ChildEntity<SchemeEnum, Value> {
        private final int ord;

        private Value(SchemeEnum parent, String name, Mode mode, int ord, String doc, String file) {
            super(parent, name, mode, doc, file);
            this.ord = ord;
        }

        /**
         * Returns value's ordinal number.
         */
        public int getOrd() {
            return ord;
        }

        boolean hasValidOrd() {
            return ord >= 0;
        }

        @Override
        void override(Value newInstance) throws SchemeException {
            super.override(newInstance);
            if (newInstance.hasValidOrd() && ord != newInstance.ord) {
                throw new SchemeException(SchemeException.formatConflictMessage(this, newInstance.getLastFile(),
                    "Ordinal number cannot be changed from " + ord + " to " + newInstance.ord), getFilesList());
            }
        }

        @Override
        public String toString() {
            return "Value{" +
                "from=" + getFrom() +
                ", name='" + getFullName() + '\'' +
                ", ord=" + ord +
                '}';
        }
    }
}
